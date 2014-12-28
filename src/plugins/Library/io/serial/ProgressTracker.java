/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.io.serial;

import plugins.Library.io.serial.Serialiser.*;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import freenet.library.util.CompositeIterable;
import freenet.library.util.exec.Progress;
import freenet.library.util.exec.TaskInProgressException;

/**
** Keeps track of a task's progress and provides methods to retrieve this data.
** For this to function properly, the data/meta for push/pull tasks MUST NOT
** be modified for as long as the task is in operation. This is because their
** {@link #equals(Object)} method is defined in terms of those fields, so any
** change to them would interfere with {@link WeakHashMap}'s ability to locate
** the tasks within the map.
**
** TODO HIGH make those fields (PullTask.meta / PushTask.data) final? would
** involve removing Task class.
**
** The progress objects are stored in a {@link WeakHashMap} and are freed
** automatically by the garbage collector if its corresponding task goes out of
** scope '''and''' no other strong references to the progress object exist.
**
** @author infinity0
*/
public class ProgressTracker<T, P extends Progress> {

	/**
	** Keeps track of the progress of each {@link PullTask}. The key is the
	** metadata of the task.
	*/
	final protected WeakHashMap<PullTask<T>, P> pullProgress = new WeakHashMap<PullTask<T>, P>();

	/**
	** Keeps track of the progress of each {@link PushTask}. The key is the
	** data of the task.
	*/
	final protected WeakHashMap<PushTask<T>, P> pushProgress = new WeakHashMap<PushTask<T>, P>();

	/**
	** An element class which is used to instantiate new elements.
	*/
	final protected Class<? extends P> progressClass;

	/**
	** DOCUMENT
	**
	** @param cc A class with a nullary constructor that is used to instantiate
	**           new progress objects. This argument may be null if a subclass
	**           overrides {@link #newProgress()}.
	*/
	public ProgressTracker(Class<? extends P> cc) {
		try {
			if (cc != null) { cc.newInstance(); }
		} catch (InstantiationException e) {
			throw new IllegalArgumentException("Cannot instantiate class. Make sure it has a nullary constructor.", e);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Cannot instantiate class. Make sure you have access to it.", e);
		}
		progressClass = cc;
	}

	protected P newProgress() {
		if (progressClass == null) {
			throw new IllegalStateException("ProgressTracker cannot create progress: No class was given to the constructor, but newElement() was not overriden.");
		}
		try {
			return progressClass.newInstance();
		} catch (InstantiationException e) {
			return null; // constructor should prevent this from ever happening, but the compiler bitches.
		} catch (IllegalAccessException e) {
			return null; // constructor should prevent this from ever happening, but the compiler bitches.
		}
	}

	// TODO NORM there is probably a better way of doing this...
	//final protected HashSet<PullTask<T>> pullWaiters() = new HashSet<PullTask<T>> pullWaiters();
	//final protected HashSet<PullTask<T>> pushWaiters() = new HashSet<PullTask<T>> pushWaiters();

	/**
	** Get the progress of a {@link PullTask} by its metadata.
	**
	** NOTE: since progress is stored in a {@link WeakHashMap} this means that
	** if the task has finished, then this will return null. (FIXME NORM)
	**
	** This doesn't have to be a major problem - when a task completes, detect
	** this, and deny further attempts to retrieve the progress.
	*/
	public P getPullProgressFor(Object meta/*, boolean block*/) {
		return getPullProgress(new PullTask<T>(meta)/*, block*/);
	}

	/**
	** Get the progress of a {@link PushTask} by its metadata.
	**
	** NOTE: since progress is stored in a {@link WeakHashMap} this means that
	** if the task has finished, then this will return null. (FIXME NORM)
	**
	** This doesn't have to be a major problem - when a task completes, detect
	** this, and deny further attempts to retrieve the progress.
	*/
	public P getPushProgressFor(T data/*, boolean block*/) {
		return getPushProgress(new PushTask<T>(data)/*, block*/);
	}

	/**
	** Get the progress of a {@link PullTask}.
	*/
	public P getPullProgress(PullTask<T> task/*, boolean block*/) {
		synchronized (pullProgress) {
			return pullProgress.get(task);
			/*P prog = pullProgress.get(task);
			if (prog != null || !block) { return prog; }

			pullWaiters.add(task);
			while ((prog = pullProgress.get(task)) == null) {
				pullProgress.wait();
			}
			return prog;*/
		}
	}

	/*public P getPullProgress(PullTask<T> task) {
		return getPullProgress(task, false);
	}*/

	/**
	** Get the progress of a {@link PushTask}.
	*/
	public P getPushProgress(PushTask<T> task/*, boolean block*/) {
		synchronized (pushProgress) {
			return pushProgress.get(task);
			/*P prog = pushProgress.get(task);
			if (prog != null || !block) { return prog; }

			pushWaiters.add(task);
			while ((prog = pushProgress.get(task)) == null) {
				pushProgress.wait();
			}
			return prog;*/
		}
	}

	/*public P getPushProgress(PushTask<T> task) {
		return getPushProgress(task, false);
	}*/

	/**
	** Creates a new pull progress and keeps track of it. If there is already a
	** progress for the metadata, throws {@link TaskInProgressException}. This
	** ensures that the object returned from this method has not been seen by
	** any other threads.
	**
	** @throws TaskInProgressException
	*/
	public P addPullProgress(PullTask<T> task) throws TaskInProgressException {
		synchronized (pullProgress) {
			P p = pullProgress.get(task);
			if (p != null) { throw new TaskInProgressException(p); }
			pullProgress.put(task, p = newProgress());

			/*if (pullWaiters.contains(task)) {
				pullProgress.notifyAll();
			}*/
			return p;
		}
	}

	/**
	** Creates a new push progress and keeps track of it. If there is already a
	** progress for the metadata, throws {@link TaskInProgressException}. This
	** ensures that the object returned from this method has not been seen by
	** any other threads.
	**
	** @throws TaskInProgressException
	*/
	public P addPushProgress(PushTask<T> task) throws TaskInProgressException {
		synchronized (pushProgress) {
			P p = pushProgress.get(task);
			if (p != null) { throw new TaskInProgressException(p); }
			pushProgress.put(task, p = newProgress());

			/*if (pushWaiters.remove(task)) {
				pushProgress.notifyAll();
			}*/
			return p;
		}
	}


	/**
	** Creates an iterable over the {@link Progress} objects corresponding to
	** the given pull tasks, all tracked by the given tracker.
	**
	** @param tracker The single tracker for all the ids
	** @param tasks The tasks to track
	*/
	public static <T, P extends Progress> Iterable<P> makePullProgressIterable(final ProgressTracker<T, P> tracker, final Iterable<PullTask<T>> tasks) {
		return new CompositeIterable<PullTask<T>, P>(tasks, true) {
			@Override public P nextFor(PullTask<T> next) {
				return tracker.getPullProgress(next);
			}
		};
	}

	/**
	** Creates an iterable over the {@link Progress} objects corresponding to
	** the given push tasks, all tracked by the given tracker.
	**
	** @param tracker The single tracker for all the ids
	** @param tasks The tasks to track
	*/
	public static <T, P extends Progress> Iterable<P> makePushProgressIterable(final ProgressTracker<T, P> tracker, final Iterable<PushTask<T>> tasks) {
		return new CompositeIterable<PushTask<T>, P>(tasks, true) {
			@Override public P nextFor(PushTask<T> next) {
				return tracker.getPushProgress(next);
			}
		};
	}

	/**
	** Creates an iterable over the {@link Progress} objects corresponding to
	** the given pull tracking ids, each tracked by its own tracker.
	**
	** @param ids Map of tracking ids to their respective trackers
	*/
	public static <T, P extends Progress> Iterable<P> makePullProgressIterable(final Map<PullTask<T>, ProgressTracker<T, ? extends P>> ids) {
		return new CompositeIterable<Map.Entry<PullTask<T>, ProgressTracker<T, ? extends P>>, P>(ids.entrySet(), true) {
			@Override public P nextFor(Map.Entry<PullTask<T>, ProgressTracker<T, ? extends P>> next) {
				return next.getValue().getPullProgress(next.getKey());
			}
		};
	}

	/**
	** Creates an iterable over the {@link Progress} objects corresponding to
	** the given push tracking ids, each tracked by its own tracker.
	**
	** @param ids Map of tracking ids to their respective trackers
	*/
	public static <T, P extends Progress> Iterable<P> makePushProgressIterable(final Map<PushTask<T>, ProgressTracker<T, ? extends P>> ids) {
		return new CompositeIterable<Map.Entry<PushTask<T>, ProgressTracker<T, ? extends P>>, P>(ids.entrySet(), true) {
			@Override public P nextFor(Map.Entry<PushTask<T>, ProgressTracker<T, ? extends P>> next) {
				return next.getValue().getPushProgress(next.getKey());
			}
		};
	}

}
