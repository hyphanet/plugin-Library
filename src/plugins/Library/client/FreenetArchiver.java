/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.client;

import static freenet.support.Logger.error;
import plugins.Library.util.exec.SimpleProgress;
import plugins.Library.util.exec.ProgressParts;
import plugins.Library.util.exec.TaskAbortException;
import plugins.Library.io.serial.LiveArchiver;
import plugins.Library.io.ObjectStreamReader;
import plugins.Library.io.ObjectStreamWriter;

import com.db4o.ObjectContainer;

import freenet.client.HighLevelSimpleClient;
import freenet.client.ClientMetadata;
import freenet.client.InsertContext;
import freenet.client.InsertException;
//import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
//import freenet.client.FetchWaiter;
import freenet.client.InsertBlock;
//import freenet.client.InsertContext;
import freenet.client.InsertException;
//import freenet.client.PutWaiter;
//import freenet.client.async.ClientGetter;
//import freenet.client.async.ClientPutter;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientPutCallback;
import freenet.client.async.ClientPutter;
import freenet.client.async.DatabaseDisabledException;
import freenet.client.events.ClientEventListener;
import freenet.client.events.ClientEvent;
import freenet.client.events.SplitfileProgressEvent;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.node.NodeClientCore;
import freenet.support.Logger;
import freenet.support.MutableBoolean;
import freenet.support.api.Bucket;
import freenet.support.io.BucketTools;
import freenet.support.io.Closer;
import freenet.support.io.FileBucket;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
** Converts between a map of {@link String} to {@link Object}, and a freenet
** key. An {@link ObjectStreamReader} and an {@link ObjectStreamWriter} is
** used to do the hard work once the relevant streams have been established,
** from temporary {@link Bucket}s.
**
** Supports a local cache.
** FIXME: NOT GARBAGE COLLECTED! Eventually will fill up all available disk space!
**
** @author infinity0
*/
public class FreenetArchiver<T>
implements LiveArchiver<T, SimpleProgress>, RequestClient {

	final protected NodeClientCore core;
	final protected ObjectStreamReader reader;
	final protected ObjectStreamWriter writer;

	final protected String default_mime;
	final protected int expected_bytes;
	private static File cacheDir;
	/** If true, we will insert data semi-asynchronously. That is, we will start the
	 * insert, with ForceEncode enabled, and return the URI as soon as possible. The
	 * inserts will continue in the background, and before inserting the final USK,
	 * the caller should call waitForAsyncInserts(). 
	 * 
	 * FIXME SECURITY: This should be relatively safe, because it's all CHKs, as 
	 * long as the content isn't easily predictable.
	 * 
	 * FIXME PERFORMANCE: This dirty hack is unnecessary if we split up 
	 * IterableSerialiser.push into a start phase and a stop phase.
	 * 
	 * The main purpose of this mechanism is to minimise memory usage: Normally the
	 * data being pushed remains in RAM while we do the insert, which can take a very
	 * long time. */
	static final boolean SEMI_ASYNC_PUSH = true;
	
	private final HashSet<PushCallback> semiAsyncPushes = new HashSet<PushCallback>();
	private final ArrayList<InsertException> pushesFailed = new ArrayList<InsertException>();
	
	public static void setCacheDir(File dir) {
		cacheDir = dir;
	}
	
	public static File getCacheDir() {
		return cacheDir;
	}

	public FreenetArchiver(NodeClientCore c, ObjectStreamReader r, ObjectStreamWriter w, String mime, int size) {
		if (c == null) {
			throw new IllegalArgumentException("Can't create a FreenetArchiver with a null NodeClientCore!");
		}
		core = c;
		reader = r;
		writer = w;
		default_mime = mime;
		expected_bytes = size;
	}
	
	public <S extends ObjectStreamWriter & ObjectStreamReader> FreenetArchiver(NodeClientCore c, S rw, String mime, int size) {
		this(c, rw, rw, mime, size);
	}

	/**
	** {@inheritDoc}
	**
	** This implementation expects metdata of type {@link FreenetURI}.
	*/
	/*@Override**/ public void pullLive(PullTask<T> task, final SimpleProgress progress) throws TaskAbortException {
		HighLevelSimpleClient hlsc = core.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS);
		Bucket tempB = null; InputStream is = null;

		try {
			try {

				long startTime = System.currentTimeMillis();
				
				FreenetURI furi = (FreenetURI)task.meta;
				
				if(cacheDir != null && cacheDir.exists() && cacheDir.canRead()) {
					File cached = new File(cacheDir, furi.toASCIIString());
					if(cached.exists() && cached.length() != 0) {
						tempB = new FileBucket(cached, true, false, false, false, false);
						System.out.println("Fetching block for FreenetArchiver from disk cache: "+furi);
					}
				}
				
				if(tempB == null) {
					
					System.out.println("Fetching block for FreenetArchiver from network: "+furi);
					
					if (progress != null) {
						hlsc.addEventHook(new SimpleProgressUpdater(progress));
					}
					
					// code for async fetch - maybe be useful elsewhere
					//ClientContext cctx = core.clientContext;
					//FetchContext fctx = hlsc.getFetchContext();
					//FetchWaiter fw = new FetchWaiter();
					//ClientGetter gu = hlsc.fetch(furi, false, null, false, fctx, fw);
					//gu.setPriorityClass(RequestStarter.INTERACTIVE_PRIORITY_CLASS, cctx, null);
					//FetchResult res = fw.waitForCompletion();
					
					FetchResult res;
					
					// bookkeeping. detects bugs in the SplitfileProgressEvent handler
					if (progress != null) {
						ProgressParts prog_old = progress.getParts();
						res = hlsc.fetch(furi);
						ProgressParts prog_new = progress.getParts();
						if (prog_old.known - prog_old.done != prog_new.known - prog_new.done) {
							Logger.error(this, "Inconsistency when tracking split file progress (pulling): "+prog_old.known+" of "+prog_old.done+" -> "+prog_new.known+" of "+prog_new.done);
							System.err.println("Inconsistency when tracking split file progress (pulling): "+prog_old.known+" of "+prog_old.done+" -> "+prog_new.known+" of "+prog_new.done);
						}
						progress.addPartKnown(0, true);
					} else {
						res = hlsc.fetch(furi);
					}
					
					tempB = res.asBucket();
				} else {
					// Make sure SimpleProgress.join() doesn't stall.
					if(progress != null) {
						progress.addPartKnown(1, true);
						progress.addPartDone();
					}
				}
				long endTime = System.currentTimeMillis();
				System.out.println("Fetched block for FreenetArchiver in "+(endTime-startTime)+"ms.");
				is = tempB.getInputStream();
				task.data = (T)reader.readObject(is);
				is.close();

			} catch (FetchException e) {
				throw new TaskAbortException("Failed to fetch content", e, true);

			} catch (IOException e) {
				throw new TaskAbortException("Failed to read content from local tempbucket", e, true);

			} catch (RuntimeException e) {
				throw new TaskAbortException("Failed to complete task: ", e);

			}
		} catch (TaskAbortException e) {
			if (progress != null) { progress.abort(e); }
			throw e;

		} finally {
			Closer.close(is);
			Closer.close(tempB);
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation produces metdata of type {@link FreenetURI}.
	**
	** If the input metadata is an insert URI (SSK or USK), it will be replaced
	** by its corresponding request URI. Otherwise, the data will be inserted
	** as a CHK. Note that since {@link FreenetURI} is immutable, the {@link
	** FreenetURI#suggestedEdition} of a USK is '''not''' automatically
	** incremented.
	*/
	/*@Override**/ public void pushLive(PushTask<T> task, final SimpleProgress progress) throws TaskAbortException {
		HighLevelSimpleClient hlsc = core.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS);
		Bucket tempB = null; OutputStream os = null;

		try {
			ClientPutter putter = null;
			try {
				tempB = core.tempBucketFactory.makeBucket(expected_bytes, 2);
				os = tempB.getOutputStream();
				writer.writeObject(task.data, os);
				os.close(); os = null;
				tempB.setReadOnly();

				FreenetURI target = (task.meta instanceof FreenetURI)? (FreenetURI)task.meta: FreenetURI.EMPTY_CHK_URI;
				InsertBlock ib = new InsertBlock(tempB, new ClientMetadata(default_mime), target);

				System.out.println("Inserting block for FreenetArchiver...");
				long startTime = System.currentTimeMillis();
				
				// code for async insert - maybe be useful elsewhere
				//ClientContext cctx = core.clientContext;
				//InsertContext ictx = hlsc.getInsertContext(true);
				//PutWaiter pw = new PutWaiter();
				//ClientPutter pu = hlsc.insert(ib, false, null, false, ictx, pw);
				//pu.setPriorityClass(RequestStarter.INTERACTIVE_PRIORITY_CLASS, cctx, null);
				//FreenetURI uri = pw.waitForCompletion();

				FreenetURI uri;

				// bookkeeping. detects bugs in the SplitfileProgressEvent handler
				if (progress != null) {
					ProgressParts prog_old = progress.getParts();
					
					if(!SEMI_ASYNC_PUSH) {
						// Actually report progress.
						if (progress != null) {
							hlsc.addEventHook(new SimpleProgressUpdater(progress));
						}
						uri = hlsc.insert(ib, false, null);
						progress.addPartKnown(0, true);
					} else {
						// Do NOT report progress. Pretend we are done as soon as
						// we have the URI. This allows us to minimise memory usage
						// without yet splitting up IterableSerialiser.push() and
						// doing it properly. FIXME
						InsertContext ctx = hlsc.getInsertContext(false);
						PushCallback cb = new PushCallback(progress, ib);
						putter = new ClientPutter(cb, ib.getData(), FreenetURI.EMPTY_CHK_URI, ib.clientMetadata,
								ctx, RequestStarter.INTERACTIVE_PRIORITY_CLASS,
								false, false, this, null, null, false);
						cb.setPutter(putter);
						long tStart = System.currentTimeMillis();
						try {
							// Early encode is normally a security risk.
							// Hopefully it isn't here.
							core.clientContext.start(putter, true);
						} catch (DatabaseDisabledException e) {
							// Impossible
						}
						uri = cb.waitForURI();
						System.out.println("Got URI for asynchronous insert: "+uri+" size "+tempB.size()+" in "+(System.currentTimeMillis() - cb.startTime));
						progress.addPartKnown(0, true);
					}
					
					ProgressParts prog_new = progress.getParts();
					if (prog_old.known - prog_old.done != prog_new.known - prog_new.done) {
						Logger.error(this, "Inconsistency when tracking split file progress (pushing): "+prog_old.known+" of "+prog_old.done+" -> "+prog_new.known+" of "+prog_new.done);
						System.err.println("Inconsistency when tracking split file progress (pushing): "+prog_old.known+" of "+prog_old.done+" -> "+prog_new.known+" of "+prog_new.done);
					}
				} else {
					uri = hlsc.insert(ib, false, null);
					long endTime = System.currentTimeMillis();
					System.out.println("Inserted block for FreenetArchiver in "+(endTime-startTime)+"ms to "+uri);
				}

				task.meta = uri;
				task.data = null;
				
				if(cacheDir != null && cacheDir.exists() && cacheDir.canRead()) {
					File cached = new File(cacheDir, uri.toASCIIString());
					Bucket cachedBucket = new FileBucket(cached, false, false, false, false, false);
					BucketTools.copy(tempB, cachedBucket);
				}
				
				if(SEMI_ASYNC_PUSH)
					tempB = null; // Don't free it here.

			} catch (InsertException e) {
				if(putter != null) {
					synchronized(this) {
						semiAsyncPushes.remove(putter);
					}
				}
				throw new TaskAbortException("Failed to insert content", e, true);

			} catch (IOException e) {
				throw new TaskAbortException("Failed to write content to local tempbucket", e, true);

			} catch (RuntimeException e) {
				throw new TaskAbortException("Failed to complete task: ", e);

			}
		} catch (TaskAbortException e) {
			if (progress != null) { progress.abort(e); }
			throw e;

		} finally {
			Closer.close(os);
			Closer.close(tempB);
		}
	}
	
	public class PushCallback implements ClientPutCallback {

		public final long startTime = System.currentTimeMillis();
		private ClientPutter putter;
		private FreenetURI generatedURI;
		private InsertException failed;
		// See FIXME's in push(), IterableSerialiser.
		// We don't do real progress, we pretend we're done when push() returns.
//		private final SimpleProgress progress;
		private final InsertBlock ib;
		
		public PushCallback(SimpleProgress progress, InsertBlock ib) {
//			this.progress = progress;
			this.ib = ib;
		}

		public synchronized void setPutter(ClientPutter put) {
			putter = put;
			synchronized(FreenetArchiver.this) {
				semiAsyncPushes.add(this);
			}
		}

		public synchronized FreenetURI waitForURI() throws InsertException {
			while(generatedURI == null && failed == null) {
				try {
					wait();
				} catch (InterruptedException e) {
					// Ignore
				}
			}
			if(failed != null) throw failed;
			return generatedURI;
		}

		public void onFailure(InsertException e, BaseClientPutter state, ObjectContainer container) {
			System.out.println("Failed background insert ("+generatedURI+"), now running: "+semiAsyncPushes.size()+".");
			synchronized(this) {
				failed = e;
				notifyAll();
			}
			synchronized(FreenetArchiver.this) {
				semiAsyncPushes.remove(this);
				pushesFailed.add(e);
				FreenetArchiver.this.notifyAll();
			}
			if(ib != null)
				ib.free(null);
		}

		public void onFetchable(BaseClientPutter state, ObjectContainer container) {
			// Ignore
		}

		public synchronized void onGeneratedURI(FreenetURI uri, BaseClientPutter state, ObjectContainer container) {
			generatedURI = uri;
			notifyAll();
		}

		public void onSuccess(BaseClientPutter state, ObjectContainer container) {
			synchronized(FreenetArchiver.this) {
				semiAsyncPushes.remove(this);
				System.out.println("Completed background insert ("+generatedURI+") in "+(System.currentTimeMillis()-startTime)+"ms, now running: "+semiAsyncPushes.size()+".");
				FreenetArchiver.this.notifyAll();
			}
			if(ib != null)
				ib.free(null);
//			progress.addPartKnown(0, true);

		}

		public void onMajorProgress(ObjectContainer container) {
			// Ignore
		}

	}



	/*@Override**/ public void pull(PullTask<T> task) throws TaskAbortException {
		pullLive(task, null);
	}

	/*@Override**/ public void push(PushTask<T> task) throws TaskAbortException {
		pushLive(task, null);
	}

	public static class SimpleProgressUpdater implements ClientEventListener {

		final int[] splitfile_blocks = new int[2];
		final SimpleProgress progress;

		public SimpleProgressUpdater(SimpleProgress prog) {
			progress = prog;
		}

		/*@Override**/ public void onRemoveEventProducer(ObjectContainer container) { }

		/*@Override**/ public void receive(ClientEvent ce, ObjectContainer maybeContainer, ClientContext context) {
			progress.setStatus(ce.getDescription());
			if (!(ce instanceof SplitfileProgressEvent)) { return; }

			// update the progress "parts" counters
			SplitfileProgressEvent evt = (SplitfileProgressEvent)ce;
			int new_succeeded = evt.succeedBlocks;
			int new_total = evt.minSuccessfulBlocks;
			// fetch can go over 100%
			if (new_succeeded > new_total) {
				Logger.normal(this, "Received SplitfileProgressEvent greater than 100%: " + evt.getDescription());
				new_succeeded = new_total;
			}
			synchronized (splitfile_blocks) {
				int old_succeeded = splitfile_blocks[0];
				int old_total = splitfile_blocks[1];
				try {
					progress.addPartKnown(new_total - old_total, evt.finalizedTotal); // throws IllegalArgumentException
					int n = new_succeeded - old_succeeded;
					if (n == 1) {
						progress.addPartDone();
					} else if (n != 0) {
						Logger.normal(this, "Received SplitfileProgressEvent out-of-order: " + evt.getDescription());
						for (int i=0; i<n; ++i) {
							progress.addPartDone();
						}
					}
				} catch (IllegalArgumentException e) {
					Logger.normal(this, "Received SplitfileProgressEvent out-of-order: " + evt.getDescription(), e);
				}
				splitfile_blocks[0] = new_succeeded;
				splitfile_blocks[1] = new_total;
			}
		}
	}

	public void waitForAsyncInserts() throws TaskAbortException {
		synchronized(this) {
			while(true) {
				if(!pushesFailed.isEmpty()) {
					throw new TaskAbortException("Failed to insert content", pushesFailed.remove(0), true);
				}
				if(semiAsyncPushes.isEmpty()) {
					System.out.println("Asynchronous inserts completed.");
					return; // Completed all pushes.
				}
				System.out.println("Waiting for "+semiAsyncPushes.size()+" asynchronous inserts...");
				try {
					wait();
				} catch (InterruptedException e) {
					// Ignore
				}
			}
		}
	}
	

	public boolean persistent() {
		return false;
	}

	public void removeFrom(ObjectContainer container) {
		throw new UnsupportedOperationException();
	}

}
