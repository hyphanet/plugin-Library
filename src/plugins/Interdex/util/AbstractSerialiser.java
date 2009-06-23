/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

/**
** A class that provides some convenience methods for {@link Serialiser}, plus
** several implementations of some higher-level functionality in terms of lower
** ones.
**
** @author infinity0
*/
abstract public class AbstractSerialiser<T> implements Serialiser<T> {

	/**
	** {@inheritDoc}
	**
	** This implementation just iterates through the list and does each task
	** one-by-one.
	*/
	public void doPull(Iterable<PullTask<T>> tasks) {
		for (PullTask<T> t: tasks) {
			doPull(t);
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation just iterates through the list and does each task
	** one-by-one.
	*/
	public void doPush(Iterable<PushTask<T>> tasks) {
		for (PushTask<T> t: tasks) {
			doPush(t);
		}
	}

	/**
	** Convenience method for pulling the data structure corresponding to a
	** given {@link Dummy}. It creates a new {@link PullTask} for the dummy,
	** calls {@link doPull(PullTask)}, and then returns the object from {@link
	** PullTask#get()}.
	**
	** @param dummy The dummy data structure.
	** @return The full data structure.
	*/
	public T pull(Dummy<T> dummy) {
		PullTask<T> ts = makePullTask(dummy);
		doPull(ts);
		return ts.get();
	}

	/**
	** Convenience method for pushing the data structure corresponding to a
	** given data structure. It creates a new {@link PushTask} for the data,
	** calls {@link doPush(PushTask)}, and then returns the {@link Dummy} from
	** {@link PushTask#get()}.
	**
	** @param data The full data structure.
	** @return The dummy data structure.
	*/
	public Dummy<T> push(T data) {
		PushTask<T> ts = makePushTask(data);
		doPush(ts);
		return ts.get();
	}

}
