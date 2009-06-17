/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

/**
** A class that handles serialisation tasks. TODO
**
** @author infinity0
*/
public interface Serialiser {

	/**
	** Poll the queue for a finished task.
	*/
	public SerialiseTask poll();

	public InflateTask newInflateTask(Object o);
	public DeflateTask newDeflateTask(Object o);

	// TODO perhaps make this implement Runnable
	public interface SerialiseTask {

		/**
		** Start the task and push it onto the queue.
		*/
		public void start();

		/**
		** See whether the task is finished.
		**
		** @return Null if the task is unfinished, or information on the final
		** status of the task, eg. path to data.
		*/
		public Object poll();

		/**
		** Wait for the task to finish. TODO maybe rename...
		**
		** @return Information on the final status of the task, eg. path to data.
		*/
		public Object join();

	}

	public interface InflateTask extends SerialiseTask {
		public Object get(String key);
	}

	public interface DeflateTask extends SerialiseTask {
		public void put(String key, Object o);
	}

}
