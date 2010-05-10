/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.io.serial;

import plugins.Library.io.serial.Serialiser.*;
import plugins.Library.util.concurrent.Scheduler;
import plugins.Library.util.concurrent.ObjectProcessor;
import plugins.Library.util.concurrent.Executors;
import plugins.Library.util.exec.Progress;
import plugins.Library.util.exec.TaskAbortException;
import plugins.Library.util.exec.TaskInProgressException;
import plugins.Library.util.exec.TaskCompleteException;
import plugins.Library.util.func.SafeClosure;
import static plugins.Library.util.func.Tuples.X2; // also imports the class

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentMap;

/**
** An {@link IterableSerialiser} that uses threads to handle tasks given to it
** in parallel, and keeps track of task progress.
**
** To implement this class, the programmer must implement the {@link
** LiveArchiver#pullLive(Serialiser.PullTask, Progress)} and {@link
** LiveArchiver#pushLive(Serialiser.PushTask, Progress)} methods.
**
** DOCUMENT (rewritten)
**
** @author infinity0
*/
public abstract class ParallelSerialiser<T, P extends Progress>
implements IterableSerialiser<T>,
           ScheduledSerialiser<T>,
           LiveArchiver<T, P>,
           Serialiser.Trackable<T> {

	final static protected ThreadPoolExecutor exec = new ThreadPoolExecutor(
		0x40, 0x40, 1, TimeUnit.SECONDS,
		new LinkedBlockingQueue<Runnable>(),
		new ThreadPoolExecutor.CallerRunsPolicy() // easier than catching RejectedExecutionException, if it ever occurs
	);

	final protected ProgressTracker<T, P> tracker;

	public ParallelSerialiser(ProgressTracker<T, P> k) {
		if (k == null) {
			throw new IllegalArgumentException("ParallelSerialiser must have a progress tracker.");
		}
		tracker = k;
	}

	// return ? extends Progress so as to hide the implementation details of P
	/*@Override**/ public ProgressTracker<T, ? extends Progress> getTracker() {
		return tracker;
	}

	protected <K extends Task> Runnable createJoinRunnable(final K task, final TaskInProgressException e, final SafeClosure<X2<K, TaskAbortException>> post) {
		return new Runnable() {
			public void run() {
				TaskAbortException ex;
				try {
					Progress p = e.getProgress();
					p.join();
					ex = new TaskCompleteException("Task complete: " + p.getSubject());
				} catch (InterruptedException e) {
					ex = new TaskAbortException("Progress join was interrupted", e, true);
				} catch (RuntimeException e) {
					ex = new TaskAbortException("failed", e);
				} catch (TaskAbortException a) {
					ex = a;
				}
				if (post != null) { post.invoke(X2(task, ex)); }
			}
		};
	}

	/**
	** DOCUMENT.
	**
	** If {@code post} is {@code null}, it is assumed that {@link Progress}
	** objects are created by the thread that calls this method. Failure to do
	** this '''will result in deadlock'''.
	**
	** Otherwise, this method will create {@link Progress} objects for each
	** task. If they are created before this, '''deadlock will result''' as the
	** handler waits for that progress to complete.
	*/
	protected Runnable createPullJob(final PullTask<T> task, final SafeClosure<X2<PullTask<T>, TaskAbortException>> post) {
		try {
			final P prog = (post != null)? tracker.addPullProgress(task): tracker.getPullProgress(task);
			return new Runnable() {
				public void run() {
					TaskAbortException ex = null;
					try { pullLive(task, prog); }
					catch (RuntimeException e) { ex = new TaskAbortException("failed", e); }
					catch (TaskAbortException e) { ex = e; }
					if (post != null) { post.invoke(X2(task, ex)); }
				}
			};
		} catch (final TaskInProgressException e) {
			return createJoinRunnable(task, e, post);
		}
	}

	/**
	** DOCUMENT.
	**
	** If {@code post} is {@code null}, it is assumed that {@link Progress}
	** objects are created by the thread that calls this method. Failure to do
	** this '''will result in deadlock'''.
	**
	** Otherwise, this method will create {@link Progress} objects for each
	** task. If they are created before this, '''deadlock will result''' as the
	** handler waits for that progress to complete.
	*/
	protected Runnable createPushJob(final PushTask<T> task, final SafeClosure<X2<PushTask<T>, TaskAbortException>> post) {
		try {
			final P prog = (post != null)? tracker.addPushProgress(task): tracker.getPushProgress(task);
			return new Runnable() {
				public void run() {
					TaskAbortException ex = null;
					try { pushLive(task, prog); }
					catch (RuntimeException e) { ex = new TaskAbortException("failed", e); }
					catch (TaskAbortException e) { ex = e; }
					if (post != null) { post.invoke(X2(task, ex)); }
				}
			};
		} catch (final TaskInProgressException e) {
			return createJoinRunnable(task, e, post);
		}
	}

	/*========================================================================
	  public interface IterableSerialiser
	 ========================================================================*/

	/*@Override**/ public void pull(PullTask<T> task) throws TaskAbortException {
		try {
			try {
				P p = tracker.addPullProgress(task);
				exec.execute(createPullJob(task, null));
				p.join();
			} catch (TaskInProgressException e) {
				throw e.join();
			}
		} catch (InterruptedException e) {
			throw new TaskAbortException("Task pull was interrupted", e, true);
		}
	}

	/*@Override**/ public void push(PushTask<T> task) throws TaskAbortException {
		try {
			try {
				P p = tracker.addPushProgress(task);
				exec.execute(createPushJob(task, null));
				p.join();
			} catch (TaskInProgressException e) {
				throw e.join();
			}
		} catch (InterruptedException e) {
			throw new TaskAbortException("Task push was interrupted", e, true);
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation DOCUMENT
	*/
	/*@Override**/ public void pull(Iterable<PullTask<T>> tasks) throws TaskAbortException {
		List<Progress> progs = new ArrayList<Progress>();
		try {
			for (Iterator<PullTask<T>> it = tasks.iterator(); it.hasNext();) {
				PullTask<T> task = it.next();
				try {
					progs.add(tracker.addPullProgress(task));
					exec.execute(createPullJob(task, null));
				} catch (TaskInProgressException e) {
					it.remove();
					progs.add(e.getProgress());
				}
			}
			for (Progress p: progs) { p.join(); }
			// TODO NORM: toad - if it fails, we won't necessarily know until all of the
			// other tasks have completed ... is this acceptable?

		} catch (InterruptedException e) {
			throw new TaskAbortException("ParallelSerialiser pull was interrupted", e, true);
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation DOCUMENT
	*/
	/*@Override**/ public void push(Iterable<PushTask<T>> tasks) throws TaskAbortException {
		List<Progress> progs = new ArrayList<Progress>();
		try {
			for (Iterator<PushTask<T>> it = tasks.iterator(); it.hasNext();) {
				PushTask<T> task = it.next();
				try {
					progs.add(tracker.addPushProgress(task));
					exec.execute(createPushJob(task, null));
				} catch (TaskInProgressException e) {
					it.remove();
					progs.add(e.getProgress());
				}
			}
			for (Progress p: progs) { p.join(); }
			// TODO NORM: toad - if it fails, we won't necessarily know until all of the
			// other tasks have completed ... is this acceptable?

		} catch (InterruptedException e) {
			throw new TaskAbortException("ParallelSerialiser pull was interrupted", e, true);
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation DOCUMENT
	*/
	/*@Override**/ public <E> ObjectProcessor<PullTask<T>, E> pullSchedule(
		BlockingQueue<PullTask<T>> input,
		BlockingQueue<X2<PullTask<T>, TaskAbortException>> output,
		Map<PullTask<T>, E> deposit
	) {
		return new ObjectProcessor<PullTask<T>, E>(input, output, deposit, null, Executors.DEFAULT_EXECUTOR) {
			@Override protected Runnable createJobFor(PullTask<T> task) {
				return createPullJob(task, postProcess);
			}
		}.autostart();
	}

	/**
	** {@inheritDoc}
	**
	** This implementation DOCUMENT
	*/
	/*@Override**/ public <E> ObjectProcessor<PushTask<T>, E> pushSchedule(
		BlockingQueue<PushTask<T>> input,
		BlockingQueue<X2<PushTask<T>, TaskAbortException>> output,
		Map<PushTask<T>, E> deposit
	) {
		return new ObjectProcessor<PushTask<T>, E>(input, output, deposit, null, Executors.DEFAULT_EXECUTOR) {
			@Override protected Runnable createJobFor(PushTask<T> task) {
				return createPushJob(task, postProcess);
			}
		}.autostart();
	}

}
