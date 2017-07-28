/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;

import freenet.client.FetchException;
import freenet.client.FetchException.FetchExceptionMode;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertException;
import freenet.client.async.ClientContext;
import freenet.client.events.ClientEvent;
import freenet.client.events.ClientEventListener;
import freenet.client.events.SplitfileProgressEvent;
import freenet.crypt.SHA256;
import freenet.library.io.FreenetURI;
import freenet.library.io.ObjectStreamReader;
import freenet.library.io.ObjectStreamWriter;
import freenet.library.io.serial.LiveArchiver;
import freenet.library.util.exec.ProgressParts;
import freenet.library.util.exec.SimpleProgress;
import freenet.library.util.exec.TaskAbortException;
import freenet.node.NodeClientCore;
import freenet.node.RequestStarter;
import freenet.support.Base64;
import freenet.support.Logger;
import freenet.support.SimpleReadOnlyArrayBucket;
import freenet.support.api.Bucket;
import freenet.support.io.Closer;
import freenet.support.io.FileBucket;

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

	private freenet.keys.FreenetURI toFreenetURI(FreenetURI u) {
		try {
			return new freenet.keys.FreenetURI(u.toString());
		} catch (MalformedURLException e) {
			Logger.error(this, "Failed to create URI", e);
			throw new RuntimeException("Failed to complete task: ", e);
		}
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
			cacheKey = u.toString();
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
						res = hlsc.fetch(toFreenetURI(u));

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
					try {
						u = new FreenetURI(e.newURI.toString());
						continue;
					} catch (MalformedURLException e1) {
						System.out.println("Cannot convert " + e.newURI + ".");						
					}
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


	enum WAIT_STATUS {
		FAILED,
		GENERATED_URI,
		GENERATED_METADATA;
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

	@Override
	public void pushLive(freenet.library.io.serial.Serialiser.PushTask<T> task,
			SimpleProgress p) throws TaskAbortException {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	public void waitForAsyncInserts() throws TaskAbortException {
		throw new RuntimeException("Not implemented.");
	}
}
