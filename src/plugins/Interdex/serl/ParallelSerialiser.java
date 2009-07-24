/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.serl.Serialiser.*;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
** An {@link IterableSerialiser} that uses threads to handle tasks given to it
** in parallel.
**
** TODO making this ParallelSerialiser<T, P extends Progress> will probably make
** things nicer...
**
** @author infinity0
*/
public abstract class ParallelSerialiser<T, P extends Progress>
implements IterableSerialiser<T>,
           Serialiser.Trackable<T> {

	protected int maxThreads = 0x10;
	protected int numThreads = 0;
	protected SynchronousQueue<Task<T>> queue = new SynchronousQueue<Task<T>>();

	final protected ProgressTracker<T, P> tracker;

	public ParallelSerialiser(ProgressTracker<T, P> k) {
		if (k == null) {
			throw new IllegalArgumentException("ParallelSerialiser must have a progress tracker.");
		}
		tracker = k;
	}

	// return ? so as to hide the implementation details of Progress
	@Override public ProgressTracker<T, ? extends Progress> getTracker() {
		return tracker;
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

	/**
	** Given a group of progresses, waits for them to all finish. Non-error
	** aborts are caught and ignored; error aborts are re-thrown.
	*/
	protected void joinAll(Iterable<P> plist) throws InterruptedException, TaskAbortException {
		Iterator<P> it = plist.iterator();
		while (it.hasNext()) {
			P p = it.next();
			try {
				p.join();
			} catch (TaskAbortException e) {
				if (e.isError()) {
					throw e;
				} else {
					// TODO perhaps have a handleNonErrorAbort() that can be overridden
					it.remove();
				}
			}
		}
	}

	/**
	** Executes a {@link PullTask} and update the progress associated with it.
	**
	** Implementations must also modify the state of the progress object such
	** that after the operation completes, all threads blocked on {@link
	** Progress#join()} will either return normally or throw the exact {@link
	** TaskAbortException} that caused it to abort.
	**
	** '''If this does not occur, deadlock will result'''.
	*/
	abstract public void pullAndUpdateProgress(PullTask<T> task, P p);

	/**
	** Executes a {@link PushTask} and update the progress associated with it.
	**
	** Implementations must also modify the state of the progress object such
	** that after the operation completes, all threads blocked on {@link
	** Progress#join()} will either return normally or throw the exact {@link
	** TaskAbortException} that caused it to abort.
	**
	** '''If this does not occur, deadlock will result'''.
	*/
	abstract public void pushAndUpdateProgress(PushTask<T> task, P p);

	/*========================================================================
	  public interface IterableSerialiser
	 ========================================================================*/

	@Override public void pull(PullTask<T> task) throws TaskAbortException {
		List<PullTask<T>> thetask = new ArrayList<PullTask<T>>(1);
		thetask.add(task);
		pull(thetask);
		if (thetask.isEmpty()) { throw new TaskCompleteException("Already done"); }
	}

	@Override public void push(PushTask<T> task) throws TaskAbortException {
		List<PushTask<T>> thetask = new ArrayList<PushTask<T>>(1);
		thetask.add(task);
		push(thetask);
		if (thetask.isEmpty()) { throw new TaskCompleteException("Already done"); }
	}

	/**
	** {@inheritDoc}
	**
	** This implementation DOCUMENT
	*/
	@Override public void pull(Iterable<PullTask<T>> tasks) throws TaskAbortException {
		kickStart();
		try {
			List<P> plist = new ArrayList<P>();
			Iterator<PullTask<T>> it = tasks.iterator();
			while (it.hasNext()) {
				PullTask<T> t = it.next();
				if (t.meta == null) {
					throw new IllegalArgumentException("ParallelSerialiser cannot handle pull tasks with null metadata");
				}

				P p = tracker.addPullProgress(t.meta);
				if (p == null) {
					// if we are already pushing this, then erase it from the task iterable
					// but we still want to wait for the task to finish, so add it to plist
					it.remove();
					plist.add(tracker.getPullProgress(t.meta));
					continue;
				}
				plist.add(p);

				while (!queue.offer(t, 1, TimeUnit.SECONDS)) {
					startHandler();
				}
			}
			// wait for all tasks to finish
			joinAll(plist);

		} catch (InterruptedException e) {
			throw new TaskAbortException("ParallelSerialiser pull was interrupted", e, true);
		} finally {
			// OPTIMISE make ProgressTracker use WeakIdentityHashMap instead
			// then we'll skip removal for completed (not aborted) tasks
			for (PullTask<T> t: tasks) { tracker.remPullProgress(t.data); }
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation DOCUMENT
	*/
	@Override public void push(Iterable<PushTask<T>> tasks) throws TaskAbortException {
		kickStart();
		try {
			List<P> plist = new ArrayList<P>();
			Iterator<PushTask<T>> it = tasks.iterator();
			while (it.hasNext()) {
				PushTask<T> t = it.next();
				if (t.data == null) {
					throw new IllegalArgumentException("ParallelSerialiser cannot handle pull tasks with null metadata");
				}

				P p = tracker.addPushProgress(t.data);
				if (p == null) {
					// if we are already pushing this, then erase it from the task iterable
					// but we still want to wait for the task to finish, so add it to plist
					it.remove();
					plist.add(tracker.getPushProgress(t.data));
					continue;
				}
				plist.add(p);

				while (!queue.offer(t, 1, TimeUnit.SECONDS)) {
					startHandler();
				}
			}
			// wait for all tasks to finish
			joinAll(plist);

		} catch (InterruptedException e) {
			throw new TaskAbortException("ParallelSerialiser push was interrupted", e, true);
		} finally {
			// OPTIMISE make ProgressTracker use WeakIdentityHashMap instead
			// then we'll skip removal for completed (not aborted) tasks
			for (PushTask<T> t: tasks) { tracker.remPushProgress(t.data); }
		}
	}

	protected class QueueHandler extends Thread {
		public void run() {
			//System.out.println(Thread.currentThread() + " started");
			try {
				Task<T> t;
				for (;;) {
					try {
						if ((t = queue.poll(1, TimeUnit.SECONDS)) == null) { break; }
					} catch (InterruptedException e) {
						continue; // can't interrupt this worker
					}
					//System.out.println(Thread.currentThread() + " popped " + t);
					if (t instanceof PullTask) {
						pullAndUpdateProgress((PullTask<T>)t, tracker.getPullProgress(t.meta));
					} else {
						pushAndUpdateProgress((PushTask<T>)t, tracker.getPushProgress(t.data));
					}
				}
			} finally {
				synchronized (ParallelSerialiser.this) {
					--numThreads;
				}
			}
			//System.out.println(Thread.currentThread() + " ended");
		}
	}

}
