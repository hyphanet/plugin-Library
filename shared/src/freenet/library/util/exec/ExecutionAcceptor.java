/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.util.exec;

/**
** Accepts state-changes on an execution.
**
** @param <V> The specific type of execution, if any.
** @author infinity0
*/
public interface ExecutionAcceptor<V> {

	public void acceptStarted(Execution<V> opn);

	public void acceptDone(Execution<V> opn, V result);

	public void acceptAborted(Execution<V> opn, TaskAbortException abort);

}
