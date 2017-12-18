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
import java.nio.file.StandardCopyOption;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
import freenet.library.io.FreenetURI;
import freenet.library.io.YamlReaderWriter;
import freenet.library.io.serial.Packer;
import freenet.library.io.serial.Packer.BinInfo;

/**
 * Class to download the entire index.
 *
 * When a newer USK is seen, stop the processing and exit.
 */
class DownloadAllOnce {
    private static final int PARALLEL_JOBS = 10;
    private static final int PARALLEL_UPLOADS = 3;

    /** Logger. */
    private static final Logger logger = Logger.getLogger(DownloadAllOnce.class.getName());

    private ScheduledExecutorService executors;
    private FcpConnection connection;
    private File directory;
    private Set<File> allFiles = new HashSet<File>();
    private int getterCounter = 0;
    private int uploadCounter = 0;

    private AdHocDataReader reader = new AdHocDataReader();

    class RotatingQueue<E> extends LinkedBlockingQueue<E> {
	public RotatingQueue(Random r) {
	    random = r;
	}

	@Override
	public E poll() {
	    int toRotate = 0;
	    int s = size();
	    if (s > 0) {
		toRotate = random.nextInt(s);
	    }
	    while (true) {
		E taken;

		taken = super.poll();
		if (taken == null) {
		    return null;
		}
		if (--toRotate > 0) {
		    offer(taken); // Ignoring impossible false status.
		    continue;
		}
		return taken;
	    }
	}

	@Override
	public E poll(long l, TimeUnit u) {
	    throw new IllegalStateException("Not implemented");
	}
	
	@Override
	public E take() throws InterruptedException {
	    E taken = poll();
	    if (taken == null) {
		return super.take();
	    }
	    return taken;
	}
    }


    /**
     * A class to keep track of the Pages we work with.
     */
    private class Page {
        // private Page parent;
        
        private FreenetURI uri;
        private int level = 0;
        
        Page(FreenetURI u, Page p) {
	    // parent = p,
            uri = u;
	    if (p != null) {
		level = p.level + 1;
	    }
        }

        FreenetURI getURI() {
            return uri;
        }

	int getLevel() {
	    return level;
	}

	File getFile() {
	    return new File(directory, getURI().toString().replace("/", "__"));
	}
    }

    private static class NotImplementedYet
	extends UnsupportedOperationException {
    }

    private Random random = new Random();
    private RotatingQueue<Page> toFetch = new RotatingQueue<Page>(random);
    private RotatingQueue<Page> toUploadUnfetchable = new RotatingQueue<Page>(random);
    private RotatingQueue<Page> toParse = new RotatingQueue<Page>(random);
    private RotatingQueue<Page> toRefetchUnfetchable = new RotatingQueue<Page>(random);
    private RotatingQueue<Page> toRefetch = new RotatingQueue<Page>(random);

    private int counterFetch = 0;
    private int counterUploadUnfetchable = 0;
    private int counterParse = 0;
    private int counterRefetchUnfetchable = 0;
    private int counterRefetch = 0;


    public synchronized final void printStatistics() {
	logger.info("Statistics");
	printStatisticsLine("toFetch", counterFetch, toFetch);
	printStatisticsLine("toUploadUnfetchable", counterUploadUnfetchable, toUploadUnfetchable);
	printStatisticsLine("toParse", counterParse, toParse);
	printStatisticsLine("toRefetchUnfetchable", counterRefetchUnfetchable, toRefetchUnfetchable);
	printStatisticsLine("toRefetch", counterRefetch, toRefetch);
	if (allFiles.size() > 0) {
	    System.out.println("To remove: " + allFiles.size());
	}
    }

    private static String STATISTICS_FORMAT = "%-21s%7d%6d%5d%5d%6d%6d%5d%5d";
    public final void printStatisticsLine(String r, int counter, RotatingQueue<Page> rqp) {
	if (rqp.size() > 0 || counter > 0) {
	    int arr[] = new int[12];
	    for (Page p : rqp) {
		arr[p.level]++;
	    }
	    System.out.println(new Formatter().format(STATISTICS_FORMAT, r,
						      counter,
						      rqp.size(),
						      arr[0],
						      arr[1],
						      arr[2],
						      arr[3],
						      arr[4],
						      arr[5]));
	}
    }

    private boolean fetch(final Page page) {
	int counter;
	synchronized (this) {
	    counter = ++getterCounter;
	}
	final String token = "Getter" + counter;
	final ClientGet getter = new ClientGet(page.getURI().toString(), token);
	getter.setPriority(Priority.prefetch);
	getter.setVerbosity(Verbosity.NONE);
	final boolean[] results = new boolean[1];
	results[0] = false;
	FcpAdapter listener = new FcpAdapter() {
		@Override
		public void receivedAllData(FcpConnection c, AllData ad) {
		    assert c == connection;
		    assert ad != null;
		    if (!token.equals(ad.getIdentifier())) {
			return;
		    }
		    logger.entering(DownloadAllOnce.class.toString(),
				    "receivedAllData",
				    "receivedAllData for " + token);
		    try {
			Files.copy(ad.getPayloadInputStream(),
				   page.getFile().toPath(),
				   StandardCopyOption.REPLACE_EXISTING);
		    } catch (IOException ioe) {
			page.getFile().delete();
			synchronized (getter) {
			    getter.notify();
			}
			return;
		    }
		    results[0] = true;
		    synchronized (getter) {
			getter.notify();
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
		}

		@Override
		public void receivedSimpleProgress(FcpConnection c,
						   net.pterodactylus.fcp.SimpleProgress sp) {
		    assert c == connection;
		    assert sp != null;
		    if (!token.equals(sp.getIdentifier())) {
			return;
		    }
		    logger.finest("Progress for " + token + " (" + sp.getSucceeded() + "/" + sp.getRequired() + "/" + sp.getTotal() + ").");
		}
	    };
	connection.addFcpListener(listener);
	try {
	    connection.sendMessage(getter);
	} catch (IOException e) {
	    e.printStackTrace();
	    return false;
	}
	synchronized (getter) {
	    try {
		getter.wait();
	    } catch (InterruptedException e) {
		e.printStackTrace();
		return false;
	    }
	}
	connection.removeFcpListener(listener);
	
	return results[0];
    }

    private void parse(final Page page) {
	try {
	    reader.readAndProcessYamlData(new FileInputStream(page.getFile()),
					  new AdHocDataReader.UriProcessor() {
		    @Override
		    public FreenetURI getURI() {
			return page.getURI();
		    }
		    
		    @Override
					   public int getLevel() {
					       return page.getLevel();
					   }

					   Set<FreenetURI> seen = new HashSet<FreenetURI>();
					   @Override
					   public boolean processUri(FreenetURI uri) {
					       if (seen.contains(uri)) {
						   return false;
					       }
					       seen.add(uri);
					       handleNew(new Page(uri, page));
					       return true;
					   }

					   @Override
					   public void uriSeen() {}

					   @Override
					   public void stringSeen() {}
					
					   @Override
					   public void childrenSeen(int level, int foundChildren) {}
					

	    },
					  page.getLevel());
	} catch (IOException ioe) {
	    page.getFile().delete();
  	}
    }

    private boolean upload(final Page page) {
	final boolean[] successfuls = new boolean[1];
	successfuls[0] = false;
	int counter;
	synchronized (this) {
	    counter = ++uploadCounter;
	}
	final String identifier = "Upload" + counter;
	final ClientPut putter = new ClientPut("CHK@", identifier);
	putter.setEarlyEncode(true);
	putter.setPriority(net.pterodactylus.fcp.Priority.bulkSplitfile);
	putter.setVerbosity(Verbosity.NONE);
	final long dataLength = page.getFile().length();
	putter.setDataLength(dataLength);

	final FcpAdapter listener = new FcpAdapter() {
		@Override
		public void receivedURIGenerated(FcpConnection c, URIGenerated uriGenerated) {
		    assert c == connection;
		    assert uriGenerated != null;
		    String identifier = uriGenerated.getIdentifier();
		    FreenetURI chk = page.getURI();
		    FreenetURI generatedURI;
		    try {
			generatedURI = new FreenetURI(uriGenerated.getURI());
		    } catch (MalformedURLException e) {
			logger.severe("Were supposed to resurrect " + chk +
				      " but the URI calculated to " + uriGenerated.getURI() +
				      " that is not possible to convert to an URI. Will upload anyway.");
			return;
		    }
		    if (!generatedURI.equals(chk)) {
			logger.severe("Were supposed to resurrect " + chk +
				      " but the URI calculated to " + uriGenerated.getURI() + ". " +
				      "Will upload anyway.");
		    } else {
			logger.finest("Resurrecting " + chk);
		    }
		}

		@Override
		public void receivedPutSuccessful(FcpConnection c, PutSuccessful putSuccessful) {
		    assert c == connection;
		    assert putSuccessful != null;
		    String identifier = putSuccessful.getIdentifier();
		    FreenetURI chk = page.getURI();
		    FreenetURI generatedURI = null;
		    try {
			try {
			    generatedURI = new FreenetURI(putSuccessful.getURI());
			} catch (MalformedURLException e) {
			    logger.severe("Uploaded " + putSuccessful.getURI() +
					  " that is not possible to convert to an URI.");
			    return;
			}
			if (!generatedURI.equals(chk)) {
			    logger.severe("Uploaded " + putSuccessful.getURI() +
					  " while supposed to upload " + chk +
					  ". ");
			    return;
			}
			logger.finest("Resurrected " + chk);
			successfuls[0] = true;
		    } finally {
			synchronized (putter) {
			    putter.notify();
			}
		    }
		}

		@Override
		public void receivedPutFailed(FcpConnection c, PutFailed putFailed) {
		    assert c == connection;
		    assert putFailed != null;
		    String identifier = putFailed.getIdentifier();
		    FreenetURI chk = page.getURI();
		    logger.severe("Uploaded " + chk + " failed.");
		    synchronized (putter) {
			putter.notify();
		    }
		}
	    };
	connection.addFcpListener(listener);
	FileInputStream in;
	try {
	    in = new FileInputStream(page.getFile());
	    putter.setPayloadInputStream(in);
	    connection.sendMessage(putter);
	    synchronized (putter) {
		putter.wait();
	    }
	    in.close();
	    in = null;
	} catch (IOException | NullPointerException e) {
	    e.printStackTrace();
	    logger.warning("Upload failed for " + page.getFile());
	} catch (InterruptedException e) {
	    e.printStackTrace();
	    logger.warning("Upload interrupted for " + page.getFile());
	} finally {
	    connection.removeFcpListener(listener);
	}
	return successfuls[0];
    }

    private void doRefetchUnfetchable(Page page) {
	if (fetch(page)) {
	    add(toParse, page);
	} else {
	    add(toRefetchUnfetchable, page);
	}
	counterRefetchUnfetchable++;
    }

    private void doRefetch(Page page) {
	if (fetch(page)) {
	    add(toRefetch, page);
	} else {
	    handleUnfetchable(page);
	}
	counterRefetch++;
    }

    private void handleNew(Page page) {
	if (page.getFile().exists()) {
	    page.getFile().setLastModified(System.currentTimeMillis());
	    allFiles.remove(page.getFile());
	    add(toParse, page);
	} else {
	    add(toFetch, page);
	}
    }

    private void doFetch(Page page) {
	if (fetch(page)) {
	    add(toParse, page);
	} else {
	    handleUnfetchable(page);
	}
	counterFetch++;
    }

    private void doParse(Page page) {
	parse(page);
	add(toRefetch, page);
	counterParse++;
    }

    private void handleUnfetchable(Page page) {
	if (page.getFile().exists()) {
	    add(toUploadUnfetchable, page);
	} else {
	    add(toRefetchUnfetchable, page);
	}
    }

    private void doUploadUnfetchable(Page page) {
	if (upload(page)) {
	    add(toRefetch, page);
	} else {
	    add(toRefetchUnfetchable, page);
	}
	counterUploadUnfetchable++;
    }


    private void add(RotatingQueue<Page> whereto, Page p) {
	whereto.offer(p);
    }

    private class CleanupOldFiles implements Runnable {
	ScheduledFuture<?> handle = null;

	public ScheduledFuture<?> setHandle(ScheduledFuture<?> h) {
	    handle = h;
	    return h;
	}

	public void run() {
	    if (toParse.size() > 0) {
		// Don't delete anything if the parsing is not completed.
		return;
	    }
	    if (allFiles.size() == 0) {
		if (handle != null) {
		    handle.cancel(true);
		    handle = null;
		}
		return;
	    }
	    // Find the oldest one.
	    long oldestAge = Long.MAX_VALUE;
	    File oldestFile = null;
	    for (File f : allFiles) {
		if (f.lastModified() < oldestAge) {
		    oldestAge = f.lastModified();
		    oldestFile = f;
		}
	    }
	    allFiles.remove(oldestFile);
	    System.out.println("Removing file " + oldestFile);
	    oldestFile.delete();
	}
    }

    private abstract class ProcessSomething implements Runnable {
	protected abstract void process();

	public void run() {
	    try {
		process();
	    } catch (Exception e) {
		System.out.println("Class " + this + " threw exception: " + e);
		e.printStackTrace();
	    }
	}
    }

    private class ProcessParse extends ProcessSomething {
	protected void process() {
	    Page page = toParse.poll();
	    if (page != null) {
		doParse(page);
	    }
	}
    }

    private class ProcessUploadUnfetchable extends ProcessSomething {
	protected void process() {
	    Page page = toUploadUnfetchable.poll();
	    if (page != null) {
		doUploadUnfetchable(page);
		return;
	    }
	}
    }

    /**
     * This is the bulk of all fetches.
     *
     * Mostly Fetch, if any, but sometimes one of the refetches.
     */
    private class ProcessFetches extends ProcessSomething {
	protected void process() {
	    int refetchable = toRefetch.size() + toRefetchUnfetchable.size();
	    if (random.nextInt(1 + refetchable) < 1000 + toFetch.size() * toFetch.size() / 100) {
		Page page = toFetch.poll();
		if (page != null) {
		    logger.finest("Fetch Fetch");
		    doFetch(page);
		    return;
		}
	    }

	    if (random.nextInt(1 + refetchable) < toRefetchUnfetchable.size()) {
		Page page = toRefetchUnfetchable.poll();
		if (page != null) {
		    logger.finest("Fetch RefetchUnfetchable");
		    doRefetchUnfetchable(page);
		    return;
		}
	    }

	    Page page = toRefetch.poll();
	    if (page != null) {
		logger.finest("Fetch Refetch");
		doRefetch(page);
		return;
	    }
	}
    }

    private class ProcessRefetchUnfetchable extends ProcessSomething {
	protected void process() {
	    Page page = toRefetchUnfetchable.poll();
	    if (page != null) {
		doRefetchUnfetchable(page);
		return;
	    }
	}
    }

    private class ProcessRefetch extends ProcessSomething {
	protected void process() {
	    Page page = toRefetch.poll();
	    if (page != null) {
		doRefetch(page);
		return;
	    }
	}
    }

    private void run(FreenetURI u) {
	executors = Executors.newScheduledThreadPool(10);
	Set<ScheduledFuture<?>> futures = new HashSet<ScheduledFuture<?>>();
	directory = new File("library-download-all-once-db");
	if (directory.exists()) {
	    allFiles.addAll(Arrays.asList(directory.listFiles()));
	    CleanupOldFiles cleanUp = new CleanupOldFiles();
	    futures.add(cleanUp.setHandle(executors.scheduleWithFixedDelay(cleanUp, 500, 1, TimeUnit.MINUTES)));
	} else {
	    directory.mkdir();
	}
	futures.add(executors.scheduleWithFixedDelay(new Runnable() {
		public void run() {
		    printStatistics();
		}
	    }, 10, 30, TimeUnit.SECONDS));
	for (int i = 0; i < 9; i++) {
	    futures.add(executors.scheduleWithFixedDelay(new ProcessFetches(), 20 + i, 4, TimeUnit.SECONDS));
	}
	futures.add(executors.scheduleWithFixedDelay(new ProcessRefetchUnfetchable(), 240, 1, TimeUnit.MINUTES));
	futures.add(executors.scheduleWithFixedDelay(new ProcessRefetch(), 500, 33, TimeUnit.SECONDS));
	for (int i = 0; i < 3; i++) {
	    futures.add(executors.scheduleWithFixedDelay(new ProcessUploadUnfetchable(), 40 + i, 2, TimeUnit.SECONDS));
	}
	futures.add(executors.scheduleWithFixedDelay(new ProcessParse(), 2, 2, TimeUnit.SECONDS));
	FcpSession session;
	try {
	    session = new FcpSession("DownloadAllOnceFor" + u);
        } catch (IllegalStateException | IOException e1) {
            e1.printStackTrace();
            return;
        }
	try {
	    run2(session, u);
	} finally {
	    waitTermination(TimeUnit.SECONDS.toMillis(1));
	    logger.info("Shutdown with " + futures.size() + " processors.");
	    executors.shutdown();
	    waitTermination(TimeUnit.SECONDS.toMillis(2000));
	    for (Iterator<ScheduledFuture<?>> futureIterator = futures.iterator();
		 futureIterator.hasNext(); ) {
		ScheduledFuture<?> future = futureIterator.next();
		if (future.isDone()) {
		    futureIterator.remove();
		}
	    }
	    logger.info("Shutdown now (after long wait) with " + futures.size() + " processors left.");
	    executors.shutdownNow();
	    executors = null;
	    session.close();
	    session = null;
	    logger.info("Shutdown now completed.");
	}
    }

    private void run2(FcpSession session, FreenetURI uri) {
	connection = session.getConnection();
	if (connection == null) {
	    throw new IllegalArgumentException("No connection.");
	}
	final SubscribeUSK subscriber = new SubscribeUSK(uri + "-1", "USK");
	subscriber.setActive(true);
	final int[] editions = new int[1];
	final FreenetURI[] newUris = new FreenetURI[1];
	editions[0] = 0;
	FcpAdapter listener = new FcpAdapter() {
		@Override
		public void receivedSubscribedUSKUpdate(FcpConnection fcpConnection, SubscribedUSKUpdate subscribedUSKUpdate) {
		    assert fcpConnection == connection;
		    FreenetURI newUri;
		    try {
			newUri = new FreenetURI(subscribedUSKUpdate.getURI());
		    } catch (MalformedURLException e) {
			throw new RuntimeException(e);
		    }
		    if (subscribedUSKUpdate.isNewKnownGood()
			&& !newUri.equals(newUris[0])) {
			newUris[0] = newUri;
			editions[0] = subscribedUSKUpdate.getEdition();
			synchronized (subscriber) {
			    subscriber.notify();
			}
		    }
		}
	    };
	connection.addFcpListener(listener);

	synchronized (subscriber) {
	    try {
		connection.sendMessage(subscriber);
		subscriber.wait(); // Wait until found
		handleNew(new Page(newUris[0], null));
		subscriber.wait(); // Work until next one found
		System.out.println("Next edition seen.");
	    } catch (InterruptedException e) {
		throw new RuntimeException("Subscription interrupted.");
	    } catch (IOException e) {
		throw new RuntimeException("Subscription can't write.");
	    }
	}
    }

    private void waitTermination(long ms) {
	try {
	    executors.awaitTermination(ms, TimeUnit.MILLISECONDS);
	} catch (InterruptedException e) {
	    throw new RuntimeException("Waiting for jobs.");
	}
    }

    public static void main(String[] argv) throws InterruptedException {
	FreenetURI u;
	try {
	    u = new FreenetURI(argv[0]);
	} catch (MalformedURLException e) {
	    e.printStackTrace();
	    System.exit(2);
	    return;
	}

	new DownloadAllOnce().run(u);
    }
}
