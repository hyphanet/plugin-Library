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
** This class expects the overrides of {@link #pull(Serialiser.PullTask)}
** and {@link #push(Serialiser.PushTask)} to notify all threads blocking on
** {@link Progress#join()}. If this does not occur, deadlock will result.
** TODO get rid of this requirement...
**
** PRIORITY rename to ParallelSerialiser or ParallelTracker or something...
**
** @author infinity0
*/
public abstract class ParallelArchiver<T>
implements IterableSerialiser<T>,
           Serialiser.Trackable<T> {

	protected int maxThreads = 4;
	protected int numThreads = 0;
	protected SynchronousQueue<Task<T>> queue = new SynchronousQueue<Task<T>>();

	final protected ProgressTracker<T, ? extends Progress> tracker;

	public ParallelArchiver(ProgressTracker<T, ? extends Progress> k) {
		if (k == null) {
			throw new IllegalArgumentException("ParallelArchiver must have a progress tracker.");
		}
		tracker = k;
	}

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
			List<Progress> plist = new ArrayList<Progress>();
			Iterator<PullTask<T>> it = tasks.iterator();
			while (it.hasNext()) {
				PullTask<T> t = it.next();
				if (t.meta == null) {
					throw new IllegalArgumentException("ParallelArchiver cannot handle pull tasks with null metadata");
				}

				Progress p = tracker.addPullProgress(t.meta);
				if (p == null) {
					// if we are already pushing this then skip it
					it.remove();
					continue;
				}
				plist.add(p);

				while (!queue.offer(t, 1, TimeUnit.SECONDS)) {
					startHandler();
				}
				//System.out.println("pushed " + t);
			}
			for (Progress p: plist) { p.join(); }
		} catch (InterruptedException e) {
			throw new TaskFailException("ParallelArchiver pull was interrupted", e);
		} finally {
			for (PullTask<T> t: tasks) { tracker.remPullProgress(t.data); }
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
			List<Progress> plist = new ArrayList<Progress>();
			Iterator<PushTask<T>> it = tasks.iterator();
			while (it.hasNext()) {
				PushTask<T> t = it.next();
				if (t.data == null) {
					throw new IllegalArgumentException("ParallelArchiver cannot handle pull tasks with null metadata");
				}

				Progress p = tracker.addPushProgress(t.data);
				if (p == null) {
					// if we are already pushing this then skip it
					it.remove();
					continue;
				}
				plist.add(p);

				while (!queue.offer(t, 1, TimeUnit.SECONDS)) {
					startHandler();
				}
				//System.out.println("pushed " + t);
			}
			for (Progress p: plist) { p.join(); }
		} catch (InterruptedException e) {
			throw new TaskFailException("ParallelArchiver push was interrupted", e);
		} finally {
			for (PushTask<T> t: tasks) { tracker.remPushProgress(t.data); }
		}
	}

	protected class QueueHandler extends Thread {
		public void run() {
			//System.out.println(Thread.currentThread() + " started");
			try {
				Task<T> t;
				while ((t = queue.poll(1, TimeUnit.SECONDS)) != null) {
					//System.out.println(Thread.currentThread() + " popped " + t);
					if (t instanceof PullTask) {
						pull((PullTask<T>)t);
					} else {
						push((PushTask<T>)t);
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
			//System.out.println(Thread.currentThread() + " ended");
		}
	}

}
