/*
 */

/*
 * Log levels used:
 * None/Warning: Serious events and small problems.
 * FINE: Stats for fetches and overview of contents of fetched keys. Minor events.
 * FINER: Queue additions, length, ETA, rotations.
 * FINEST: Really minor events.
 */

package freenet.library.uploader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.pterodactylus.fcp.*;
import plugins.Library.io.FreenetURI;

/**
 * Class to download the entire index.
 */
class FetchAllOnce extends AdHocDataReader {
    private static final int PARALLEL_JOBS = 10;
    private static final int PARALLEL_UPLOADS = 3;

    private static final Logger logger = Logger.getLogger(FetchAllOnce.class.getName());

    public final Map<FetchedPage, GetAdapter> stillRunning = new HashMap<>();
    private FreenetURI uri;
    private FreenetURI newUri;
    private int edition;
    private FcpConnection connection;
    private static int getterCounter = 0;
    private static int uploadCounter = 0;
    private LinkedBlockingQueue<FetchedPage> objectQueue = new LinkedBlockingQueue<>();
    private Thread cleanupThread;
    private final List<FetchedPage> roots = new ArrayList<>();

    private ExecutorService uploadStarter = null;
    private Map<String, OngoingUpload> ongoingUploads = null;

    private int successful = 0;
    private int successfulBlocks = 0;
    private long successfulBytes = 0;
    private int failed = 0;
    private long uriUrisSeen = 0;
    private long stringUrisSeen = 0;
    private int recreated = 0;
    private int failedRecreated = 0;
    private int avoidFetching = 0;
    private int uploadsStarted = 0;
    private int avoidRecreate = 0;
    private int wrongChkCounterForUpload = 0;
    private int maxObjectQueueSize = 0;

    private Random rand = new Random();
    private Date started = new Date();

    public FetchAllOnce(FreenetURI u) {
        uri = u;
    }

    // TODO: move to a separate util class
    public static class WeakHashSet<T> implements Set<T> {
        // We just use the keys and let all values be TOKEN.
        private Map<T, Object> map = new WeakHashMap<>();
        private static final Object TOKEN = new Object();

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
        private Set<FetchedPage> parents = Collections.synchronizedSet(new WeakHashSet<>());
        private Set<FetchedPage> children = Collections.synchronizedSet(new HashSet<>());

        private FreenetURI uri;
        int level;
        private boolean succeeded;
        private boolean failed;

        FetchedPage(FreenetURI u) {
            this(u, 0);
        }

        FetchedPage(FreenetURI u, int l) {
            uri = u;
            level = l;
        }

        void addParent(FetchedPage fp) {
            parents.add(fp);
        }

        void addChild(FetchedPage fp) {
            children.add(fp);
        }

        FetchedPage newChild(FreenetURI u) {
            FetchedPage child = new FetchedPage(u, level + 1);
            child.addParent(this);
            addChild(child);
            return child;
        }

        FreenetURI getURI() {
            return uri;
        }

        boolean hasParent() {
            return !parents.isEmpty();
        }

        private FetchedPage[] getParents() {
            // Even though parents and children are synchronized we
            // encountered some ConcurrentModificationException when
            // fetching them through iterators so we avoid that.
            return parents.toArray(new FetchedPage[0]);
        }

        private FetchedPage[] getChildren() {
            return children.toArray(new FetchedPage[0]);
        }

        /**
         * fetchedPage is an ancestor, any number of levels, to this
         * page.
         *
         * @param fetchedPage the ancestor to search for.
         */
        public boolean hasParent(FetchedPage fetchedPage) {
            if (parents.contains(fetchedPage)) {
                return true;
            }
            for (FetchedPage parent : getParents()) {
                if (parent != null && parent.hasParent(fetchedPage)) {
                    return true;
                }
            }
            return false;
        }

        int getTreeSize() {
            int size = 1;
            for (FetchedPage child : getChildren()) {
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
            for (FetchedPage child : getChildren()) {
                size += child.getTreeSizeSucceeded();
            }
            return size;
        }

        int getTreeSizeFailed() {
            int size = failed ? 1 : 0;
            for (FetchedPage child : getChildren()) {
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

        public FetchedPage findUri(FreenetURI u) {
            if (u.equals(uri)) {
                return this;
            }
            for (FetchedPage child : getChildren()) {
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
        private final Object subscriber;

        public USKUpdateAdapter(Object s) {
            subscriber = s;
        }

        @Override
        public void receivedSubscribedUSKUpdate(FcpConnection fcpConnection, SubscribedUSKUpdate subscribedUSKUpdate) {
            assert fcpConnection == connection;
            if (// FIXME subscribedUSKUpdate.isNewKnownGood() &&
                    subscribedUSKUpdate.getEdition() > edition) {
                updated = true;
                try {
                    newUri = new FreenetURI(subscribedUSKUpdate.getURI());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
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

    private class StatisticsAccumulator {
        private int count = 0;
        private int sum = 0;

        void addSample(int found) {
            count++;
            sum += found;
        }

        double getMean() {
            return (double) sum / count;
        }

        public String toString() {
            return getMean() + " (" + count + ")";
        }
    }

    private Map<Integer, StatisticsAccumulator> statistics = new HashMap<>();

    private void addFoundChildren(int level, int foundChildren) {
        if (!statistics.containsKey(level)) {
            statistics.put(level, new StatisticsAccumulator());
        }
        statistics.get(level).addSample(foundChildren);
    }

    private double getEstimatedPagesLeft(FetchedPage page) {
        double estimate = 0.0;
        double extra = 0.0;
        Map<Integer, Integer> pagesPerLevel = new HashMap<>();
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
        private final FreenetURI freenetURI;
        private final Runnable callback;

        public OngoingUpload(FreenetURI fname, Runnable cback) {
            freenetURI = fname;
            callback = cback;
        }

        Date getStarted() {
            return started;
        }

        FreenetURI getKey() {
            return freenetURI;
        }

        void complete() {
            final long millis = new Date().getTime() - started.getTime();
            final long seconds = millis / 1000;
            final long minutes = seconds / 60;
            final long hours = minutes / 60;
            logger.log(Level.FINE, "Upload completed after {0,number}:{1,number,00}:{2,number,00}.",
                    new Object[]{
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
                    synchronized (ongoingUploads) {
                        for (Map.Entry<String, OngoingUpload> entry : ongoingUploads.entrySet()) {
                            if (oldest == null || oldest.compareTo(entry.getValue().getStarted()) > 0) {
                                oldest = entry.getValue().getStarted();
                            }
                        }
                    }
                    ongoingUploadsMessage = " and " + ongoingUploadsSize() + " uploads";
                    if (oldest != null && new Date().getTime() - oldest.getTime() > TimeUnit.HOURS.toMillis(5)) {
                        ongoingUploadsMessage += new MessageFormat(", oldest from {0,date,long}").format(new Object[]{oldest});
                    }
                }
                logger.finest("Outstanding " + stillRunning.size() + " ClientGet jobs " +
                        "(" + completed + "/" + required + "/" + total + ")" + ongoingUploadsMessage);
            }
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
            getterCounter++;
            token = "Getter" + getterCounter;
            waitingLaps = 0;
            getter = new ClientGet(page.getURI().toString(), token);
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
         *
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

        private boolean processAnUri(FreenetURI uri) {
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
                public FreenetURI getURI() {
                    return page.getURI();
                }

                @Override
                public int getLevel() {
                    return page.level;
                }

                @Override
                public boolean processUri(FreenetURI uri) {
                    return processAnUri(uri);
                }

                @Override
                public void uriSeen() {
                    uriUrisSeen++;
                }

                @Override
                public void stringSeen() {
                    stringUrisSeen++;
                }

                @Override
                public void childrenSeen(int level, int foundChildren) {
                    addFoundChildren(level, foundChildren);
                }
            };
            final InputStream inputStream = ad.getPayloadInputStream();
            try {
                readAndProcessYamlData(inputStream, uriProcessor, page.level);
            } catch (IOException | ClassCastException e) {
                logger.log(Level.SEVERE, "Cannot unpack.", e);
                e.printStackTrace();
                System.exit(1);
            } finally {
                markDone();
                successful++;
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
            failed++;
            showProgress();
            upload(page, () -> {
                objectQueue.offer(page);
                recreated++;
            });
        }

        /**
         * We have detected that we cannot download a certain CHK.
         * <p>
         * If this CHK is actually cached, lets upload it from
         * the cache in an attempt to repair the index.
         *
         * @param page     the URI to upload.
         * @param callback when the file is successfully uploaded.
         */
        public boolean upload(final FetchedPage page, final Runnable callback) {
            final File dir = new File(".", UploaderPaths.LIBRARY_CACHE);
            if (!dir.canRead()) {
                logger.warning("Cannot find cache dir");
                return false;
            }
            final File file = new File(dir, page.getURI().toString());
            if (!file.canRead()) {
                logger.warning("Cannot find " + file + " in the cache.");
                return false;
            }
            if (uploadStarter == null) {
                uploadStarter = Executors.newSingleThreadExecutor();
                uploadStarter.execute(() -> connection.addFcpListener(new FcpAdapter() {
                    @Override
                    public void receivedURIGenerated(FcpConnection c, URIGenerated uriGenerated) {
                        assert c == connection;
                        assert uriGenerated != null;
                        String identifier = uriGenerated.getIdentifier();
                        FreenetURI chk;
                        synchronized (ongoingUploads) {
                            chk = ongoingUploads.get(identifier).getKey();
                        }
                        FreenetURI generatedURI;
                        try {
                            generatedURI = new FreenetURI(uriGenerated.getURI());
                        } catch (MalformedURLException e) {
                            logger.severe("Were supposed to resurrect " + chk +
                                    " but the URI calculated to " + uriGenerated.getURI() +
                                    " that is not possible to convert to an URI. Will upload anyway.");
                            wrongChkCounterForUpload++;
                            return;
                        }
                        if (!generatedURI.equals(chk)) {
                            logger.severe("Were supposed to resurrect " + chk +
                                    " but the URI calculated to " + uriGenerated.getURI() + ". " +
                                    "Will upload anyway.");
                            wrongChkCounterForUpload++;
                        } else {
                            logger.finest("Resurrecting " + chk);
                        }
                    }

                    @Override
                    public void receivedPutSuccessful(FcpConnection c, PutSuccessful putSuccessful) {
                        assert c == connection;
                        assert putSuccessful != null;
                        String identifier = putSuccessful.getIdentifier();
                        OngoingUpload ongoingUpload;
                        synchronized (ongoingUploads) {
                            ongoingUpload = ongoingUploads.get(identifier);
                        }
                        final OngoingUpload foundUpload = ongoingUpload;
                        FreenetURI chk = foundUpload.getKey();
                        FreenetURI generatedURI = null;
                        try {
                            generatedURI = new FreenetURI(putSuccessful.getURI());
                        } catch (MalformedURLException e) {
                            logger.severe("Uploaded " + putSuccessful.getURI() +
                                    " that is not possible to convert to an URI.");
                        }
                        if (generatedURI != null) {
                            if (!generatedURI.equals(chk)) {
                                logger.severe("Uploaded " + putSuccessful.getURI() +
                                        " while supposed to upload " + chk + ". ");
                            } else {
                                foundUpload.complete();
                            }
                        }
                        synchronized (ongoingUploads) {
                            ongoingUploads.remove(identifier);
                            ongoingUploads.notifyAll();
                        }
                        synchronized (stillRunning) {
                            stillRunning.notifyAll();
                        }
                    }

                    @Override
                    public void receivedPutFailed(FcpConnection c, PutFailed putFailed) {
                        assert c == connection;
                        assert putFailed != null;
                        String identifier = putFailed.getIdentifier();
                        OngoingUpload ongoingUpload;
                        synchronized (ongoingUploads) {
                            ongoingUpload = ongoingUploads.get(identifier);
                        }
                        final OngoingUpload foundUpload = ongoingUpload;
                        FreenetURI chk = foundUpload.getKey();
                        logger.severe("Uploaded " + chk + " failed.");
                        failedRecreated++;
                        synchronized (ongoingUploads) {
                            ongoingUploads.remove(identifier);
                            ongoingUploads.notifyAll();
                        }
                        synchronized (stillRunning) {
                            stillRunning.notifyAll();
                        }
                    }
                }));
                ongoingUploads = new HashMap<>();
            }
            uploadsStarted++;
            uploadStarter.execute(() -> {
                if (!page.hasParent()) {
                    avoidRecreate++;
                    return;
                }
                uploadCounter++;
                final String identifier = "Upload" + uploadCounter;
                synchronized (ongoingUploads) {
                    ongoingUploads.put(identifier, new OngoingUpload(page.getURI(), callback));
                    ongoingUploads.notifyAll();
                }
                final ClientPut putter = new ClientPut("CHK@", identifier);
                putter.setEarlyEncode(true);
                putter.setPriority(Priority.bulkSplitfile);
                putter.setVerbosity(Verbosity.NONE);
                final long dataLength = file.length();
                putter.setDataLength(dataLength);
                try (FileInputStream in = new FileInputStream(file)) {
                    putter.setPayloadInputStream(in);
                    connection.sendMessage(putter);
                } catch (IOException | NullPointerException e) {
                    e.printStackTrace();
                    logger.warning("Upload failed for " + file);
                }
                while (true) {
                    synchronized (ongoingUploads) {
                        if (ongoingUploads.size() < PARALLEL_UPLOADS) {
                            break;
                        }
                        try {
                            ongoingUploads.wait(TimeUnit.SECONDS.toMillis(3));
                        } catch (InterruptedException e) {
                            throw new RuntimeException("Waiting for upload slot terminated.");
                        }
                    }
                }
            });
            return true;
        }

        @Override
        public void receivedSimpleProgress(FcpConnection c, SimpleProgress sp) {
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

            synchronized (stillRunning) {
                stillRunning.notifyAll(); // Signal to the cleanup thread
            }
        }

        private void forgetAboutThis() {
            assert done;
            connection.removeFcpListener(this);
            synchronized (stillRunning) {
                stillRunning.remove(page);
                stillRunning.notifyAll(); // Signal to the cleanup thread
                printLeft();
            }
        }

        boolean isDone() {
            return done;
        }
    }

    private int uploadsWaiting() {
        return uploadsStarted - uploadCounter - avoidRecreate;
    }

    private void ageRunning() {
        final HashSet<Entry<FetchedPage, GetAdapter>> stillRunningCopy;
        synchronized (stillRunning) {
            stillRunningCopy = new HashSet<>(stillRunning.entrySet());
        }
        for (Entry<FetchedPage, GetAdapter> entry : stillRunningCopy) {
            entry.getValue().hasBeenWaiting(entry.getKey());
        }
    }

    public void doDownload() {
        FcpSession session = null;
        try {
            session = new FcpSession("DownloaderFor" + uri);
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
                    // NOTE: The response/callback will be only after changing the version of the index.
                    //  If it need to start the process on a known version, restart Fred so that
                    //  FCP will forget about known version.
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
                            logger.fine("Queue empty. Still running " + stillRunning.size());
                            stillRunning.wait(20_000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            System.exit(1);
                        }
                    }
                }

                boolean empty;
                do {
                    ageRunning();
                    synchronized (roots) {
                        final int roots_size = roots.size();
                        if (roots_size > 1) {
                            int roots_distance = roots_size - 1;
                            if (roots.get(1).getTreeSizeSucceeded() >=
                                    roots.get(0).getTreeSizeSucceeded()
                                            - roots_distance * roots_distance * roots_distance) {
                                roots.remove(0);
                            }
                        }
                    }

                    FetchedPage lastRoot;
                    synchronized (roots) {
                        lastRoot = roots.get(roots.size() - 1);
                    }

                    // Randomize the order by rotating the queue
                    int maxLaps = objectQueue.size();
                    if (maxLaps == 0) {
                        maxLaps = 1;
                    }
                    int toRotate = rand.nextInt(maxLaps);
                    int rotated = 0;
                    int counted = 0;

                    while (!objectQueue.isEmpty()) {
                        FetchedPage taken;
                        try {
                            taken = objectQueue.take();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            System.exit(1);
                            continue;
                        }
                        if (!taken.hasParent()) {
                            logger.finer("Avoid fetching " + taken.getURI());
                            avoidFetching++;
                            continue;
                        }

                        counted += taken.level * taken.level * taken.level;
                        if (counted < toRotate) {
                            rotated++;
                            objectQueue.offer(taken);
                            continue;
                        }

                        if (!taken.hasParent(lastRoot) && rand.nextInt(100) > 0) {
                            logger.finer("Defer fetching non-last " + taken.getURI());
                            objectQueue.offer(taken);
                            continue;
                        }

                        logger.finest("Rotated " + rotated + " (count to " + toRotate + ").");
                        new GetAdapter(taken);
                        break;
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
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
            return;
        } finally {
            removeCleanupThread();
            if (session != null) {
                session.close();
            }
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
        if (avoidRecreate > 0) {
            recreatedMessage += " Recreation avoided: " + avoidRecreate;
        }
        String urisSeenMessage = "";
        if (uriUrisSeen > 0 || stringUrisSeen > 0) {
            urisSeenMessage = " StringUrisSeen: " + stringUrisSeen + "/" + (uriUrisSeen + stringUrisSeen);
            urisSeenMessage += new Formatter().format(" (%.1f%%)", 100.0 * stringUrisSeen / (uriUrisSeen + stringUrisSeen));
        }

        String wrongChkCounterForUploadMessage = "";
        if (wrongChkCounterForUpload > 0) {
            wrongChkCounterForUploadMessage = " WrongChkUploaded: " + wrongChkCounterForUpload;
        }
        logger.fine("Fetches: Successful: " + successful +
                " blocks: " + successfulBlocks +
                " bytes: " + successfulBytes +
                " Failed: " + failed +
                urisSeenMessage +
                recreatedMessage +
                wrongChkCounterForUploadMessage +
                " Avoided: " + avoidFetching);

        StringBuilder sb = new StringBuilder();
        List<FetchedPage> copiedRoots;
        synchronized (roots) {
            copiedRoots = new ArrayList<>(roots);
        }
        Collections.reverse(copiedRoots);
        boolean first = true;
        for (FetchedPage root : copiedRoots) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            long edition = root.getURI().getEdition();
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
                            new Object[]{
                                    new Date(new Double(1.0 / fractionDone * (new Date().getTime() - started.getTime())).longValue()
                                            + started.getTime()),
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

    /**
     * 1. chdir to the directory with all the files.
     * 2. Give parameters --move CHK/filename
     *    The CHK/filename is of the top file (in library.index.lastpushed.chk).
     */
    public void doMove() {
        int count = 0;
        File toDirectory = new File("../" + UploaderPaths.LIBRARY_CACHE + ".new2");
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
            } catch (InterruptedException e) { // TODO Auto-generated catch block
                e.printStackTrace();
                System.exit(1);
                return;
            }
            final FetchedPage finalPage = page;
            FileInputStream inputStream;
            try {
                Files.createLink(Paths.get(toDirectory.getPath(), page.uri.toString()), Paths.get(page.uri.toString()));
                inputStream = new FileInputStream(page.uri.toString());
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
                            @Override
                            public FreenetURI getURI() {
                                return finalPage.getURI();
                            }

                            @Override
                            public int getLevel() {
                                return 1;
                            }

                            Set<FreenetURI> seen = new HashSet<>();

                            @Override
                            public boolean processUri(FreenetURI uri) {
                                if (seen.contains(uri)) {
                                    return false;
                                }
                                seen.add(uri);
                                objectQueue.offer(finalPage.newChild(uri));
                                return true;
                            }

                            @Override
                            public void uriSeen() {
                            }

                            @Override
                            public void stringSeen() {
                            }

                            @Override
                            public void childrenSeen(int level, int foundChildren) {
                            }

                        }, page.level);
            } catch (IOException e) {
                System.out.println("Cannot read file " + page.uri);
                e.printStackTrace();
                System.exit(1);
                return;
            }

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
                for (int i = 0; i < uploadsWaiting() + ongoingUploadsSize() + stillRunning.size(); i++) {
                    stillRunning.wait(TimeUnit.SECONDS.toMillis(1 + uploadsWaiting() + uploadsWaiting()));
                }
                while (stillRunning.size() >= PARALLEL_JOBS) {
                    stillRunning.wait(1 + TimeUnit.MINUTES.toMillis(2));
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private synchronized void startCleanupThread() {
        if (cleanupThread == null) {
            cleanupThread = new Thread(() -> {
                boolean moreJobs = false;
                do {
                    if (moreJobs) {
                        synchronized (stillRunning) {
                            try {
                                stillRunning.wait(1_234_567);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                System.exit(1);
                            }
                        }
                        Set<GetAdapter> copy;
                        synchronized (stillRunning) {
                            copy = new HashSet<>(stillRunning.values());
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
            });
            cleanupThread.start();
        }
    }

    private synchronized void removeCleanupThread() {
        cleanupThread = null;

        Set<GetAdapter> copy;
        synchronized (stillRunning) {
            copy = new HashSet<>(stillRunning.values());
        }
        for (GetAdapter ga : copy) {
            ga.markDone();
            ga.forgetAboutThis();
        }
    }
}
