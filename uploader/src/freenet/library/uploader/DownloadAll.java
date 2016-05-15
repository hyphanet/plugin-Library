/*
 */

package freenet.library.uploader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.pterodactylus.fcp.AllData;
import net.pterodactylus.fcp.ClientGet;
import net.pterodactylus.fcp.ClientPut;
import net.pterodactylus.fcp.FcpAdapter;
import net.pterodactylus.fcp.FcpConnection;
import net.pterodactylus.fcp.GetFailed;
import net.pterodactylus.fcp.Priority;
import net.pterodactylus.fcp.PutFailed;
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

	/** Logger. */
	private static final Logger logger = Logger.getLogger(DownloadAll.class.getName());
    
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
    private Map<String, OngoingUpload> ongoingUploads = null;
            
    private int successful = 0;
    private int successfulBlocks = 0;
    private long successfulBytes = 0;
    private int failed = 0;
    private int recreated = 0;
    private int failedRecreated = 0;
    private int avoidFetching = 0;
    private int wrongChkCounterForUpload = 0;
    private int maxObjectQueueSize = 0;
    
    private Random rand = new Random();
    private Date started = new Date();

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
        private Set<FetchedPage> parents = Collections.synchronizedSet(new WeakHashSet<FetchedPage>());
        private Set<FetchedPage> children = Collections.synchronizedSet(new HashSet<FetchedPage>());
        
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
    
        /**
         * fetchedPage is an ancestor, any number of levels, to this
         * page.
         * 
         * @param fetchedPage the ancestor to search for.
         * @return
         */
		public boolean hasParent(FetchedPage fetchedPage) {
			if (parents.contains(fetchedPage)) {
				return true;
			}
			for (FetchedPage parent : parents) {
				if (parent.hasParent(fetchedPage)) {
					return true;
				}
			}
			return false;
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
            if (subscribedUSKUpdate.isNewKnownGood() &&
            		subscribedUSKUpdate.getEdition() > edition) {
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
                logger.info("Found: " + newUri + " Edition: " + edition);
                FetchedPage rootPage = new FetchedPage(newUri);
                synchronized (roots) {
                    roots.add(rootPage);
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
    	
    	public String toString() {
    		return "" + getMean() + " (" + count + ")";
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
    

    private static class OngoingUpload {
    	private final Date started = new Date();
    	private final String filename;
    	private final Runnable callback;
    	
    	public OngoingUpload(String fname, Runnable cback) {
    		filename = fname;
    		callback = cback;
    	}

    	Date getStarted() {
    		return started;
    	}

    	String getFilename() {
			return filename;
		}

		void complete() {
			final long millis = new Date().getTime() - started.getTime();
			final long seconds = millis / 1000;
			final long minutes = seconds / 60;
			final long hours = minutes / 60;
			logger.log(Level.FINE, "Upload completed after {0,number}:{1,number,00}:{2,number,00}.",
					new Object[] {
					hours,
					minutes % 60,
					seconds % 60,
			});
			callback.run();
		}
    }

	/**
     * Show the amount of outstanding work.
     */
    void printLeft() {
    	if (logger.isLoggable(Level.FINEST)) {
            int total = 0;
            int required = 0;
            int completed = 0;
            synchronized (stillRunning) {
                for (GetAdapter value : stillRunning.values()) {
                    total += value.progressTotal;
                    required += value.progressRequired;
                    completed += value.progressCompleted;
                }
                String ongoingUploadsMessage = "";
                if (logger.isLoggable(Level.FINEST) && ongoingUploadsSize() > 0) {
                	Date oldest = null;
                	for (Map.Entry<String, OngoingUpload> entry : ongoingUploads.entrySet()) {
                		if (oldest == null || oldest.compareTo(entry.getValue().getStarted()) > 0) {
                			oldest = entry.getValue().getStarted();
                		}
                	}
                	ongoingUploadsMessage = " and " + ongoingUploads.size() + " uploads";
                	if (oldest != null && new Date().getTime() - oldest.getTime() > TimeUnit.HOURS.toMillis(5)) {
                		ongoingUploadsMessage += new MessageFormat(", oldest from {0,date,long}").format(new Object[] { oldest });
                	}
                	ongoingUploadsMessage += ".";
                }
                logger.finest("Outstanding " + stillRunning.size() + " ClientGet jobs " +
                        "(" + completed + "/" + required + "/" + total + ")" +
                		ongoingUploadsMessage);
            }
    	}
    }

    interface UriProcessor {
    	public boolean processUri(String uri);
    }

    private void readAndProcessYamlData(InputStream inputStream, UriProcessor uriProcessor, String page_uri, int page_level)
    		throws IOException {
        int foundChildren = 0;
        try {
			Object readObject = new YamlReaderWriter().readObject(inputStream); 
            Map<String, Object> map = ((LinkedHashMap<String, Object>) readObject);
            if (map.containsKey("ttab") &&
            		map.containsKey("utab") &&
            		map.containsKey("totalPages")) {
                Map<String, Object> map2 = (Map<String, Object>) map.get("ttab");
                if (map2.containsKey("entries")) {
                    Map<String, BinInfo> entries =
                            (Map<String, Packer.BinInfo>) map2.get("entries");
                    for (BinInfo value : entries.values()) {
                    	try {
                    		String u = (String) value.getID();
                    		if (uriProcessor.processUri(u)) {
                    			foundChildren ++;
                    		}
                    		
                    	} catch (ClassCastException e) {
                    		throw new RuntimeException("Cannot process BinInfo value " + value.getID() + " for " + page_uri, e);
                    	}
                    }
                    Map<String, Object> subnodes =
                            (Map<String, Object>) map2.get("subnodes");
                    logger.log(Level.FINER, "Contains ttab.entries (level {0}) with {1} subnodes", new Object[] {
                    		page_level,
                    		subnodes.size(),
                    });
                    for (String key : subnodes.keySet()) {
                        if (uriProcessor.processUri(key)) {
                        	foundChildren ++;
                        }
                    }
                    return;
                }
            }
            if (map.containsKey("lkey") &&
                    map.containsKey("rkey") &&
                    map.containsKey("entries")) {
            	// Must separate map and array!
                if (map.containsKey("subnodes")) {
                	throw new RuntimeException("This parsing is not complex enough to handle subnodes for terms for " +
                							   page_uri);
                }
                if (map.get("entries") instanceof Map) {
                    Map<String, BinInfo> entries =
                            (Map<String, Packer.BinInfo>) map.get("entries");
                    logger.log(Level.FINE,
                    		"Contains from {1} to {2} (level {0}) with {3} entries.",
                    		new Object[] {
                    			page_level,
                    			map.get("lkey"),
                    			map.get("rkey"),
                    			entries.size()
                    });
                    for (BinInfo value : entries.values()) {
                    	try {
                    		String u = (String) value.getID();
                    		if (uriProcessor.processUri(u)) {
                    			foundChildren ++;
                    		}
                    	} catch (ClassCastException e) {
                    		throw new RuntimeException("Cannot process BinInfo (2) " + value.getID() + " for " + page_uri);
                    	}
                    }
                    return;
                }
                if (map.get("entries") instanceof ArrayList) {
                	// Assuming this is a list of TermPageEntries.
                    logger.log(Level.FINE,
                    		"Contains from {1} to {2} (level {0}) with page entries.",
                    		new Object[] {
                    			page_level,
                    			map.get("lkey"),
                    			map.get("rkey")
                    });
                    return;
                }
            }
            Entry<String, Object> entry = map.entrySet().iterator().next();
            if (entry.getValue() instanceof Map) {
                Map<String, Object> map2 = (Map<String, Object>) entry.getValue();
                if (map2.containsKey("node_min")
                        && map2.containsKey("size")
                        && map2.containsKey("entries")) {
                	logger.log(Level.FINER, "Starts with entry for {1} (level {0}). Searching for subnodes.", new Object[] {
                			page_level,
                			entry.getKey(),
                	});
                	String first = null;
                	String last = null;
                	for (Entry<String, Object> contents : map.entrySet()) {
                		if (contents.getValue() instanceof Map) {
                			if (first == null) {
                				first = contents.getKey();
                			}
                			last = contents.getKey();
                			Map<String, Object> map3 = (Map<String, Object>) contents.getValue();
                			if (map3.containsKey("subnodes")) {
                    			Map<String, Object> subnodes =
                    					(Map<String, Object>) map3.get("subnodes");
                    			logger.log(Level.FINER, "Entry for {1} (level {0}) contains {2} subnodes.", new Object[] {
                    					page_level,
                    					contents.getKey(),
                    					subnodes.size(),
                    			});
                    			
                    			for (String key : subnodes.keySet()) {
                    				if (uriProcessor.processUri(key)) {
                    					foundChildren ++;
                                    }
                    			}
                			}
                			continue;
                		}
                		throw new RuntimeException("Cannot process entries. Entry for " + contents.getKey() + " is not String=Map for " + page_uri);
                	}
                	logger.log(Level.FINER, "Starts with entry for {1} and ended with entry {2} (level {0}).", new Object[] {
                			page_level,
                			first,
                			last,
                	});
                    return;
                }
            }
            logger.severe("Cannot understand contents: " + map);
            System.exit(1);
        } finally {
        	addFoundChildren(page_level, foundChildren);
            logger.exiting(GetAdapter.class.toString(),
            		"receivedAllData added " + foundChildren + " to the queue.");
        }

    }
    
    private class GetAdapter extends FcpAdapter {
        private ClientGet getter;
        private String token;
        private FetchedPage page;
        private int progressTotal;
        private int progressRequired;
        private int progressCompleted;
        private boolean done;
        int waitingLaps;
        public static final int WAITING_FACTOR = 50;

        public GetAdapter(FetchedPage u) {
            page = u;
            getterCounter ++;
            token = "Getter" + getterCounter;
            waitingLaps = 0;
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
         * Called when nothing has happened for a while with this request.
         * @param key The page.
         */
        public void hasBeenWaiting(FetchedPage key) {
            waitingLaps++;
            if (waitingLaps > WAITING_FACTOR * PARALLEL_JOBS) {
                connection.removeFcpListener(this);
                getter = null;
                synchronized (stillRunning) {
                    stillRunning.remove(key);
                }
                if (key.hasParent()) {
                    logger.warning("Restarting fetch for " + key.getURI());
                    new GetAdapter(key);
                } else {
                    logger.finer("Avoid refetching " + key.getURI());
                }
            }
        }

        
        private boolean processAnUri(String uri) {
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
            final int objectQueueSize = objectQueue.size();
            if (objectQueueSize > maxObjectQueueSize) {
            	maxObjectQueueSize = objectQueueSize;
            }
			logger.entering(GetAdapter.class.toString(),
            		"receivedAllData",
            		"receivedAllData for " + token +
                    " adding to the " + objectQueueSize + " elements in the queue " +
                    "(max " + maxObjectQueueSize + ").");
            page.didSucceed();
            UriProcessor uriProcessor = new UriProcessor() {
				@Override
				public boolean processUri(String uri) {
					return processAnUri(uri);
				}
            };
        	final InputStream inputStream = ad.getPayloadInputStream();
            try {
            	readAndProcessYamlData(inputStream, uriProcessor, page.getURI(), page.level);
            } catch (IOException e) {
            	logger.log(Level.SEVERE, "Cannot unpack.", e);
                e.printStackTrace();
                System.exit(1);
            } catch (ClassCastException cce) {
            	logger.log(Level.SEVERE, "Cannot unpack.", cce);
            	cce.printStackTrace();
            	System.exit(1);
            } finally {
                markDone();
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
            logger.warning("receivedGetFailed for " + token + " (" + page.getURI() + ").");
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
         * If we are running on a host where this CHK is actually cached,
         * lets upload it from the cache in an attempt to repair the index.
         * 
         * @param filename of the file to upload.
         * @param callback when the file is successfully uploaded.
         */
        public boolean upload(final String filename, final Runnable callback) {
        	final File dir = new File(".", UploaderPaths.LIBRARY_CACHE);
            if (!dir.canRead()) {
                return false;
            }
            final File file = new File(dir, filename);
            if (!file.canRead()) {
                logger.warning("Cannot find " + file + " in the cache.");
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
		            			String chk = ongoingUploads.get(identifier).getFilename();
								if (!uriGenerated.getURI().equals(chk)) {
		            				logger.severe("Were supposed to resurrect " + chk +
		            						" but the URI calculated to " + uriGenerated.getURI() + ". " +
		            						"Will upload anyway.");
		            				wrongChkCounterForUpload++;
		            			} else {
		            				logger.fine("Resurrecting " + chk);
		            			}
		                	}
		                	
		            		@Override
		            		public void receivedPutSuccessful(FcpConnection c, PutSuccessful putSuccessful) {
		            			assert c == connection;
		            			assert putSuccessful != null;
		            			String identifier = putSuccessful.getIdentifier();
		            			final OngoingUpload foundUpload = ongoingUploads.get(identifier);
								String chk = foundUpload.getFilename();
								if (!putSuccessful.getURI().equals(chk)) {
		            				logger.severe("Uploaded " + putSuccessful.getURI() +
		            						" while supposed to upload " + chk +
		            						". ");
		            			} else {
		            				foundUpload.complete();
		            			}
		            			ongoingUploads.remove(identifier);
		            			synchronized (stillRunning) {
		            				stillRunning.notifyAll();
		            			}
		            		};
		            		
		            		@Override
		            		public void receivedPutFailed(FcpConnection c, PutFailed putFailed) {
		            			assert c == connection;
		            			assert putFailed != null;
		            			String identifier = putFailed.getIdentifier();
		            			final OngoingUpload foundUpload = ongoingUploads.get(identifier);
								String chk = foundUpload.getFilename();
								logger.severe("Uploaded " + chk + " failed.");
		            			failedRecreated++;
		            			ongoingUploads.remove(identifier);
		            			synchronized (stillRunning) {
		            				stillRunning.notifyAll();
		            			}
		            		}
		            	});
            		}
            	});
            	ongoingUploads = new HashMap<String, OngoingUpload>();
            }
            uploadStarter.execute(new Runnable() {
                public void run() {
                    uploadCounter++;
                    final String identifier = "Upload" + uploadCounter;
                    ongoingUploads.put(identifier, new OngoingUpload(filename, callback));
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
                        logger.warning("Upload failed for " + file);
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


    public void doDownload() {
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
                            logger.fine("Queue empty. " +
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
                    for (Entry<FetchedPage, GetAdapter> entry :
                        new HashSet<Entry<FetchedPage, GetAdapter>>(stillRunning.entrySet())) {
                        entry.getValue().hasBeenWaiting(entry.getKey());
                    }
                    synchronized (roots) {
                        final int roots_size = roots.size();
						if (roots_size > 1) {
							int roots_distance = roots_size - 1;
                            if (roots.get(1).getTreeSizeSucceeded() >= roots.get(0).getTreeSizeSucceeded() - roots_distance * roots_distance * roots_distance) {
                                roots.remove(0);
                            }
                        }
                    }

                    FetchedPage lastRoot;
                    synchronized (roots) {
                    	lastRoot = roots.get(roots.size() - 1);
                    }

                    if (!empty) {
                        try {
                            FetchedPage taken = objectQueue.take();
                            while (!taken.hasParent()) {
                                logger.finer("Avoid fetching " + taken.getURI());
                                taken = null;
                                avoidFetching++;
                                if (objectQueue.isEmpty()) {
                                    break;
                                }
                                taken = objectQueue.take();
                            }
                            // Randomize the order by rotating the queue
                            int maxLaps = objectQueue.size();
                            if (maxLaps == 0) {
                                maxLaps = 1;
                            }
                            int toRotate = rand.nextInt(maxLaps);
                            int rotated = 0;
                            assert taken.level > 0;
                            for (int i = 0; i < toRotate; i += taken.hasParent(lastRoot) ? taken.level * taken.level * taken.level : 1) {
                                objectQueue.offer(taken);
                                taken = objectQueue.take();
								while (!taken.hasParent()) {
                                    taken = null;
                                    avoidFetching++;
                                    if (objectQueue.isEmpty()) {
                                        break;
                                    }
                                    taken = objectQueue.take();
                                    assert taken.level > 0;
                                }
                                rotated++;
                            }
                            logger.finer("Rotated " + rotated + " (count to " + toRotate + ").");
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
            if (uploadStarter != null) {
            	uploadStarter.shutdown();
            	try {
					uploadStarter.awaitTermination(1, TimeUnit.HOURS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
            }
            connection.removeFcpListener(subscriberListener);
        } finally {
            removeCleanupThread();
            session.close();
            connection = null;
        }
        showProgress();
    }


    private void showProgress() {
    	String recreatedMessage = "";
    	if (recreated > 0) {
    		recreatedMessage = " Recreated: " + recreated;
    	}
    	if (failedRecreated > 0) {
    		recreatedMessage += " Recreation failed: " + failedRecreated;
    	}
    	String wrongChkCounterForUploadMessage = "";
    	if (wrongChkCounterForUpload > 0) {
    		wrongChkCounterForUploadMessage = " WrongChkUploaded: " + wrongChkCounterForUpload;
    	}
        logger.fine("Fetches: Successful: " + successful + 
                " blocks: " + successfulBlocks +
                " bytes: " + successfulBytes +
                " Failed: " + failed +
                recreatedMessage +
                wrongChkCounterForUploadMessage +
                " Avoided: " + avoidFetching + ".");

        StringBuilder sb = new StringBuilder();
        List<FetchedPage> copiedRoots;
        synchronized (roots) {
            copiedRoots = new ArrayList<FetchedPage>(roots);
        }
        Collections.reverse(copiedRoots);
        boolean first = true;
		for (FetchedPage root : copiedRoots) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String edition = root.getURI().substring(uri.length());
            sb.append(edition);
            int succeeded = root.getTreeSizeSucceeded();
            int failed = root.getTreeSizeFailed();
            if (failed > 0) {
            	sb.append(new Formatter().format(" FAILED: %.1f%%.", 100.0 * failed / (failed + succeeded)));
            }
            double estimate = getEstimatedPagesLeft(root);
            if (estimate < Double.POSITIVE_INFINITY) {
                final double fractionDone = 1.0 * succeeded / (estimate + succeeded);
				sb.append(new Formatter().format(" Fetched: %.1f%%.",
                		100.0 * fractionDone));
				if (first) {
					logger.log(Level.FINER, "ETA: {0,date}, Started: {1,date}. Done {2,number,percent}.",
							new Object[] {
							new Date(new Double(1.0 / fractionDone * (new Date().getTime() - started.getTime())).longValue() +
									 started.getTime()),
							started,
							fractionDone,
					});
					first = false;
				}
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

        System.out.println("Editions: " + sb.toString());
    }

    public void doMove() {
    	int count = 0;
    	File toDirectory = new File("../" + UploaderPaths.LIBRARY_CACHE + ".new");
    	if (!toDirectory.mkdir()) {
    		System.err.println("Could not create the directory " + toDirectory);
    		System.exit(1);
    	}
    	final FetchedPage fetchedPage = new FetchedPage(uri);
		roots.add(fetchedPage);
		objectQueue.add(fetchedPage);
		while (objectQueue.size() > 0) {
			FetchedPage page;
			try {
				page = objectQueue.take();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				System.exit(1);
				return;
			}
			final FetchedPage finalPage = page;
			FileInputStream inputStream;
			try {
				Files.createLink(Paths.get(toDirectory.getPath(), page.uri), Paths.get(page.uri));
				inputStream = new FileInputStream(page.uri);
				count++;
				System.out.println("Read file " + count + " in " + page.uri + " level " + page.level + " left: " + objectQueue.size());
			} catch (IOException e) {
				System.out.println("Cannot find file " + page.uri);
				e.printStackTrace();
				System.exit(1);
				return;
			}
			try {
				readAndProcessYamlData(inputStream,
						new UriProcessor() {
							Set<String> seen = new HashSet<String>();
							@Override
							public boolean processUri(String uri) {
								if (seen.contains(uri)) {
									return false;
					            }
								seen.add(uri);
								objectQueue.offer(finalPage.newChild(uri));
								return true;
							}
					
				}, page.getURI(), page.level);
			} catch (IOException e) {
				System.out.println("Cannot read file " + page.uri);
				e.printStackTrace();
				System.exit(1);
				return;
			}
			
		}
    }
    
    public static void main(String[] argv) {
    	if (argv.length > 1 && argv[0].equals("--move")) {
    		new DownloadAll(argv[1]).doMove();
    	} else {
    		new DownloadAll(argv[0]).doDownload();
    	}
    }

    private int ongoingUploadsSize() {
    	if (ongoingUploads == null) {
    		return 0;
    	}

    	synchronized (ongoingUploads) {
    		return ongoingUploads.size();
    	}
    }

    public void waitForSlot() {
        startCleanupThread();
        synchronized (stillRunning) {
            try {
            	stillRunning.wait(TimeUnit.SECONDS.toMillis(1));
                while (stillRunning.size() + ongoingUploadsSize() * ongoingUploadsSize() >= PARALLEL_JOBS) {
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
