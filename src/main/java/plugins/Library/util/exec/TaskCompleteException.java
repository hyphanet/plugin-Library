/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.exec;

/**
** Thrown when a task aborts due to the task already having been done, eg. by
** another thread.
**
** @author infinity0
*/
public class TaskCompleteException extends TaskAbortException {

	// EXPAND

	public TaskCompleteException(String s) {
		super(s, null, false, false);
	}

}
