/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

import plugins.Library.serial.Serialiser.*;

import java.util.Iterator;
import java.util.IdentityHashMap;

/**
** Keeps track of each task's progress and provides methods to retrieve this
** data. For this to function properly, the data/metadata for push/pull tasks
** (respectively) MUST NOT be null, and MUST NOT be internally modified by the
** parent {@link Serialiser} and (in the case of {@link Serialiser.Composite},
** its child serialisers). This is because they are used as keys into {@link
** IdentityHashMap}s.
**
** (Can't think of reason for the 2nd condition, now... maybe I was confused)
**
** TODO possibly make this use WeakIdentityHashMap (google-collections'
** MapMaker has an implementation). This will prevent the (rare and
** non-problematic, just inefficient) case of:
**
** * Thread A: inflate:pull: task complete, so remove Progress from the tracker
** * Thread B: inflate: checks condition of data - not loaded
** * Thread A: inflate: load data from task into the structure
** * Thread B: inflate:pull: tracker does not have Progress, re-pull the data
**
** But if we do this, we will have to require inflate() and deflate() to free
** the meta/data after it is used... may not be a good thing when we want to
** push data, but retain a copy of it in memory...
**
** On second thoughts, not freeing the data is fine, and will prevent it from
** being pushed twice, because it is still in the tracker's progress map.
**
** On third thoughts, no, what if the data changes, then we will want to push
** the same object twice... force deflate() to perform shallow clone?
**
** @author infinity0
*/
public class ProgressTracker<T, P extends Progress> {

	/**
	** Keeps track of the progress of each {@link PullTask}. The key is the
	** metadata of the task.
	*/
	protected IdentityHashMap<Object, P> pullProgress = new IdentityHashMap<Object, P>();

	/**
	** Keeps track of the progress of each {@link PushTask}. The key is the
	** data of the task.
	*/
	protected IdentityHashMap<T, P> pushProgress = new IdentityHashMap<T, P>();

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


	public P getPullProgress(Object meta) {
		synchronized (pullProgress) {
			return pullProgress.get(meta);
		}
	}

	public P getPushProgress(Object data) { // Object, not T, to match map.get(Object)
		synchronized (pushProgress) {
			return pushProgress.get(data);
		}
	}

	/**
	** Creates a new pull progress and keeps track of it. If there is already a
	** progress for the metadata, returns null. This ensures that the object
	** returned from this method has not been seen by any other threads.
	*/
	public P addPullProgress(Object meta) {
		synchronized (pullProgress) {
			if (pullProgress.containsKey(meta)) { return null; }
			P p = newProgress();
			pullProgress.put(meta, p);
			return p;
		}
	}

	/**
	** Creates a new pull progress and keeps track of it. If there is already a
	** progress for the metadata, returns null. This ensures that the object
	** returned from this method has not been seen by any other threads.
	*/
	public P addPushProgress(T data) {
		synchronized (pushProgress) {
			if (pushProgress.containsKey(data)) { return null; }
			P p = newProgress();
			pushProgress.put(data, p);
			return p;
		}
	}

	public P remPullProgress(Object meta) {
		synchronized (pullProgress) {
			return pullProgress.remove(meta);
		}
	}

	public P remPushProgress(Object data) { // Object, not T, to match map.remove(Object)
		synchronized (pushProgress) {
			return pushProgress.remove(data);
		}
	}

}
