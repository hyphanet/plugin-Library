/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

import plugins.Library.event.Progress;

/**
** Thrown when a task is already in progress elsewhere.
**
** @author infinity0
** @see Serialiser
*/
public class TaskInProgressException extends TaskAbortException {

	final Progress prog;

	public TaskInProgressException(String s, Progress p) {
		super(s, null, false, false);
		prog = p;
	}

	public TaskInProgressException(Progress p) {
		super(null, null, false, false);
		prog = p;
	}

	public Progress getProgress() {
		return prog;
	}

	/**
	** DOCUMENT
	*/
	public TaskCompleteException join() throws InterruptedException, TaskAbortException {
		prog.join();
		return new TaskCompleteException("Completed task: " + prog.getSubject());
	}

}
