/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

/**
** Thrown when a task fails. EXPAND
**
** TODO think about making this a CHECKED exception and having deflate, push,
** pull throw it.
**
** @author infinity0
** @see Serialiser
*/
public class TaskFailException extends RuntimeException {

	public TaskFailException(String s, Throwable t) {
		super(s, t);
	}

	public TaskFailException(String s) {
		super(s);
	}

	public TaskFailException(Throwable t) {
		super(t);
	}

}
