package freenet.library.uploader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.pterodactylus.fcp.ClientPut;
import net.pterodactylus.fcp.FcpAdapter;
import net.pterodactylus.fcp.FcpConnection;
import net.pterodactylus.fcp.FinishedCompression;
import net.pterodactylus.fcp.PutFailed;
import net.pterodactylus.fcp.PutFetchable;
import net.pterodactylus.fcp.PutSuccessful;
import net.pterodactylus.fcp.StartedCompression;
import net.pterodactylus.fcp.URIGenerated;
import net.pterodactylus.fcp.Verbosity;

class FileClientPutter {
	protected FcpConnection connection;

	/**
	 * Before synchronizing on stillRunning, be sure to synchronize
	 * connection!
	 */
	protected Map<String, PushAdapter> stillRunning =
			new HashMap<String, PushAdapter>();
	private Thread cleanupThread;
	
	FileClientPutter(FcpConnection fcpConnection) {
		connection = fcpConnection;
	}

	protected net.pterodactylus.fcp.Priority getPriority() {
		return net.pterodactylus.fcp.Priority.bulkSplitfile;		
	}
	
	int getQueuedSize() {
		synchronized (stillRunning) {
			return stillRunning.size();
		}
	}

	protected class PushAdapter extends FcpAdapter {
    	private ClientPut putter;
    	private String identifier;
		private String token;
		private String uri;
		private int progressTotal;
		private int progressCompleted;
		private boolean done;

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
				System.out.println("Outstanding " + stillRunning.size() + " ClientPut jobs " +
						"(" + completed + "/" + total + ")");
			}
		}
		
		@Override
        public void receivedPutSuccessful(FcpConnection c, PutSuccessful ps) {
			assert c == connection;
			assert ps != null;
			if (!identifier.equals(ps.getIdentifier()))
				return;
			System.out.println("receivedPutSuccessful for " + token);
			markDone();
    	}
    	
    	@Override
        public void receivedPutFetchable(FcpConnection c, PutFetchable pf) {
			assert c == connection;
			assert pf != null;
			if (!identifier.equals(pf.getIdentifier()))
				return;
			System.out.println("receivedPutFetchable for " + token);
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
			System.out.println("receivedPutFailed for " + token);
    		System.exit(1);
    		markDone();
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
				System.out.println(token + " failed - aborted.");
				markDone();
			}
			progressCompleted = sp.getSucceeded();
			progressTotal = sp.getTotal();
			System.out.println("receivedSimpleProgess for " + token + ": " +
					sp.getSucceeded() + "/" + sp.getTotal());
			printLeft();
    	}
    	
    	@Override
    	public void receivedStartedCompression(FcpConnection c, 
    			StartedCompression startedCompression) {
    		assert c == connection;
			assert startedCompression != null;
			if (!identifier.equals(startedCompression.getIdentifier()))
				return;
			System.out.println("receivedStartedCompression for " + token);
    	}

    	@Override
    	public void receviedFinishedCompression(FcpConnection c, 
    			FinishedCompression finishedCompression) {
    		assert c == connection;
			assert finishedCompression != null;
			if (!identifier.equals(finishedCompression.getIdentifier()))
				return;
			System.out.println("receivedFinishedCompression for " + token);
    	}

    	public void receivedURIGenerated(FcpConnection c, URIGenerated uriGenerated) {
    		assert c == connection;
			assert uriGenerated != null;
			if (!identifier.equals(uriGenerated.getIdentifier()))
				return;
			System.out.println("receivedURIGenerated for " + token);
			uri = uriGenerated.getURI();
			synchronized (this) {
				this.notifyAll();
			}
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
    	
    	String getURI() {
    		return uri;
    	}
    };


	protected static int counter = 1;

	PushAdapter startFileUpload(final String token, File file)
			throws IOException {
		final String identifier = "FcpArchiver" + counter;
		counter++;
        final ClientPut putter = new ClientPut("CHK@", identifier);
        putter.setClientToken(token);
        putter.setEarlyEncode(true);
        putter.setPriority(getPriority());
        putter.setVerbosity(Verbosity.ALL);
        
        final long dataLength = file.length();
		putter.setDataLength(dataLength);
        
        FileInputStream in = new FileInputStream(file);
        putter.setPayloadInputStream(in);

		PushAdapter putterListener = new PushAdapter(putter, identifier, token);
        connection.addFcpListener(putterListener);
        
        connection.sendMessage(putter);
        in.close();

		startCleanupThread();        

		return putterListener;
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

}
