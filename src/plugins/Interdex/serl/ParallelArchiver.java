/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.serl.Serialiser.*;

import java.util.Map;
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
** An {@link IterableSerialiser} that uses threads to handle tasks given to it
** in parallel.
**
** It also keeps track of each task's progress and provides methods to retrieve
** this data. For this to function properly, the data/metadata for push/pull
** tasks (respectively) MUST NOT be internally modified by the child {@link
** Archiver}, since these are used as keys into {@link IdentityHashMap}s.
**
** URGENT atm has deadlock problems...
**
** @author infinity0
*/
public class ParallelArchiver<T, I> extends CompositeArchiver<T, I> implements IterableSerialiser<T> {

	protected int maxThreads = 4;
	protected int numThreads = 0;
	protected SynchronousQueue<Task<T>> queue = new SynchronousQueue<Task<T>>();

	/**
	** Keeps track of the progress of each {@link PullTask}. The key is the
	** metadata of the task.
	*/
	protected IdentityHashMap<Object, Progress> pullProgress = new IdentityHashMap<Object, Progress>();

	/**
	** Keeps track of the progress of each {@link PushTask}. The key is the
	** data of the task.
	*/
	protected IdentityHashMap<T, Progress> pushProgress = new IdentityHashMap<T, Progress>();

	public ParallelArchiver(Archiver<I> s, Translator<T, I> t) {
		super(s, t);
	}

	public ParallelArchiver(Archiver<I> s) {
		super(s, null);
	}

	/**
	** Set the maximum number of parallel {@link Thread}s that this serialiser
	** will automatically start.
	*/
	public synchronized void setMaxThreads(int n) {
		if (n < 1) {
			throw new IllegalArgumentException("Must be able to have at least one thread!");
		}
		maxThreads = n;
	}

	/**
	** This method will start a new thread if {@link #maxThreads} allows.
	*/
	protected synchronized void startHandler() {
		if (numThreads >= maxThreads) { return; }
		++numThreads;
		(new QueueHandler()).start();
	}

	/**
	** This method will start a new thread if there are no threads currently
	** running. When called at the beginning of a task series, it ensures that
	** there is at least one thread available to receive a task.
	*/
	protected synchronized void kickStart() {
		if (numThreads != 0) { return; }
		++numThreads;
		(new QueueHandler()).start();
	}

	public Progress getPullProgress(Object meta) {
		synchronized (pullProgress) {
			return pullProgress.get(meta);
		}
	}

	public Progress getPushProgress(T data) {
		synchronized (pushProgress) {
			return pushProgress.get(data);
		}
	}

	/*========================================================================
	  public interface IterableSerialiser
	 ========================================================================*/

	/**
	** {@inheritDoc}
	**
	** This implementation DOCUMENT
	*/
	@Override public void pull(Iterable<PullTask<T>> tasks) {
		kickStart();
		try {
			Iterator<PullTask<T>> it = tasks.iterator();
			while (it.hasNext()) {
				PullTask<T> t = it.next();
				synchronized (pullProgress) {
					// if we are already pushing this then skip it
					if (pullProgress.containsKey(t.meta)) {
						it.remove();
						continue;
					}
					// TODO use mikeb's Progress class
					pullProgress.put(t.meta, new Progress());
				}
				if (!queue.offer(t, 1, TimeUnit.SECONDS)) {
					startHandler();
				}
			}
			for (PullTask<T> t: tasks) {
				getPullProgress(t.meta).join();
			}
		} catch (InterruptedException e) {
			throw new TaskFailException("ParallelArchiver pull was interrupted", e);
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation DOCUMENT
	*/
	@Override public void push(Iterable<PushTask<T>> tasks) {
		kickStart();
		try {
			Iterator<PushTask<T>> it = tasks.iterator();
			while (it.hasNext()) {
				PushTask<T> t = it.next();
				synchronized (pushProgress) {
					// if we are already pushing this then skip it
					if (pushProgress.containsKey(t.data)) {
						it.remove();
						continue;
					}
					// TODO use mikeb's Progress class
					pushProgress.put(t.data, new Progress());
				}
				if (!queue.offer(t, 1, TimeUnit.SECONDS)) {
					startHandler();
				}
			}
			for (PushTask<T> t: tasks) {
				getPushProgress(t.data).join();
			}
		} catch (InterruptedException e) {
			throw new TaskFailException("ParallelArchiver push was interrupted", e);
		}
	}


	// TODO use mikeb's Progress class instead...
	public static class Progress {

		private boolean done;

		public String getProgress() {
			return "ongoing";
		}

		public synchronized void done() {
			done = true;
			notifyAll();
		}

		public synchronized void join() throws InterruptedException {
			while (!done) { wait(); }
		}

	}

	protected class QueueHandler extends Thread {
		public void run() {
			try {
				Task<T> t;
				while ((t = queue.poll(1, TimeUnit.SECONDS)) != null) {
					if (t instanceof PullTask) {
						Progress p = getPullProgress(t.meta);
						pull((PullTask<T>)t);
						p.done();
					} else {
						Progress p = getPushProgress(t.data);
						push((PushTask<T>)t);
						p.done();
					}
				}
			} catch (InterruptedException e) {
				// TODO find some way to pass this exception onto the method which put the
				// task onto the queue, instead of just throwing it...
				throw new TaskFailException("ParallelArchiver was interrupted", e);
			} finally {
				synchronized (ParallelArchiver.this) {
					--numThreads;
				}
			}
		}
	}

}
