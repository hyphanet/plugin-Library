/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import java.util.Map;

/**
** A class that serialises simple objects directly.
**
** @author infinity0
*/
public interface Archiver<T> {

	/**
	** Execute a {@link PullTask}, returning only when the task is done.
	*/
	public void pull(PullTask<T> task);

	/**
	** Execute a {@link PushTask}, returning only when the task is done.
	*/
	public void push(PushTask<T> task);

	/**
	** Defines a serialisation task for an object.
	**
	** DOCUMENT
	*/
	abstract public static class Task<T> {

		public Object meta = null;
		public T data = null;

	}

	/**
	** Defines a pull task.
	*/
	public static class PullTask<T> extends Task<T> {

		public PullTask(Object m) {
			meta = m;
		}

	}

	/**
	** Defines a push task.
	*/
	public static class PushTask<T> extends Task<T> {

		public PushTask(T d) {
			data = d;
		}

		public PushTask(T d, Object m) {
			data = d;
			meta = m;
		}

	}

}
