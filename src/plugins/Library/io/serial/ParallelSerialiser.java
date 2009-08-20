/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

import plugins.Library.event.Progress;
import plugins.Library.serial.Serialiser.*;
import plugins.Library.util.concurrent.Scheduler;

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

	final protected PullTaskHandler pullHandler = new PullTaskHandler(exec);
	final protected PushTaskHandler pushHandler = new PushTaskHandler(exec);

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

	/*========================================================================
	  public interface IterableSerialiser
	 ========================================================================*/

	/*@Override**/ public void pull(PullTask<T> task) throws TaskAbortException {
		try {
			try {
				P p = tracker.addPullProgress(task);
				pullHandler.schedule(task);
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
				pushHandler.schedule(task);
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
					pullHandler.schedule(task);
				} catch (TaskInProgressException e) {
					it.remove();
					progs.add(e.getProgress());
				}
			}
			for (Progress p: progs) { p.join(); }
			// TODO: toad - if it fails, we won't necessarily know until all of the
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
					pushHandler.schedule(task);
				} catch (TaskInProgressException e) {
					it.remove();
					progs.add(e.getProgress());
				}
			}
			for (Progress p: progs) { p.join(); }
			// TODO: toad - if it fails, we won't necessarily know until all of the
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
	/*@Override**/ public Scheduler pullSchedule(BlockingQueue<PullTask<T>> input,
	                                        BlockingQueue<PullTask<T>> output,
	                                        ConcurrentMap<PullTask<T>, TaskAbortException> error) {
		return new PullTaskHandler(input, output, error);
	}

	/**
	** {@inheritDoc}
	**
	** This implementation DOCUMENT
	*/
	/*@Override**/ public Scheduler pushSchedule(BlockingQueue<PushTask<T>> input,
	                                        BlockingQueue<PushTask<T>> output,
	                                        ConcurrentMap<PushTask<T>, TaskAbortException> error) {
		return new PushTaskHandler(input, output, error);
	}


	/************************************************************************
	** {@link Scheduler} that handles the tasks given to it. This is basically
	** just a glorified wrapper around {@link ThreadPoolExecutor}.
	**
	** Instances of this class can use one of two modes of operation -
	** {@linkplain #ParallelSerialiser.TaskHandler(ThreadPoolExecutor) serial}
	** or {@linkplain #ParallelSerialiser.TaskHandler(BlockingQueue,
	** BlockingQueue, ConcurrentMap, int) parallel}.
	**
	** TODO maybe code a serial mode that can support out and error queues.
	**
	** @author infinity0
	*/
	abstract public class TaskHandler<K extends Task> extends Thread implements Scheduler {

		final protected BlockingQueue<K> in;
		final protected BlockingQueue<K> out;
		final protected ConcurrentMap<K, TaskAbortException> err;

		final protected static int defaultMaxThreads = 0x10;
		final protected ThreadPoolExecutor exec;

		final protected boolean parallel;
		volatile protected boolean running = true;

		/**
		** Creates a new handler using the given queues, with a newly-constructed
		** {@link ThreadPoolExecutor} to execute them. A new thread will be started
		** that listens on the input queue and calls {@link #schedule(Object)} on
		** each task received. When no more items are to be added to the queue, this
		** thread can be stopped with {@link #close()}.
		**
		** In this mode, the {@link Scheduler} will create {@link Progress} objects
		** for each task as it is run. If they are created before this, deadlock
		** will result as the handler tries to wait for that progress to complete.
		**
		** @param i The input queue
		** @param o The output queue
		** @param e The error map
		** @param m Most threads the {@link ThreadPoolExecutor} will start
		*/
		public TaskHandler(BlockingQueue<K> i, BlockingQueue<K> o, ConcurrentMap<K, TaskAbortException> e, int m) {
			if (i == null || o == null || e == null) {
				throw new IllegalArgumentException("When running as a parallel service, all three queues must be non-null");
			}
			parallel = true;
			in = i;
			out = o;
			err = e;
			// this is the same as Executors.newFixedThreadPool(m), except that that has 0 timeout
			exec = new ThreadPoolExecutor(
				m, m, 1, TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>(),
				new ThreadPoolExecutor.CallerRunsPolicy()
			);
			start();
		}

		/**
		** Creates a new handler which uses the given {@link ThreadPoolExecutor} to
		** execute the tasks passed to it.
		**
		** In this mode, it is assumed that {@link Progress} objects are created by
		** the thread that calls {@link #schedule(Object)}. '''Failure to do this
		** will result in deadlock.'''
		**
		** @param e The {@link ThreadPoolExecutor} to handle task execution.
		*/
		public TaskHandler(ThreadPoolExecutor e) {
			parallel = false;
			in = out = null; err = null;
			exec = e;
		}

		abstract public void handleTask(K task) throws TaskInProgressException;

		// TODO could make this part of the Scheduler interface

		public void schedule(final K task) {
			try {
				handleTask(task);
			} catch (final TaskInProgressException e) {
				exec.execute(new Runnable() {
					public void run() {
						try {
							Progress p = e.getProgress();
							p.join();
							if (err != null) { err.put(task, new TaskCompleteException("Task complete: " + p.getSubject())); }
						} catch (InterruptedException e) {
							if (err != null) { err.put(task, new TaskAbortException("Progress join was interrupted", e, true)); }
						} catch (TaskAbortException a) {
							if (err != null) { err.put(task, a); }
						}
					}
				});
			}
		}

		// public class Thread

		@Override public void run() {
			if (!parallel) {
				throw new IllegalStateException("This scheduler was not created as a parallel service. Use schedule() instead.");
			}
			try {
				while (running) {
					try {
						final K task = in.take();
						schedule(task);
					} catch (InterruptedException e) {
						continue;
					}
				}
			} finally {
				exec.shutdown();
				try {
					while (!exec.awaitTermination(1, TimeUnit.SECONDS));
				} catch (InterruptedException e) { }
			}
		}

		// public interface Scheduler

		/*@Override**/ public void close() {
			running = false;
			interrupt();
		}

		/*@Override**/ public boolean isActive() {
			// NOTE: this method is pointless when in serial mode
			// URGENT verify this
			return exec.getTaskCount() > exec.getCompletedTaskCount();
		}

		// public class Object

		@Override public void finalize() {
			close();
		}

	}

	protected class PullTaskHandler extends TaskHandler<PullTask<T>> {

		public PullTaskHandler(BlockingQueue<PullTask<T>> i, BlockingQueue<PullTask<T>> o, ConcurrentMap<PullTask<T>, TaskAbortException> e) {
			super(i, o, e, defaultMaxThreads);
		}

		public PullTaskHandler(ThreadPoolExecutor e) {
			super(e);
		}

		@Override public void handleTask(final PullTask<T> task) throws TaskInProgressException {
			// if not parallel then we assume the caller has made the progress object
			// DOCUMENT THIS MORE THOROUGHLY
			final P prog = (parallel)? tracker.addPullProgress(task):
			                           tracker.getPullProgress(task);
			exec.execute(new Runnable() {
				public void run() {
					try {
						pullLive(task, prog);
						if (parallel) { out.put(task); }
					} catch (InterruptedException e) {
						if (parallel) { err.put(task, new TaskAbortException("Task pull was interrupted", e, true)); }
					} catch (TaskAbortException e) {
						if (parallel) { err.put(task, e); }
					}
				}
			});
		}
	}

	protected class PushTaskHandler extends TaskHandler<PushTask<T>> {

		public PushTaskHandler(BlockingQueue<PushTask<T>> i, BlockingQueue<PushTask<T>> o, ConcurrentMap<PushTask<T>, TaskAbortException> e) {
			super(i, o, e, defaultMaxThreads);
		}

		public PushTaskHandler(ThreadPoolExecutor e) {
			super(e);
		}

		@Override public void handleTask(final PushTask<T> task) throws TaskInProgressException {
			// if not parallel then we assume the caller has made the progress object
			// DOCUMENT THIS MORE THOROUGHLY
			final P prog = (parallel)? tracker.addPushProgress(task):
			                           tracker.getPushProgress(task);
			exec.execute(new Runnable() {
				public void run() {
					try {
						pushLive(task, prog);
						if (parallel) { out.put(task); }
					} catch (InterruptedException e) {
						if (parallel) { err.put(task, new TaskAbortException("Task push was interrupted", e, true)); }
					} catch (TaskAbortException e) {
						if (parallel) { err.put(task, e); }
					}
				}
			});
		}
	}

}
