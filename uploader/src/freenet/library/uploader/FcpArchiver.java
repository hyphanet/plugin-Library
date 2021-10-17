package freenet.library.uploader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import freenet.support.Base64;
import freenet.crypt.SHA256;

import plugins.Library.Priority;
import plugins.Library.io.FreenetURI;
import plugins.Library.io.ObjectStreamReader;
import plugins.Library.io.ObjectStreamWriter;
import plugins.Library.io.serial.LiveArchiver;
import plugins.Library.util.exec.SimpleProgress;
import plugins.Library.util.exec.TaskAbortException;

import net.pterodactylus.fcp.ClientPut;
import net.pterodactylus.fcp.FcpAdapter;
import net.pterodactylus.fcp.FcpConnection;
import net.pterodactylus.fcp.PutFailed;
import net.pterodactylus.fcp.PutFetchable;
import net.pterodactylus.fcp.PutSuccessful;
import net.pterodactylus.fcp.URIGenerated;
import net.pterodactylus.fcp.Verbosity;

public class FcpArchiver<T,  S extends ObjectStreamWriter & ObjectStreamReader> 
		implements LiveArchiver<T, plugins.Library.util.exec.SimpleProgress> {
	private FcpConnection connection;
	private File cacheDir;
	private ObjectStreamReader<T> reader;
	private ObjectStreamWriter<T> writer;
	private int totalBlocksStillUploading = 0;
	private Priority priorityLevel;

	/**
	 * Before synchronizing on stillRunning, be sure to synchronize
	 * connection!
	 */
	private Map<String, PushAdapter> stillRunning = new HashMap<>();
	private Thread cleanupThread;

	public FcpArchiver(FcpConnection fcpConnection, 
					   File directory,
					   S rw,
					   String mime, int s,
					   Priority pl) {
		connection = fcpConnection;
		cacheDir = directory;
		reader = rw;
		writer = rw;
		priorityLevel = pl;
	}
	
	private net.pterodactylus.fcp.Priority getPriority() {
		switch (priorityLevel) {
		case Interactive:
			return net.pterodactylus.fcp.Priority.interactive;
		case Bulk:
			return net.pterodactylus.fcp.Priority.bulkSplitfile;
		}
		return net.pterodactylus.fcp.Priority.bulkSplitfile;		
	}

	@Override
	public void pull(plugins.Library.io.serial.Serialiser.PullTask<T> task)
			throws TaskAbortException {
		pullLive(task, null);
	}

	@Override
	public void push(plugins.Library.io.serial.Serialiser.PushTask<T> task)
			throws TaskAbortException {
		pushLive(task, null);
	}

	/**
	 * Initial implementation, fetch everything from the cache. This means 
	 * that we cannot take over someone else's index. 
	 */
	@Override
	public void pullLive(plugins.Library.io.serial.Serialiser.PullTask<T> task,
			SimpleProgress progress) throws TaskAbortException {
		if (cacheDir.exists()) {
			String cacheKey = null;
			if (task.meta instanceof FreenetURI) {
				cacheKey = task.meta.toString();
			} else if (task.meta instanceof String) {
				cacheKey = (String) task.meta;
			} else if (task.meta instanceof byte[]) {
				cacheKey = Base64.encode(SHA256.digest((byte[]) task.meta));
			}

			try {
				if(cacheDir != null && cacheDir.exists() && cacheDir.canRead()) {
					File cached = new File(cacheDir, cacheKey);
					if(cached.exists() && 
							cached.length() != 0 &&
							cached.canRead()) {
						InputStream is = new FileInputStream(cached);
						task.data = (T) reader.readObject(is);
						is.close();
					}
				}
					
				if (progress != null) {
					progress.addPartKnown(0, true);
				}
			} catch (IOException e) {
				System.out.println("IOException:");
				e.printStackTrace();
				throw new TaskAbortException("Failed to read content from local tempbucket", e, true);
			}
			return;
		}
		throw new UnsupportedOperationException(
				"Cannot find the key " +
				task.meta +
				" in the cache.");
	}
	
	private static long lastUriMillis = 0;

	private class PushAdapter extends FcpAdapter {
    	private ClientPut putter;
    	private String identifier;
		private String token;
		private FreenetURI uri;
		private int progressTotal;
		private int progressCompleted;
		private boolean done;
		private long started;

		public PushAdapter(ClientPut p, String i, String t) {
    		putter = p;
    		identifier = i;
    		token = t;
    		uri = null;
    		progressTotal = 0;
    		progressCompleted = 0;
    		synchronized (stillRunning) {
    			stillRunning.put(token, this);
				printLeft();
    		}
    		started = System.currentTimeMillis();
		}

		/**
		 * Show the amount of outstanding work.
		 */
		void printLeft() {
			int total = 0;
			int completed = 0;
			synchronized (stillRunning) {
				for (Map.Entry<String, PushAdapter> entry : stillRunning.entrySet()) {
					total += entry.getValue().progressTotal;
					completed += entry.getValue().progressCompleted;
				}
				totalBlocksStillUploading = total - completed;
				System.out.println("Outstanding " + stillRunning.size() + " jobs " +
						"(" + completed + "/" + total + ")");
			}
		}

		private String at() {
			return " took " + (System.currentTimeMillis() - started) + "ms";
		}

		@Override
        public void receivedPutSuccessful(FcpConnection c, PutSuccessful ps) {
			assert c == connection;
			assert ps != null;
			if (!identifier.equals(ps.getIdentifier()))
				return;
			System.out.println("receivedPutSuccessful for " + token + at());
			markDone();
    	}
    	
    	@Override
        public void receivedPutFetchable(FcpConnection c, PutFetchable pf) {
			assert c == connection;
			assert pf != null;
			if (!identifier.equals(pf.getIdentifier()))
				return;
			System.out.println("receivedPutFetchable for " + token + at());
			synchronized (this) {
				this.notifyAll();
			}
    	}
    	

    	@Override
        public void receivedPutFailed(FcpConnection c, PutFailed pf) {
			assert c == connection;
			assert pf != null;
			if (!identifier.equals(pf.getIdentifier()))
				return;
            synchronized (putter) {
                putter.notify();
            }
			System.err.println("receivedPutFailed for " + token + at() + " aborting.");
    		markDone();
    		System.exit(1);
        }
        
    	@Override
        public void receivedSimpleProgress(FcpConnection c,
        		net.pterodactylus.fcp.SimpleProgress sp) {
			assert c == connection;
			assert sp != null;
			if (!identifier.equals(sp.getIdentifier()))
				return;
			if (sp.getFailed() > 0 ||
					sp.getFatallyFailed() > 0) {
				System.out.println(token + "failed - aborted.");
			}
			progressCompleted = sp.getSucceeded();
			progressTotal = sp.getTotal();
			printLeft();
			synchronized (stillRunning) {
				stillRunning.notifyAll();
			}
    	}

    	public void receivedURIGenerated(FcpConnection c, URIGenerated uriGenerated) {
    		assert c == connection;
			assert uriGenerated != null;
			if (!identifier.equals(uriGenerated.getIdentifier()))
				return;
			String sinceTime = "";
			if (lastUriMillis != 0) {
				sinceTime = " (" + (System.currentTimeMillis() - lastUriMillis) + "ms since last URI)";
			}
			System.out.println("receivedURIGenerated for " + token + at() + sinceTime);
			try {
				uri = new FreenetURI(uriGenerated.getURI());
			} catch (MalformedURLException e) {
				System.err.println("receivedURIGenerated failed with URI: " + uriGenerated.getURI() +
						" for " + token + at() + " aborting.");
	    		markDone();
	    		System.exit(1);
			}
			synchronized (this) {
				this.notifyAll();
			}
			lastUriMillis = System.currentTimeMillis();
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
				stillRunning.remove(token);
				stillRunning.notifyAll();
				printLeft();
			}
        }

    	boolean isDone() {
    		return done;
    	}
    	
    	FreenetURI getURI() {
    		return uri;
    	}
    };


	private static int counter = 1;

	@Override
	public void pushLive(plugins.Library.io.serial.Serialiser.PushTask<T> task,
			SimpleProgress progress) throws TaskAbortException {
		// Slow down the build up of the queue.
		try {
			int stillRunningSize;
			synchronized (stillRunning) {
				stillRunningSize = stillRunning.size();
			}
			final int uploading = totalBlocksStillUploading + stillRunningSize;
			Thread.sleep(1 + 10 * uploading * uploading);
		} catch (InterruptedException e1) {
			throw new RuntimeException("Unexpected interrupt");
		}
		if (connection == null) {
			throw new IllegalArgumentException("No connection.");
		}
		final String identifier = "FcpArchiver" + counter;
		final String token = "FcpArchiverPushLive" + counter;
		counter++;
        final ClientPut putter = new ClientPut("CHK@", identifier);
        putter.setClientToken(token);
        putter.setEarlyEncode(true);
        putter.setPriority(getPriority());
        putter.setVerbosity(Verbosity.ALL);
        
        // Writing to file.
		File file = new File(cacheDir, token);
		FileOutputStream fileOut = null;
		try {
			fileOut = new FileOutputStream(file);
			writer.writeObject(task.data, fileOut);
		} catch (IOException e) {
			throw new TaskAbortException("Cannot write to file " + file, e);
		} finally {
			try {
				fileOut.close();
			} catch (IOException e) {
				throw new TaskAbortException("Cannot close file " + file, e);
			}
		}

        final long dataLength = file.length();
		putter.setDataLength(dataLength);
        
        FileInputStream in;
		try {
			in = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			throw new TaskAbortException("Cannot read from file " + file, e);
		}
        putter.setPayloadInputStream(in);

		PushAdapter putterListener = new PushAdapter(putter, identifier, token);
        connection.addFcpListener(putterListener);
        try {
        	if (progress != null) {
        		progress.addPartKnown(1, true);
        	}
			connection.sendMessage(putter);
			in.close();
		} catch (IOException e) {
			throw new TaskAbortException("Cannot send message", e);
		}

        // Wait for identifier
        synchronized (putterListener) {
			while (putterListener.getURI() == null) {
				try {
					putterListener.wait();
				} catch (InterruptedException e) {
					throw new TaskAbortException("Iterrupted wait", e);
				}
			}
		}

        if (progress != null) {
        	progress.addPartDone();
        }
        task.meta = putterListener.getURI();

        // Moving file.
        file.renameTo(new File(cacheDir, putterListener.getURI().toString()));

		startCleanupThread();        
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
										stillRunning.wait();
									} catch (InterruptedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
				        		}
			            		Set<PushAdapter> copy;
			            		synchronized (stillRunning) {
			            			copy = new HashSet<PushAdapter>(stillRunning.values());
			            		}
		        				for (PushAdapter pa : copy) {
		        					if (pa.isDone()) {
		        						pa.forgetAboutThis();
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

	@Override
	public void waitForAsyncInserts() throws TaskAbortException {
		boolean moreJobs = false;
		do {
			if (moreJobs) {
				synchronized (stillRunning) {
					try {
						stillRunning.wait(TimeUnit.HOURS.toMillis(1));
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			synchronized (stillRunning) {
				moreJobs = !stillRunning.isEmpty();
			}
		} while (moreJobs);
	}
}
