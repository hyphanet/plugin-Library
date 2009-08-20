/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.io.serial;

import plugins.Library.io.serial.Serialiser.*;
import plugins.Library.util.exec.TaskAbortException;

/**
** An interface that handles an iterable group of {@link Serialiser.Task}s.
**
** @author infinity0
*/
public interface IterableSerialiser<T> extends Archiver<T> {

	/**
	** Execute everything in a group of {@link PullTask}s, returning only when
	** they are all done.
	**
	** @param tasks The group of tasks to execute
	*/
	public void pull(Iterable<PullTask<T>> tasks) throws TaskAbortException;

	/**
	** Execute everything in a group of {@link PushTask}s, returning only when
	** they are all done.
	**
	** @param tasks The group of tasks to execute
	*/
	public void push(Iterable<PushTask<T>> tasks) throws TaskAbortException;

}
