/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;


import plugins.Library.Library;

import freenet.client.ClientMetadata;
import freenet.client.FetchException;
import freenet.client.FetchException.FetchExceptionMode;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertBlock;
import freenet.client.InsertContext;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientPutCallback;
import freenet.client.async.ClientPutter;
import freenet.client.async.PersistenceDisabledException;
import freenet.client.events.ClientEvent;
import freenet.client.events.ClientEventListener;
import freenet.client.events.SplitfileProgressEvent;
import freenet.crypt.SHA256;
import freenet.keys.CHKBlock;
import freenet.keys.FreenetURI;
import freenet.library.io.ObjectStreamReader;
import freenet.library.io.ObjectStreamWriter;
import freenet.library.io.serial.LiveArchiver;
import freenet.library.util.exec.ProgressParts;
import freenet.library.util.exec.SimpleProgress;
import freenet.library.util.exec.TaskAbortException;
import freenet.node.NodeClientCore;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.support.Base64;
import freenet.support.Logger;
import freenet.support.SimpleReadOnlyArrayBucket;
import freenet.support.SizeUtil;
import freenet.support.api.Bucket;
import freenet.support.api.RandomAccessBucket;
import freenet.support.io.BucketTools;
import freenet.support.io.Closer;
import freenet.support.io.FileBucket;
import freenet.support.io.ResumeFailedException;

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
implements LiveArchiver<T, SimpleProgress> {

	final protected NodeClientCore core;
	final protected ObjectStreamReader reader;
	final protected ObjectStreamWriter writer;

	final protected String default_mime;
	final protected int expected_bytes;
	public final short priorityClass;
	public final boolean realTimeFlag;
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
	private long totalBytesPushing;
	
	public static void setCacheDir(File dir) {
		cacheDir = dir;
	}
	
	public static File getCacheDir() {
		return cacheDir;
	}

	public FreenetArchiver(NodeClientCore c, ObjectStreamReader r, ObjectStreamWriter w, String mime, int size, short priority) {
		if (c == null) {
			throw new IllegalArgumentException("Can't create a FreenetArchiver with a null NodeClientCore!");
		}
		this.priorityClass = priority;
		// FIXME pass it in somehow
		if(priorityClass <= RequestStarter.IMMEDIATE_SPLITFILE_PRIORITY_CLASS)
			realTimeFlag = true;
		else
			realTimeFlag = false;
		core = c;
		reader = r;
		writer = w;
		default_mime = mime;
		expected_bytes = size;
	}
	
	public <S extends ObjectStreamWriter & ObjectStreamReader> FreenetArchiver(NodeClientCore c, S rw, String mime, int size, short priority) {
		this(c, rw, rw, mime, size, priority);
	}

	/**
	** {@inheritDoc}
	**
	** This implementation expects metdata of type {@link FreenetURI}.
	*
	* FIXME OPT NORM: Two-phase pull: First pull to a bucket, then construct the data.
	* The reason for this is memory usage: We can limit the number in the second
	* phase according to downstream demand (e.g. blocking queues in ObjectProcessor's),
	* so that the amount of stuff kept in memory is limited, while still allowing an
	* (almost?) unlimited number in the fetching data phase. See comments in ParallelSerialiser.createPullJob.
	*/
	/*@Override**/ public void pullLive(PullTask<T> task, final SimpleProgress progress) throws TaskAbortException {
		// FIXME make retry count configgable by client metadata somehow
		// clearly a web UI fetch wants it limited; a merge might want it unlimited
		HighLevelSimpleClient hlsc = core.makeClient(priorityClass, false, false);
		Bucket tempB = null; InputStream is = null;

		long startTime = System.currentTimeMillis();
		
		FreenetURI u;
		byte[] initialMetadata;
		String cacheKey;
		
		if(task.meta instanceof String) {
			try {
				task.meta = new FreenetURI((String) task.meta);
			} catch (MalformedURLException e) {
				// This wasn't an URI after all.
			}
		}

		if (task.meta instanceof FreenetURI) {
			u = (FreenetURI) task.meta;
			initialMetadata = null;
			cacheKey = u.toString(false, true);
		} else {
			initialMetadata = (byte[]) task.meta;
			u = FreenetURI.EMPTY_CHK_URI;
			cacheKey = Base64.encode(SHA256.digest(initialMetadata));
		}
		
		for(int i=0;i<10;i++) {
		// USK redirects should not happen really but can occasionally due to race conditions.
		
		try {
			try {

				if(cacheDir != null && cacheDir.exists() && cacheDir.canRead()) {
					File cached = new File(cacheDir, cacheKey);
					if(cached.exists() && cached.length() != 0) {
						tempB = new FileBucket(cached, true, false, false, false);
						System.out.println("Fetching block for FreenetArchiver from disk cache: "+cacheKey);
					}
				}
				
				if(tempB == null) {
					
					if(initialMetadata != null)
						System.out.println("Fetching block for FreenetArchiver from metadata ("+cacheKey+")");
					else
						System.out.println("Fetching block for FreenetArchiver from network: "+u);
					
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
					ProgressParts prog_old = null;
					if (progress != null) {
						prog_old = progress.getParts();
					}

					if(initialMetadata != null)
						res = hlsc.fetchFromMetadata(new SimpleReadOnlyArrayBucket(initialMetadata));
					else
						res = hlsc.fetch(u);

					ProgressParts prog_new;
					if (progress != null) {
						prog_new = progress.getParts();
						if (prog_old.known - prog_old.done != prog_new.known - prog_new.done) {
							Logger.error(this, "Inconsistency when tracking split file progress (pulling): "+prog_old.known+" of "+prog_old.done+" -> "+prog_new.known+" of "+prog_new.done);
							System.err.println("Inconsistency when tracking split file progress (pulling): "+prog_old.known+" of "+prog_old.done+" -> "+prog_new.known+" of "+prog_new.done);
						}
						progress.addPartKnown(0, true);
					}
					
					tempB = res.asBucket();
				} else {
					// Make sure SimpleProgress.join() doesn't stall.
					if(progress != null) {
						progress.addPartKnown(1, true);
						progress.addPartDone();
					}
				}
				is = tempB.getInputStream();
				task.data = (T)reader.readObject(is);
				is.close();
				long endTime = System.currentTimeMillis();
				System.out.println("Fetched block for FreenetArchiver in "+(endTime-startTime)+"ms.");

			} catch (FetchException e) {
				if(e.mode == FetchExceptionMode.PERMANENT_REDIRECT && e.newURI != null) {
					u = e.newURI;
					continue;
				}
				System.out.println("FetchException:");
				e.printStackTrace();
				throw new TaskAbortException("Failed to fetch content", e, true);

			} catch (IOException e) {
				System.out.println("IOException:");
				e.printStackTrace();
				throw new TaskAbortException("Failed to read content from local tempbucket", e, true);

			} catch (RuntimeException e) {
				System.out.println("RuntimeException:");
				e.printStackTrace();
				throw new TaskAbortException("Failed to complete task: ", e);

			}
		} catch (TaskAbortException e) {
			if (progress != null) { progress.abort(e); }
			throw e;

		} finally {
			Closer.close(is);
			Closer.close(tempB);
		}
		break;
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
		HighLevelSimpleClient hlsc = core.makeClient(priorityClass, false, false);
		RandomAccessBucket tempB = null; OutputStream os = null;

		
		try {
			ClientPutter putter = null;
			PushCallback cb = null;
			try {
				tempB = core.tempBucketFactory.makeBucket(expected_bytes, 2);
				os = tempB.getOutputStream();
				writer.writeObject(task.data, os);
				os.close(); os = null;
				tempB.setReadOnly();

				boolean insertAsMetadata;
				FreenetURI target;
				
				if(task.meta instanceof FreenetURI) {
					insertAsMetadata = false;
					target = (FreenetURI) task.meta;
				} else {
					insertAsMetadata = true;
					target = FreenetURI.EMPTY_CHK_URI;
				}
				InsertBlock ib = new InsertBlock(tempB, new ClientMetadata(default_mime), target);

				long startTime = System.currentTimeMillis();
				
				// bookkeeping. detects bugs in the SplitfileProgressEvent handler
				ProgressParts prog_old = null;
				if(progress != null)
					prog_old = progress.getParts();
					
				// FIXME make retry count configgable by client metadata somehow
				// unlimited for push/merge
				InsertContext ctx = hlsc.getInsertContext(false);
				ctx.maxInsertRetries = -1;
                // Early encode is normally a security risk.
                // Hopefully it isn't here.
				ctx.earlyEncode = true;
				
				String cacheKey = null;
				
//				if(!SEMI_ASYNC_PUSH) {
//					// Actually report progress.
//					if (progress != null) {
//						hlsc.addEventHook(new SimpleProgressUpdater(progress));
//					}
//					uri = hlsc.insert(ib, false, null, priorityClass, ctx);
//					if (progress != null)
//						progress.addPartKnown(0, true);
//				} else {
					// Do NOT report progress. Pretend we are done as soon as
					// we have the URI. This allows us to minimise memory usage
					// without yet splitting up IterableSerialiser.push() and
					// doing it properly. FIXME
					if(progress != null)
						progress.addPartKnown(1, true);
					cb = new PushCallback(progress, ib);
					putter = new ClientPutter(cb, ib.getData(), FreenetURI.EMPTY_CHK_URI, ib.clientMetadata,
							ctx, priorityClass,
							false, null, false, core.clientContext, null, insertAsMetadata ? CHKBlock.DATA_LENGTH : -1);
					cb.setPutter(putter);
					long tStart = System.currentTimeMillis();
					try {
						core.clientContext.start(putter);
					} catch (PersistenceDisabledException e) {
						// Impossible
					}
					WAIT_STATUS status = cb.waitFor();
					if(status == WAIT_STATUS.FAILED) {
						cb.throwError();
					} else if(status == WAIT_STATUS.GENERATED_URI) {
						FreenetURI uri = cb.getURI();
						task.meta = uri;
						cacheKey = uri.toString(false, true);
						System.out.println("Got URI for asynchronous insert: "+uri+" size "+tempB.size()+" in "+(System.currentTimeMillis() - cb.startTime));
					} else {
						Bucket data = cb.getGeneratedMetadata();
						byte[] buf = BucketTools.toByteArray(data);
						data.free();
						task.meta = buf;
						cacheKey = Base64.encode(SHA256.digest(buf));
						System.out.println("Got generated metadata ("+buf.length+" bytes) for asynchronous insert size "+tempB.size()+" in "+(System.currentTimeMillis() - cb.startTime));
					}
					if(progress != null)
						progress.addPartDone();
//				}
					
				if(progress != null) {
					ProgressParts prog_new = progress.getParts();
					if (prog_old.known - prog_old.done != prog_new.known - prog_new.done) {
						Logger.error(this, "Inconsistency when tracking split file progress (pushing): "+prog_old.known+" of "+prog_old.done+" -> "+prog_new.known+" of "+prog_new.done);
						System.err.println("Inconsistency when tracking split file progress (pushing): "+prog_old.known+" of "+prog_old.done+" -> "+prog_new.known+" of "+prog_new.done);
					}
				}

				task.data = null;
				
				if(cacheKey != null && cacheDir != null && cacheDir.exists() && cacheDir.canRead()) {
					File cached = new File(cacheDir, cacheKey);
					Bucket cachedBucket = new FileBucket(cached, false, false, false, false);
					BucketTools.copy(tempB, cachedBucket);
				}
				
				if(SEMI_ASYNC_PUSH)
					tempB = null; // Don't free it here.

			} catch (InsertException e) {
				if(cb != null) {
					synchronized(this) {
						if(semiAsyncPushes.remove(cb))
							totalBytesPushing -= cb.size();
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
	
	enum WAIT_STATUS {
		FAILED,
		GENERATED_URI,
		GENERATED_METADATA;
	}
	
	public class PushCallback implements ClientPutCallback {

		public final long startTime = System.currentTimeMillis();
		private ClientPutter putter;
		private FreenetURI generatedURI;
		private Bucket generatedMetadata;
		private InsertException failed;
		// See FIXME's in push(), IterableSerialiser.
		// We don't do real progress, we pretend we're done when push() returns.
//		private final SimpleProgress progress;
		private final long size;
		private final InsertBlock ib;
		
		public PushCallback(SimpleProgress progress, InsertBlock ib) {
//			this.progress = progress;
			this.ib = ib;
			size = ib.getData().size();
		}

		public long size() {
			return size;
		}

		public synchronized void setPutter(ClientPutter put) {
			putter = put;
			synchronized(FreenetArchiver.this) {
				if(semiAsyncPushes.add(this))
					totalBytesPushing += size;
				System.out.println("Added insert of " + size + " bytes, now pushing: " + 
						   semiAsyncPushes.size() +
						   " (" + SizeUtil.formatSize(totalBytesPushing) + ").");
			}
		}

		public synchronized WAIT_STATUS waitFor() {
			while(generatedURI == null && generatedMetadata == null && failed == null) {
				try {
					wait();
				} catch (InterruptedException e) {
					// Ignore
				}
			}
			if(failed != null) return WAIT_STATUS.FAILED;
			if(generatedURI != null) return WAIT_STATUS.GENERATED_URI;
			return WAIT_STATUS.GENERATED_METADATA;
		}
		
		public synchronized void throwError() throws InsertException {
			if(failed != null) throw failed;
		}
		
		public synchronized FreenetURI getURI() {
			return generatedURI;
		}
		
		public synchronized Bucket getGeneratedMetadata() {
			return generatedMetadata;
		}

		@Override
		public void onFailure(InsertException e, BaseClientPutter state) {
			System.out.println("Failed background insert (" + generatedURI + "), now pushing: " +
					   semiAsyncPushes.size() +
					   " (" + SizeUtil.formatSize(totalBytesPushing) + ").");
			synchronized(this) {
				failed = e;
				notifyAll();
			}
			synchronized(FreenetArchiver.this) {
				if(semiAsyncPushes.remove(this))
					totalBytesPushing -= size;
				pushesFailed.add(e);
				FreenetArchiver.this.notifyAll();
			}
			if(ib != null)
				ib.free();
		}

		@Override
		public void onFetchable(BaseClientPutter state) {
			// Ignore
		}

		@Override
		public synchronized void onGeneratedURI(FreenetURI uri, BaseClientPutter state) {
			generatedURI = uri;
			notifyAll();
		}

		@Override
		public void onSuccess(BaseClientPutter state) {
			synchronized(FreenetArchiver.this) {
				if(semiAsyncPushes.remove(this))
					totalBytesPushing -= size;
				System.out.println("Completed background insert (" + generatedURI + ") in " +
						   (System.currentTimeMillis()-startTime) + "ms, now pushing: " +
						   semiAsyncPushes.size() + 
						   " (" + SizeUtil.formatSize(totalBytesPushing) + ").");
				FreenetArchiver.this.notifyAll();
			}
			if(ib != null)
				ib.free();

		}

		@Override
		public synchronized void onGeneratedMetadata(Bucket metadata,
				BaseClientPutter state) {
			generatedMetadata = metadata;
			notifyAll();
		}

        @Override
        public void onResume(ClientContext context) throws ResumeFailedException {
            // Ignore.
        }

        @Override
        public RequestClient getRequestClient() {
            return Library.REQUEST_CLIENT;
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

		@Override public void receive(ClientEvent ce, ClientContext context) {
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

				try {
					wait();
				} catch (InterruptedException e) {
					// Ignore
				}
			}
		}
	}
	
}
