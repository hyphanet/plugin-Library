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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Date;
import java.util.Formatter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import freenet.library.io.FreenetURI;
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

/**
 * Class to download the entire index.
 *
 * When a newer USK is seen, stop the processing and exit.
 */
class DownloadAllOnce {
	/** Logger. */
	private static final Logger logger = Logger.getLogger(DownloadAllOnce.class.getName());

	private ScheduledExecutorService FCPexecutors;
	private ScheduledExecutorService otherExecutors;
	private FcpConnection connection;
	private boolean closingDown = false;
	private File directory;
	private File morePagesDirectory;
	private Set<File> allFiles = new HashSet<File>();
	private Uploads oldUploads = new Uploads();
	private int getterCounter = 0;
	private int uploadCounter = 0;

	private AdHocDataReader reader = new AdHocDataReader();

	private static final long OPERATION_GIVE_UP_TIME = TimeUnit.HOURS.toMillis(2);

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
		Date nextFetchAttempt = new Date();
		StringBuffer logAttempts;
		private long timeToNextFetchAttempt;

		Page(FreenetURI u, Page p) {
			// parent = p,
			uri = u;
			if (p != null) {
				level = p.level + 1;
			}
			fetchTimerReset();
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

		private void calculateNextFetchAttempt() {
			nextFetchAttempt = new Date(new Date().getTime() + timeToNextFetchAttempt);
		}

		void fetchFailed() {
			timeToNextFetchAttempt += 1 + 2 * random.nextInt(Long.valueOf(timeToNextFetchAttempt).intValue()) - timeToNextFetchAttempt / 2;
			calculateNextFetchAttempt();
			logAttempts.append("Failed at ").append(new Date()).append(" and deferred to ").append(nextFetchAttempt).append("\n");
		}

		boolean fetchAvailable() {
			return new Date().after(nextFetchAttempt);
		}

		void fetchTimerReset() {
			timeToNextFetchAttempt = TimeUnit.HOURS.toMillis(4);
			logAttempts = new StringBuffer();
			logAttempts.append("Deferred to ").append(new Date()).append("\n");
			calculateNextFetchAttempt();
		}
	}

	class AvoidRecentFetchesQueue extends RotatingQueue<Page> {
		AvoidRecentFetchesQueue(Random r) {
			super(r);
		}

		public Page pollNotDeferred() {
			int maxLaps = size();
			if (maxLaps > 20) {
				maxLaps = 10;
			}
			do {
				Page page = poll();
				if (page == null) {
					return page;
				}
				if (page.fetchAvailable()) {
					return page;
				}
				logger.finest("Skipped page deferred until " + page.nextFetchAttempt);
				offer(page); // Ignored impossible false status
			} while (maxLaps-- > 0);
			logger.finest("Did not find any not deferred page");
			return null;
		}
	}
			


	private Random random = new Random();
	private RotatingQueue<Page> toParse = new RotatingQueue<Page>(random);
	private RotatingQueue<Page> toFetch = new RotatingQueue<Page>(random);
	private AvoidRecentFetchesQueue toRefetchUnfetchable = new AvoidRecentFetchesQueue(random);
	private RotatingQueue<Page> toRefetch = new RotatingQueue<Page>(random);
	private AvoidRecentFetchesQueue toUploadUnfetchable = new AvoidRecentFetchesQueue(random);

	private int counterParse = 0;
	private int counterFetch = 0;
	private int counterRefetchUnfetchable = 0;
	private int counterRefetch = 0;
	private int counterUploadUnfetchable = 0;
	private int counterRefetchUpload = 0;


	private static String STATISTICS_FORMAT_PREFIX = "%-21s%7d";

	public synchronized final void logStatistics() {
		StringBuffer sb = new StringBuffer();
		sb.append(statisticsLine("toParse", counterParse, toParse));
		sb.append(statisticsLine("toFetch", counterFetch, toFetch));
		sb.append(statisticsLine("toRefetchUnfetchable", counterRefetchUnfetchable, toRefetchUnfetchable));
		if (counterRefetchUpload > 0) {
			sb.append(new Formatter().format(STATISTICS_FORMAT_PREFIX, 
							 "RefetchUpload", counterRefetchUpload)).append("\n");
		}
		sb.append(statisticsLine("toRefetch", counterRefetch, toRefetch));
		sb.append(statisticsLine("toUploadUnfetchable", counterUploadUnfetchable, toUploadUnfetchable));
		if (allFiles.size() > 0) {
			sb.append("Files left to remove: " + allFiles.size() + "\n");
		}
		if (oldUploads.size() > 0) {
			sb.append("Uploads from previous run: " + oldUploads.size() + "\n");
		}
		logger.info("Statistics:\n" + sb.toString() + "End Statistics.");
	}

	private static String STATISTICS_FORMAT = STATISTICS_FORMAT_PREFIX + "%6d%5d%5d%6d%6d%5d%5d\n";

	public final String statisticsLine(String r, int counter, RotatingQueue<Page> rqp) {
		if (rqp.size() > 0 || counter > 0) {
			int arr[] = new int[12];
			for (Page p : rqp) {
				arr[p.level]++;
			}
			return new Formatter()
					.format(STATISTICS_FORMAT, r, counter, rqp.size(), arr[0], arr[1], arr[2], arr[3], arr[4], arr[5])
					.toString();
		}
		return "";
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
				logger.entering(DownloadAllOnce.class.toString(), "receivedAllData", "receivedAllData for " + token);
				try {
					Files.copy(ad.getPayloadInputStream(), page.getFile().toPath(),
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
				logger.fine("receivedGetFailed for " + token + " (" + page.getURI() + ").");
			}

			@Override
			public void receivedSimpleProgress(FcpConnection c, net.pterodactylus.fcp.SimpleProgress sp) {
				assert c == connection;
				assert sp != null;
				if (!token.equals(sp.getIdentifier())) {
					return;
				}
				logger.finest("Progress for " + token + " (" + sp.getSucceeded() + "/" + sp.getRequired() + "/"
						+ sp.getTotal() + ").");
			}
		};
		connection.addFcpListener(listener);
		try {
			connection.sendMessage(getter);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Exception", e);
			return false;
		}
		synchronized (getter) {
			try {
				getter.wait(OPERATION_GIVE_UP_TIME);
			} catch (InterruptedException e) {
				if (!closingDown) {
					logger.log(Level.SEVERE, "Exception", e);
				}
				return false;
			}
		}
		connection.removeFcpListener(listener);

		boolean result = results[0];
		if (result) {
			page.fetchTimerReset();
		} else {
			page.fetchFailed();
		}

		return result;
	}

	private void parse(final Page page) {
		try {
			reader.readAndProcessYamlData(new FileInputStream(page.getFile()), new AdHocDataReader.UriProcessor() {
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
				public void uriSeen() {
				}

				@Override
				public void stringSeen() {
				}

				@Override
				public void childrenSeen(int level, int foundChildren) {
				}


			}, page.getLevel());
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
				if (!identifier.equals(uriGenerated.getIdentifier())) {
					return;
				}
				FreenetURI chk = page.getURI();
				FreenetURI generatedURI;
				try {
					generatedURI = new FreenetURI(uriGenerated.getURI());
				} catch (MalformedURLException e) {
					logger.severe(
							"Were supposed to resurrect " + chk + " but the URI calculated to " + uriGenerated.getURI()
									+ " that is not possible to convert to an URI. Will upload anyway.");
					return;
				}
				if (!generatedURI.equals(chk)) {
					logger.severe("Were supposed to resurrect " + chk + " but the URI calculated to "
							+ uriGenerated.getURI() + ". " + "Will upload anyway.");
				} else {
					logger.finest("Resurrecting " + chk);
				}
			}

			@Override
			public void receivedPutSuccessful(FcpConnection c, PutSuccessful putSuccessful) {
				assert c == connection;
				assert putSuccessful != null;
				if (!identifier.equals(putSuccessful.getIdentifier())) {
					return;
				}
				FreenetURI chk = page.getURI();
				FreenetURI generatedURI = null;
				try {
					try {
						generatedURI = new FreenetURI(putSuccessful.getURI());
					} catch (MalformedURLException e) {
						logger.severe("Uploaded " + putSuccessful.getURI() + " that is not possible to convert to an URI.");
						return;
					}
					if (!generatedURI.equals(chk)) {
						logger.severe("Uploaded " + putSuccessful.getURI() + " while supposed to upload " + chk + ". ");
						return;
					}
					logger.finer("Uploaded " + chk);
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
				if (!identifier.equals(putFailed.getIdentifier())) {
					return;
				}
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
				putter.wait(OPERATION_GIVE_UP_TIME);
			}
			in.close();
			in = null;
		} catch (IOException | NullPointerException e) {
			logger.log(Level.WARNING, "Upload failed for " + page.getFile(), e);
		} catch (InterruptedException e) {
			if (!closingDown) {
				logger.log(Level.WARNING, "Upload interrupted for " + page.getFile(), e);
			}
			return false;
		} finally {
			connection.removeFcpListener(listener);
		}
		return successfuls[0];
	}

	private boolean doRefetchUnfetchable(Page page) {
		counterRefetchUnfetchable++;
		boolean result = fetch(page);
		if (result) {
			add(toParse, page);
		} else {
			add(toRefetchUnfetchable, page);
		}
		return result;
	}

	private boolean doRefetchToUpload(Page page) {
		counterRefetchUpload++;
		boolean result = fetch(page);
		if (result) {
			add(toRefetch, page);
		} else {
			add(toUploadUnfetchable, page);
		}
		return result;
	}

	private boolean doRefetch(Page page) {
		counterRefetch++;
		boolean result = fetch(page);
		if (result) {
			add(toRefetch, page);
		} else {
			handleUnfetchable(page);
		}
		return result;
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

	private boolean doFetch(Page page) {
		counterFetch++;
		boolean result = fetch(page);
		if (result) {
			add(toParse, page);
		} else {
			handleUnfetchable(page);
		}
		return result;
	}

	private void doParse(Page page) {
		counterParse++;
		parse(page);
		if (oldUploads.check(page.getURI())) {
			add(toUploadUnfetchable, page);
		} else {
			add(toRefetch, page);
		}
	}

	private void handleUnfetchable(Page page) {
		if (page.getFile().exists()) {
			add(toUploadUnfetchable, page);
		} else {
			add(toRefetchUnfetchable, page);
		}
	}

	private boolean doUploadUnfetchable(Page page) {
		counterUploadUnfetchable++;
		boolean result = upload(page);
		add(toRefetch, page);
		return result;
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
			if (toFetch.size() > 0) {
				// Don't delete anything if the fetching is not completed.
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
				if (!f.exists()) {
					allFiles.remove(f);
					try {
						oldUploads.check(new FreenetURI(f.getName()));
					} catch (MalformedURLException e) {
						logger.log(Level.WARNING, "File " + f + " was deleted", e);
					}
					return;
				}
				if (f.lastModified() < oldestAge) {
					oldestAge = f.lastModified();
					oldestFile = f;
				}
			}
			allFiles.remove(oldestFile);
			try {
				oldUploads.check(new FreenetURI(oldestFile.getName()));
			} catch (MalformedURLException e) {
				logger.log(Level.WARNING, "Deleting file " + oldestFile, e);
			}
			logger.fine("Removing file " + oldestFile);
			oldestFile.delete();
		}
	}

	/**
	 * Class to keep track of uploads from the previous run.
	 */
	private class Uploads {
		private Set<FreenetURI> fromPreviousRun = new HashSet<FreenetURI>();
		private final static String OLD_UPLOADS_FILENAME = "old_uploads.saved";

		void load() {
			File file = new File(directory, OLD_UPLOADS_FILENAME);
			if (file.exists()) {
				logger.finest("Reading file " + file);
				try {
					FileInputStream f = new FileInputStream(file);
					ObjectInputStream ois = new ObjectInputStream(f);
					fromPreviousRun = (Set<FreenetURI>) ois.readObject();
					ois.close();
				} catch (IOException e) {
					logger.warning("Could not read the file " + file);
				} catch (ClassCastException | ClassNotFoundException e) {
					logger.warning("File " + file + " contains strange object");
				} finally {
					file.delete();
				}
			} else {
				logger.finest("No file " + file);
			}
		}

		private void rotate() {
			String OLD_FILENAME = OLD_UPLOADS_FILENAME + ".old";
			File oldfile = new File(directory, OLD_FILENAME);
			if (oldfile.exists()) {
				oldfile.delete();
			}
			File file = new File(directory, OLD_UPLOADS_FILENAME);
			if (file.exists()) {
				file.renameTo(oldfile);
			}
		}

		synchronized void save() {
			rotate();
			File file = new File(directory, OLD_UPLOADS_FILENAME);
			Set<FreenetURI> set = new HashSet<FreenetURI>();
			for (Page p : toUploadUnfetchable) {
				set.add(p.getURI());
			}
			if (set.size() > 0) {
				logger.finest("Writing file " + file);
				try {
					FileOutputStream f = new FileOutputStream(file);
					ObjectOutputStream oos = new ObjectOutputStream(f);
					oos.writeObject(set);
					oos.close();
					f.close();
				} catch (IOException e) {
					logger.log(Level.WARNING, "Problem writing file " + file, e);
					file.delete();
				}
			} else {
				logger.finest("Nothing to write to file " + file);
			}
		}

		synchronized int size() {
			return fromPreviousRun.size();
		}

		synchronized boolean check(FreenetURI uri) {
			boolean retval = fromPreviousRun.contains(uri);
			fromPreviousRun.remove(uri);
			return retval;
		}
	}

	private abstract class ProcessSomething implements Runnable {
		protected abstract void process();

		public void run() {
			try {
				process();
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Class " + this + " threw exception: " + e, e);
			}
		}
	}

	private class ProcessParse extends ProcessSomething {
		protected void process() {
			Page page = toParse.poll();
			if (page != null) {
				doParse(page);
				otherExecutors.schedule(this, 0, TimeUnit.SECONDS);
			} else {
				otherExecutors.schedule(this, 10, TimeUnit.SECONDS);
			}
		}
	}

	private class ProcessUploadUnfetchable extends ProcessSomething {
		protected void process() {
			if (morePagesDirectory != null) {
				Page page = toRefetchUnfetchable.poll();
				if (page != null) {
					File fromFile = new File(morePagesDirectory, page.getFile().getName());
					try {
						Files.copy(fromFile.toPath(), page.getFile().toPath());
						boolean result = doUploadUnfetchable(page);
						logger.finer("Uploaded Unfetchable" + (result ? "" : "failed") + ".");
						return;
					} catch (UnsupportedOperationException uoe) {
						logger.log(Level.SEVERE, "Could not copy file " + fromFile + " to " + page.getFile() + ".", uoe);
						toRefetchUnfetchable.offer(page);
					} catch (FileAlreadyExistsException faee) {
						logger.log(Level.SEVERE, "Could not copy file " + fromFile + " to " + page.getFile() + ".", faee);
						toUploadUnfetchable.offer(page);
					} catch (IOException ioe) {
						logger.log(Level.SEVERE, "Could not copy file " + fromFile + " to " + page.getFile() + ".", ioe);
						if (page.getFile().exists()) {
						    	page.getFile().delete();
							logger.info("Deleted partial copy " + page.getFile());
						}
						toRefetchUnfetchable.offer(page);
					} catch (SecurityException se) {
						logger.log(Level.SEVERE, "Could not copy file " + fromFile + " to " + page.getFile() + ".", se);
						toRefetchUnfetchable.offer(page);
					}
				}
			}

			Page page = toUploadUnfetchable.poll();
			if (page != null) {
				boolean result = doUploadUnfetchable(page);
				logger.finer("Uploaded Unfetchable" + (result ? "" : "failed") + ".");
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
			Page page = toFetch.poll();
			if (page != null) {
				boolean result = doFetch(page);
				logger.finest("Fetched Fetch" + (result ? "" : " failed") + ".");
				return;
			}

			page = toRefetchUnfetchable.pollNotDeferred();
			if (page != null) {
				String log = page.logAttempts.toString();
				boolean result = doRefetchUnfetchable(page);
				logger.finer(log + "Fetched RefetchUnfetchable" + (result ? "" : " failed") + ".");
				return;
			}

			page = toUploadUnfetchable.pollNotDeferred();
			if (page != null) {
				String log = page.logAttempts.toString();
				boolean result = doRefetchToUpload(page);
				logger.finer(log + "Fetched ToUpload" + (result ? "" : " failed") + ".");
				return;
			}

			page = toRefetch.poll();
			if (page != null) {
				boolean result = doRefetch(page);
				logger.finer("Fetched Refetch" + (result ? "" : " failed") + ".");
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

	private void run(FreenetURI u, File morePagesDir) {
		morePagesDirectory = morePagesDir;
		FCPexecutors = Executors.newScheduledThreadPool(10);
		otherExecutors = Executors.newScheduledThreadPool(1);
		directory = new File("library-download-all-once-db");
		if (directory.exists()) {
			oldUploads.load();
			allFiles.addAll(Arrays.asList(directory.listFiles()));
			CleanupOldFiles cleanUp = new CleanupOldFiles();
			cleanUp.setHandle(otherExecutors.scheduleWithFixedDelay(cleanUp, 30000, 5, TimeUnit.SECONDS));
		} else {
			directory.mkdir();
		}

		otherExecutors.scheduleAtFixedRate(new Runnable() {
			public void run() {
				logStatistics();
			}
		}, 10, 30, TimeUnit.SECONDS);
		for (int i = 0; i < 10; i++) {
			FCPexecutors.scheduleWithFixedDelay(new ProcessFetches(), 20 + i, 4, TimeUnit.SECONDS);
		}
		for (int i = 0; i < 4; i++) {
			FCPexecutors.scheduleWithFixedDelay(new ProcessUploadUnfetchable(), 40 + i, 1, TimeUnit.SECONDS);
		}
		otherExecutors.schedule(new ProcessParse(), 2000, TimeUnit.MILLISECONDS);
		otherExecutors.scheduleWithFixedDelay(new Runnable() {
			public void run() {
				oldUploads.save();
			}
		}, 100, 20, TimeUnit.MINUTES);
		FcpSession session;
		try {
			session = new FcpSession("DownloadAllOnceFor" + u);
		} catch (IllegalStateException | IOException e1) {
			logger.log(Level.SEVERE, "Exception", e1);
			return;
		}
		try {
			run2(session, u);
		} finally {
			waitTermination(TimeUnit.SECONDS.toMillis(1));
			closingDown = true;
			logger.info("Shutdown.");
			FCPexecutors.shutdown();
			otherExecutors.shutdown();
			oldUploads.save();
			waitTermination(TimeUnit.MINUTES.toMillis(1) + OPERATION_GIVE_UP_TIME);
			logger.info("Shutdown now (after long wait).");
			FCPexecutors.shutdownNow();
			FCPexecutors = null;
			otherExecutors.shutdownNow();
			otherExecutors = null;
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
			public void receivedSubscribedUSKUpdate(FcpConnection fcpConnection,
					SubscribedUSKUpdate subscribedUSKUpdate) {
				assert fcpConnection == connection;
				FreenetURI newUri;
				try {
					newUri = new FreenetURI(subscribedUSKUpdate.getURI());
				} catch (MalformedURLException e) {
					throw new RuntimeException(e);
				}
				if (subscribedUSKUpdate.isNewKnownGood() && !newUri.equals(newUris[0])) {
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
				logger.info("Next edition seen.");
			} catch (InterruptedException e) {
				throw new RuntimeException("Subscription interrupted.");
			} catch (IOException e) {
				throw new RuntimeException("Subscription can't write.");
			}
		}
	}

	private void waitTermination(long ms) {
		try {
			FCPexecutors.awaitTermination(ms, TimeUnit.MILLISECONDS);
			otherExecutors.awaitTermination(1 + ms / 10, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			throw new RuntimeException("Waiting for jobs.");
		}
	}

	public static void main(String[] argv) throws InterruptedException {
		FreenetURI u;
		try {
			u = new FreenetURI(argv[0]);
		} catch (MalformedURLException e) {
			logger.log(Level.SEVERE, "Exception", e);
			System.exit(2);
			return;
		}

		File morePagesDir = null;
		if (argv.length > 1) {
		    morePagesDir = new File(argv[1]);
		    if (!morePagesDir.exists()) {
			logger.severe("Directory " + morePagesDir + " does not exist.");
			System.exit(2);
			return;
		    }
		    if (!morePagesDir.isDirectory()) {
			logger.severe("File " + morePagesDir + " is not a directory.");
			System.exit(2);
			return;
		    }
		}

		new DownloadAllOnce().run(u, morePagesDir);
	}
}
