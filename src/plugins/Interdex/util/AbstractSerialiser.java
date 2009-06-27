/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import java.util.Map;

/**
** A class that provides a skeletal implementation of {@link Serialiser}. It
** divides up the process into independent parts for other classes to do, such
** as {@link Archiver} and {@link Translator}, and implements some higher-level
** functionality in terms of lower ones.
**
** @author infinity0
** @see Translator
** @see Archiver
*/
abstract public class AbstractSerialiser<T, I> implements Serialiser<T> {

	/**
	** The {@link Translator} to pass target data through.
	*/
	protected Translator<T, I> trans;

	/**
	** The {@link Archiver} to pass intermediate data through.
	*/
	protected Archiver<I> arch;

	/**
	** {@inheritDoc}
	**
	** DOCUMENT
	*/
	public void pull(PullTask<T> task) {
		PullTask<I> archivable = new PullTask<I>(task.meta);
		arch.pull(archivable);
		T pulldata = trans.rev(archivable.data);

		task.meta = archivable.meta; task.data = pulldata;
	}

	/**
	** {@inheritDoc}
	**
	** DOCUMENT
	*/
	public void push(PushTask<T> task) {
		I intermediate = trans.app(task.data);
		PushTask<I> archivable = new PushTask<I>(intermediate, task.meta);
		arch.push(archivable);

		task.meta = archivable.meta;
	}

	/**
	** {@inheritDoc}
	**
	** This implementation just iterates through the list and does each task
	** one-by-one.
	*/
	public void pull(Iterable<PullTask<T>> tasks) {
		for (PullTask<T> t: tasks) {
			pull(t);
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation just iterates through the list and does each task
	** one-by-one.
	*/
	public void push(Iterable<PushTask<T>> tasks) {
		for (PushTask<T> t: tasks) {
			push(t);
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation just iterates through the map and does each task
	** one-by-one.
	*/
	public <K> void pull(Map<K, PullTask<T>> tasks) {
		for (PullTask<T> t: tasks.values()) {
			pull(t);
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation just iterates through the map and does each task
	** one-by-one.
	*/
	public <K> void push(Map<K, PushTask<T>> tasks) {
		for (PushTask<T> t: tasks.values()) {
			push(t);
		}
	}

}
