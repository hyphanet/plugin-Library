/*
 */

package freenet.library.uploader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import net.pterodactylus.fcp.AllData;
import net.pterodactylus.fcp.ClientGet;
import net.pterodactylus.fcp.ClientPut;
import net.pterodactylus.fcp.FcpAdapter;
import net.pterodactylus.fcp.FcpConnection;
import net.pterodactylus.fcp.GetFailed;
import net.pterodactylus.fcp.Priority;
import net.pterodactylus.fcp.PutSuccessful;
import net.pterodactylus.fcp.SubscribeUSK;
import net.pterodactylus.fcp.SubscribedUSKUpdate;
import net.pterodactylus.fcp.URIGenerated;
import net.pterodactylus.fcp.Verbosity;
import freenet.library.io.YamlReaderWriter;
import freenet.library.io.serial.Packer;
import freenet.library.io.serial.Packer.BinInfo;

/**
 * Class to download the entire index.
 */
public class DownloadAll {
    private static final int PARALLEL_JOBS = 10;
    public final Map<FetchedPage, GetAdapter> stillRunning = new HashMap<FetchedPage, GetAdapter>();
    private String uri;
    private String newUri;
    private int edition;
    private FcpConnection connection;
    private static int getterCounter = 0;
    private static int uploadCounter = 0;
    private LinkedBlockingQueue<FetchedPage> objectQueue =
            new LinkedBlockingQueue<FetchedPage>();
    private Thread cleanupThread;
    private List<FetchedPage> roots = new ArrayList<FetchedPage>();
    
    private ExecutorService uploadStarter = null;
    private Map<String, Map.Entry<String, Runnable>> ongoingUploads = null;
            
    private int successful = 0;
    private int successfulBlocks = 0;
    private long successfulBytes = 0;
    private int failed = 0;
    private int recreated = 0;
    private int avoidFetching = 0;
    
    private Random rand = new Random();

    public DownloadAll(String u) {
        uri = u;
    }
    
    public static class WeakHashSet<T>
         implements Set<T> {
        /**
         * We just use the keys and let all values be TOKEN.
         */
        private Map<T, Object> map = new WeakHashMap<T, Object>();
        private static Object TOKEN = new Object();
        
        @Override
        public boolean add(T arg0) {
            if (map.containsKey(arg0)) {
                return false;
            } else {
                map.put(arg0, TOKEN);
                return true;
            }
        }

        @Override
        public boolean addAll(Collection<? extends T> arg0) {
            boolean retval = false;
            for (T ele : arg0) {
                if (add(ele)) {
                    retval = true;
                }
            }
            return retval;
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public boolean contains(Object arg0) {
            return map.containsKey(arg0);
        }

        @Override
        public boolean containsAll(Collection<?> arg0) {
            for (Object ele : arg0) {
                if (!contains(ele)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public Iterator<T> iterator() {
            return map.keySet().iterator();
        }

        @Override
        public boolean remove(Object arg0) {
            return map.remove(arg0) != null;
        }

        @Override
        public boolean removeAll(Collection<?> arg0) {
            boolean retval = true;
            for (Object ele : arg0) {
                if (!remove(ele)) {
                    retval = false;
                }
            }
            return retval;
        }

        @Override
        public boolean retainAll(Collection<?> arg0) {
            boolean retval = false;
            for (T ele : map.keySet()) {
                if (!arg0.contains(ele)) {
                    if (map.remove(ele) != null) {
                        retval = true;
                    }
                }
            }
            return retval;
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public Object[] toArray() {
            return map.keySet().toArray();
        }

        @Override
        public <T> T[] toArray(T[] arg0) {
            return map.keySet().toArray(arg0);
        }
        
    }

    /**
     * A class to keep track of what pages are fetched and how they are related
     * to other fetched pages. The purpose of this is to avoid fetching stuff
     * related only to "old" editions.
     */
    private static class FetchedPage {
        /**
         * This is really a Set but there is no WeakSet so we use the keys
         * and let all values be TOKEN.
         */
        private Set<FetchedPage> parents = new WeakHashSet<FetchedPage>();
        private Set<FetchedPage> children = new HashSet<FetchedPage>();
        
        private String uri;
        int level;
        private boolean succeeded;
        private boolean failed;
        
        FetchedPage(String u) {
            this(u, 0);
        }
        
        FetchedPage(String u, int l) {
            uri = u;
            level = l;
        }
        
        void addParent(FetchedPage fp) {
            parents.add(fp);
        }
        
        void addChild(FetchedPage fp) {
            children.add(fp);
        }
        
        FetchedPage newChild(String u) {
            FetchedPage child = new FetchedPage(u, level + 1);
            child.addParent(this);
            addChild(child);
            return child;
        }
        
        String getURI() {
            return uri;
        }
        
        boolean hasParent() {
            return !parents.isEmpty();
        }
    
        int getTreeSize() {
            int size = 1;
            for (FetchedPage child : children) {
                size += child.getTreeSize();
            }
            return size;
        }
        
        void addPerLevel(Map<Integer, Integer> result) {
        	if (!result.containsKey(level)) {
        		result.put(level, 0);
        	}
        	if (!succeeded && !failed) {
        		result.put(level, result.get(level) + 1);
        	}
        	for (FetchedPage child : children) {
        		child.addPerLevel(result);
        	}
        }

        int getTreeSizeSucceeded() {
            int size = succeeded ? 1 : 0;
            for (FetchedPage child : children) {
                size += child.getTreeSizeSucceeded();
            }
            return size;
        }

        int getTreeSizeFailed() {
            int size = failed ? 1 : 0;
            for (FetchedPage child : children) {
                size += child.getTreeSizeFailed();
            }
            return size;
        }
        
        void didFail() {
            failed = true;
        }

        void didSucceed() {
            failed = false;
            succeeded = true;
        }

        public FetchedPage findUri(String u) {
            if (u.equals(uri)) {
                return this;
            }
            for (FetchedPage child : children) {
                FetchedPage found = child.findUri(u);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
    }
    
    private class USKUpdateAdapter extends FcpAdapter {
        
        private boolean updated = false;
        private Object subscriber;

        public USKUpdateAdapter(Object s) {
            subscriber = s;
        }

        @Override
        public void receivedSubscribedUSKUpdate(FcpConnection fcpConnection, SubscribedUSKUpdate subscribedUSKUpdate) {
            assert fcpConnection == connection;
            if (subscribedUSKUpdate.getNewKnownGood()) {
                updated = true;
                newUri = subscribedUSKUpdate.getURI();
                edition = subscribedUSKUpdate.getEdition();
                synchronized (subscriber) {
                    subscriber.notify();
                }
            }
        }
        
        public void restart() {
            if (updated) {
                updated = false;
                System.out.println("Found: " + newUri + " Edition: " + edition);
                FetchedPage rootPage = new FetchedPage(newUri);
                synchronized (roots) {
                    roots.add(rootPage);
                    while (roots.size() > 2) {
                        roots.remove(0);
                    }
                }
                new GetAdapter(rootPage.newChild(newUri));
            }
        }
    }
    
    
    class StatisticsAccumulator {
    	private int count = 0;
    	private int sum = 0;

    	void addSample(int found) {
    		count++;
    		sum += found;
    	}

    	double getMean() {
    		return 1.0 * sum / count;
    	}
    }
    
    private Map<Integer, StatisticsAccumulator> statistics = new HashMap<Integer, StatisticsAccumulator>();
    private void addFoundChildren(int level, int foundChildren) {
    	if (!statistics.containsKey(level)) {
    		statistics.put(level, new StatisticsAccumulator());
    	}
    	statistics.get(level).addSample(foundChildren);
	}
    
    private double getEstimatedPagesLeft(FetchedPage page) {
    	double estimate = 0.0;
    	double extra = 0.0;
    	Map<Integer, Integer> pagesPerLevel = new HashMap<Integer, Integer>();
    	page.addPerLevel(pagesPerLevel);
    	for (int level = 1; pagesPerLevel.containsKey(level); level++) {
    		if (!statistics.containsKey(level)) {
    			return Double.POSITIVE_INFINITY;
    		}
    		extra += pagesPerLevel.get(level);
    		estimate += extra;
    		extra = extra * statistics.get(level).getMean();
    	}
    	return estimate;
    }
    


    private class GetAdapter extends FcpAdapter {
        private ClientGet getter;
        private String token;
        private FetchedPage page;
        private int progressTotal;
        private int progressRequired;
        private int progressCompleted;
        private boolean done;

        public GetAdapter(FetchedPage u) {
            page = u;
            getterCounter ++;
            token = "Getter" + getterCounter;
            getter = new ClientGet(page.getURI(), token);
            getter.setPriority(Priority.prefetch);
            getter.setVerbosity(Verbosity.ALL);
 
            waitForSlot();
            connection.addFcpListener(this);
            try {
                connection.sendMessage(getter);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
            synchronized (stillRunning) {
                stillRunning.put(page, this);
                stillRunning.notifyAll();
            }
        }

        /**
         * Show the amount of outstanding work.
         */
        void printLeft() {
            int total = 0;
            int required = 0;
            int completed = 0;
            synchronized (stillRunning) {
                for (GetAdapter value : stillRunning.values()) {
                    total += value.progressTotal;
                    required += value.progressRequired;
                    completed += value.progressCompleted;
                }
                System.out.println("Outstanding " + stillRunning.size() + " ClientGet jobs " +
                        "(" + completed + "/" + required + "/" + total + ") ");
            }
            showProgress();
        }
        
        private boolean processUri(String uri) {
        	synchronized (roots) {
        		for (FetchedPage root : roots) {
        			FetchedPage foundChild = root.findUri(uri);
        			if (foundChild != null) {
        				page.addChild(foundChild);
        				foundChild.addParent(page);
        				return false;
                    }
                }
            }
            objectQueue.offer(page.newChild(uri));
            return true;
        }
        
        @Override
        public void receivedAllData(FcpConnection c, AllData ad) {
            assert c == connection;
            assert ad != null;
            if (!token.equals(ad.getIdentifier())) {
                return;
            }
            System.out.println("receivedAllData for " + token +
                    " adding to the " + objectQueue.size() + " elements in the queue.");
            page.didSucceed();
            int foundChildren = 0;
            try {
                try {
                    Map<String, Object> map =
                            (LinkedHashMap<String, Object>) 
                            new YamlReaderWriter().readObject(ad.getPayloadInputStream());
                    if (map.containsKey("ttab")) {
                        Map<String, Object> map2 = (Map<String, Object>) map.get("ttab");
                        if (map2.containsKey("entries")) {
                            System.out.println("Contains ttab.entries");
                            Map<String, BinInfo> entries =
                                    (Map<String, Packer.BinInfo>) map2.get("entries");
                            for (BinInfo value : entries.values()) {
                            	try {
                            		String u = (String) value.getID();
                            		if (processUri(u)) {
                            			foundChildren ++;
                            		}
                            		
                            	} catch (ClassCastException e) {
                            		System.out.println("Cannot process " + value.getID());
                            	}
                            }
                            Map<String, Object> subnodes =
                                    (Map<String, Object>) map2.get("subnodes");
                            for (String key : subnodes.keySet()) {
                                if (processUri(key)) {
                                	foundChildren ++;
                                }
                            }
                            return;
                        }
                    }
                    if (map.containsKey("lkey") &&
                            map.containsKey("rkey") &&
                            map.containsKey("entries")) {
                        System.out.println("Contains entries");
                        Map<String, BinInfo> entries =
                                (Map<String, Packer.BinInfo>) map.get("entries");
                        for (BinInfo value : entries.values()) {
                        	try {
                        		String u = (String) value.getID();
                        		if (processUri(u)) {
                        			foundChildren ++;
                        		}
                        	} catch (ClassCastException e) {
                        		System.out.println("Cannot process " + value.getID());
                        	}
                        }
                        return;
                    }
                    Entry<String, Object> entry = map.entrySet().iterator().next();
                    if (entry.getValue() instanceof Map) {
                        Map<String, Object> map2 = (Map<String, Object>) entry.getValue();
                        if (map2.containsKey("node_min")
                                && map2.containsKey("size")
                                && map2.containsKey("entries")) {
                            return;
                        }
                    }
                    System.out.println("Cannot understand contents: " + map);
                    System.exit(1);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            } finally {
            	addFoundChildren(page.level, foundChildren);
                markDone();
                System.out.println("receivedAllData for " + token + " done.");
                successful ++;
                successfulBlocks += progressCompleted;
                successfulBytes += ad.getDataLength();
                showProgress();
            }
        }

		@Override
        public void receivedGetFailed(FcpConnection c, GetFailed gf) {
            assert c == connection;
            assert gf != null;
            if (!token.equals(gf.getIdentifier())) {
                return;
            }
            synchronized (getter) {
                getter.notify();
            }
            System.out.println("receivedGetFailed for " + token + " (" + page + ").");
            // System.exit(1);
            page.didFail();
            markDone();
            failed ++;
            showProgress();
            upload(page.getURI(), new Runnable() {
            	public void run() {
            		objectQueue.offer(page);
            		recreated ++;
            	}
            });
        }
        
        /**
         * We have detected that we cannot download a certain CHK.
         * 
         * If we are running on the host where this CHK is actually cached,
         * lets upload it from the cache in an attempt to repair.
         * 
         * @param filename of the file to upload.
         */
        public boolean upload(final String filename, final Runnable callback) {
        	final File dir = new File(".", UploaderPaths.LIBRARY_CACHE);
            if (!dir.canRead()) {
                return false;
            }
            final File file = new File(dir, filename);
            if (!file.canRead()) {
                System.err.println("Cannot find " + file + " in the cache.");
                return false;
            }
            if (uploadStarter == null) {
            	uploadStarter = Executors.newSingleThreadExecutor();
            	uploadStarter.execute(new Runnable() {
            		public void run() {
		            	connection.addFcpListener(new FcpAdapter() {
		            		@Override
		                	public void receivedURIGenerated(FcpConnection c, URIGenerated uriGenerated) {
		                		assert c == connection;
		            			assert uriGenerated != null;
		            			String identifier = uriGenerated.getIdentifier();
		            			String chk = ongoingUploads.get(identifier).getKey();
								if (!uriGenerated.getURI().equals(chk)) {
		            				System.err.println("Were supposed to upload " + chk +
		            						" but calculated to " + uriGenerated.getURI());
		            				System.exit(1);
		            			}
		                	}
		                	
		            		@Override
		            		public void receivedPutSuccessful(FcpConnection c, PutSuccessful putSuccessful) {
		            			assert c == connection;
		            			assert putSuccessful != null;
		            			String identifier = putSuccessful.getIdentifier();
		            			ongoingUploads.get(identifier).getValue().run();
		            			ongoingUploads.remove(identifier);
		            		};
		            	});
            		}
            	});
            	ongoingUploads = new HashMap<String, Map.Entry<String, Runnable>>();
            }
            uploadStarter.execute(new Runnable() {
                public void run() {
                    uploadCounter++;
                    final String identifier = "Upload" + uploadCounter;
                    ongoingUploads.put(identifier, new AbstractMap.SimpleImmutableEntry<String, Runnable>(filename, callback));
                    final ClientPut putter = new ClientPut("CHK@", identifier);
                    putter.setEarlyEncode(true);
                    putter.setPriority(net.pterodactylus.fcp.Priority.bulkSplitfile);
                    putter.setVerbosity(Verbosity.NONE);
                    final long dataLength = file.length();
                    putter.setDataLength(dataLength);
                    FileInputStream in;
                    try {
                        in = new FileInputStream(file);
                        putter.setPayloadInputStream(in);
                        connection.sendMessage(putter);
                        in.close();
                        in = null;
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.err.println("Upload failed for " + file);
                    }
                }
            });
            return true;
        }

        @Override
        public void receivedSimpleProgress(FcpConnection c,
                net.pterodactylus.fcp.SimpleProgress sp) {
            assert c == connection;
            assert sp != null;
            if (!token.equals(sp.getIdentifier())) {
                return;
            }
            progressTotal = sp.getTotal();
            progressRequired = sp.getRequired();
            progressCompleted = sp.getSucceeded();
            printLeft();
        }
        

        private void markDone() {
            done = true;
            synchronized (this) {
                this.notifyAll();
            }
            // Signal to the cleanup thread:
            synchronized (stillRunning) {
                stillRunning.notifyAll();
            }
        }
        
        private void forgetAboutThis() {
            assert done;
            connection.removeFcpListener(this);
            synchronized (stillRunning) {
                stillRunning.remove(page);
                // Signal to the 
                stillRunning.notifyAll();
                printLeft();
            }
        }

        boolean isDone() {
            return done;
        }
    };


    public void doit() {
        FcpSession session;
        try {
            session = new FcpSession("DownloaderFor" + uri);
        } catch (IllegalStateException | IOException e1) {
            e1.printStackTrace();
            return;
        }
        try {
            connection = session.getConnection();
            if (connection == null) {
                throw new IllegalArgumentException("No connection.");
            }
            final SubscribeUSK subscriber = new SubscribeUSK(uri + "-1", "USK");
            subscriber.setActive(true);
    
            final USKUpdateAdapter subscriberListener = new USKUpdateAdapter(subscriber);
            connection.addFcpListener(subscriberListener);

            synchronized (subscriber) {
                try {
                    connection.sendMessage(subscriber);
                    subscriber.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException("Waiting for connection interrupted.");
                } catch (IOException e) {
                    throw new RuntimeException("Hello cannot write.");
                }
            }
            subscriberListener.restart();
            
            boolean moreJobs = false;
            do {
                if (moreJobs) {
                    synchronized (stillRunning) {
                        try {
                            System.out.println("Queue empty. " +
                                               "Still running " +
                                               stillRunning.size() + ".");
                            stillRunning.wait(20000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            System.exit(1);
                        }
                    }
                }
                boolean empty = true;
                do {
                    if (!empty) {
                        try {
                            FetchedPage taken = objectQueue.take();
                            while (!taken.hasParent()) {
                                taken = null;
                                avoidFetching++;
                                if (objectQueue.isEmpty()) {
                                    break;
                                }
                                taken = objectQueue.take();
                            }
                            // Randomize the order by rotating the queue
                            int maxLaps = objectQueue.size() / PARALLEL_JOBS;
                            if (maxLaps == 0) {
                                maxLaps = 1;
                            }
                            int rotateLaps = rand.nextInt(maxLaps);
                            for (int i = 0; i < rotateLaps; i++) {
                                objectQueue.offer(taken);
                                taken = objectQueue.take();
                                while (!taken.hasParent()) {
                                    taken = null;
                                    avoidFetching++;
                                    if (objectQueue.isEmpty()) {
                                        break;
                                    }
                                    taken = objectQueue.take();
                                }
                            }
                            System.out.println("Rotated " + rotateLaps);
                            if (taken == null) {
                                break;
                            }
                            new GetAdapter(taken);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            System.exit(1);
                        }
                    }
                    subscriberListener.restart();
                    empty = objectQueue.isEmpty();
                } while (!empty);
                synchronized (stillRunning) {
                    moreJobs = !stillRunning.isEmpty();
                }
            } while (moreJobs);
            connection.removeFcpListener(subscriberListener);
        } finally {
            removeCleanupThread();
            session.close();
            connection = null;
        }
        showProgress();
    }


    private void showProgress() {
        System.out.println("Fetches: Successful: " + successful + 
                " blocks: " + successfulBlocks +
                " bytes: " + successfulBytes +
                " Failed: " + failed +
                " Recreated: " + recreated +
                " Avoided: " + avoidFetching + ".");

        StringBuilder sb = new StringBuilder();
        synchronized (roots) {
            for (FetchedPage root : roots) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                String edition = root.getURI().substring(uri.length());
                sb.append(edition);
                int succeeded = root.getTreeSizeSucceeded();
                int failed = root.getTreeSizeFailed();
                if (failed > 0) {
                	sb.append(new Formatter().format(" FAILED: %.2f%%.", 100.0 * failed / (failed + succeeded)));
                }
                double estimate = getEstimatedPagesLeft(root);
                if (estimate < Double.POSITIVE_INFINITY) {
                    sb.append(new Formatter().format(" Fetched: %.2f%%.",
                    		100.0 * (failed + succeeded) / (estimate + succeeded)));
                }
                sb.append(" (");
                sb.append(succeeded);
    
                if (failed > 0) {
                    sb.append(" and ");
                    sb.append(failed);
                    sb.append(" failed");
                }
                
                sb.append(")");
            }
        }

        System.out.println("Editions: " + sb.toString());
    }
    
    public static void main(String[] argv) {
        new DownloadAll(argv[0]).doit();
    }

    public void waitForSlot() {
        startCleanupThread();
        synchronized (stillRunning) {
            try {
                while (stillRunning.size() >= PARALLEL_JOBS) {
                    stillRunning.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private synchronized void startCleanupThread() {
        if (cleanupThread == null) {
            cleanupThread = new Thread(
                new Runnable() {
                    public void run () {
                        boolean moreJobs = false;
                        do {
                            if (moreJobs) {
                                synchronized (stillRunning) {
                                    try {
                                        stillRunning.wait(1234567);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                        System.exit(1);
                                    }
                                }
                                Set<GetAdapter> copy;
                                synchronized (stillRunning) {
                                    copy = new HashSet<GetAdapter>(stillRunning.values());
                                }
                                for (GetAdapter ga : copy) {
                                    if (ga.isDone()) {
                                        ga.forgetAboutThis();
                                    }
                                }
                            }
                            synchronized (stillRunning) {
                                moreJobs = !stillRunning.isEmpty();
                            }
                        } while (moreJobs);
                        removeCleanupThread();
                    }
                }
            );
            cleanupThread.start();
        }
    }
    
    private synchronized void removeCleanupThread() {
        cleanupThread = null;
    }
}
