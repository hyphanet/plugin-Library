/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.serl.Serialiser.*;

import java.util.Iterator;
import java.util.IdentityHashMap;

/**
** Keeps track of each task's progress and provides methods to retrieve this
** data. For this to function properly, the data/metadata for push/pull tasks
** (respectively) MUST NOT be null, and MUST NOT be internally modified by the
** parent {@link Serialiser} and (in the case of {@link CompositeSerialiser},
** its child serialisers. This is because they are used as keys into {@link
** IdentityHashMap}s.
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

	public P getPushProgress(T data) {
		synchronized (pushProgress) {
			return pushProgress.get(data);
		}
	}

	/**
	** Creates a new pull progress and keeps track of it. If there is already a
	** progress for the metadata, returns null.
	*/
	public P addPullProgress(Object meta) {
		synchronized (pullProgress) {
			if (pullProgress.containsKey(meta)) { return null; }
			P p = newProgress();
			pullProgress.put(meta, p);
			return p;
		}
	}

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

	public P remPushProgress(T data) {
		synchronized (pushProgress) {
			return pushProgress.remove(data);
		}
	}

	public Iterable<P> iterableOfPull(Iterable<Object> mib) {
		return new PullProgressIterable(mib);
	}

	public Iterable<P> iterableOfPush(Iterable<T> dib) {
		return new PushProgressIterable(dib);
	}

	/************************************************************************
	** An {@link Iterator} view over the {@link Progress} corresponding to
	** the given group of metadata.
	*/
	public class PullProgressIterable implements Iterable<P> {
		final private Iterable<Object> ib;

		public PullProgressIterable(Iterable<Object> mib) {
			ib = mib;
		}

		public Iterator<P> iterator() {
			final Iterator<Object> it = ib.iterator();
			return new Iterator<P>() {
				private Object last;

				@Override public boolean hasNext() {
					return it.hasNext();
				}

				@Override public P next() {
					last = it.next();
					return getPullProgress(last);
				}

				@Override public void remove() {
					it.remove();
					remPullProgress(last);
				}
			};
		}
	}

	/************************************************************************
	** An {@link Iterator} view over the {@link Progress} corresponding to
	** the given group of data.
	*/
	public class PushProgressIterable implements Iterable<P> {
		final private Iterable<T> ib;

		public PushProgressIterable(Iterable<T> dib) {
			ib = dib;
		}

		public Iterator<P> iterator() {
			final Iterator<T> it = ib.iterator();
			return new Iterator<P>() {
				private T last;

				@Override public boolean hasNext() {
					return it.hasNext();
				}

				@Override public P next() {
					last = it.next();
					return getPushProgress(last);
				}

				@Override public void remove() {
					it.remove();
					remPushProgress(last);
				}
			};
		}
	}

}
