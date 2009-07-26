/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

/**
** An abstraction of the progress of a task.
**
** @author infinity0
*/
public interface Progress {

	/**
	** Returns the name of the task, if any.
	*/
	public String getName();

	/**
	** Returns the current completion status.
	*/
	public String getStatus();

	/**
	** Returns how many "parts" of the task is done.
	*/
	public int partsDone();

	/**
	** Returns how many "parts" of the task are known to exists.
	*/
	public int partsTotal();

	/**
	** Whether the total number of parts is the actual final total number.
	*/
	public boolean isTotalFinal();

	/**
	** Wait for the progress to finish.
	*/
	public void join() throws InterruptedException, TaskAbortException;

}
