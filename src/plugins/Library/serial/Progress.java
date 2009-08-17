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
	** Returns the subject / name of the task, if any.
	*/
	public String getSubject();

	/**
	** Returns the current completion status.
	*/
	public String getStatus();

	/**
	** Get the details of this progress as a {@link ProgressParts} object, or
	** throw an exception if the task has aborted.
	*/
	public ProgressParts getParts() throws TaskAbortException;

	/**
	** Whether the progress has started.
	*/
	public boolean isStarted();

	/**
	** Whether the progress has completed successfully, or throw an exception
	** if it has aborted.
	*/
	public boolean isDone() throws TaskAbortException;

	/**
	** Wait for the progress to finish.
	*/
	public void join() throws InterruptedException, TaskAbortException;

	/*

	display would look something like:

	getName(): getStatus()
	|        done|         started| known|                           estimate|

	*/

}
