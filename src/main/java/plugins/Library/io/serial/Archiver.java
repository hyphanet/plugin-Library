/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.io.serial;

import plugins.Library.io.serial.Serialiser.*;
import plugins.Library.util.exec.TaskAbortException;

import java.util.Collection;
import java.util.Map;

/**
** An interface that handles a single {@link Serialiser.Task}.
**
** @author infinity0
*/
public interface Archiver<T> extends Serialiser<T> {

	/**
	** Execute a {@link PullTask}, returning only when the task is done.
	**
	** Implementations of this method which act on a recursive data structure
	** that is designed to be partially loaded (such as {@link
	** plugins.Library.util.SkeletonMap}), should only pull the minimal data
	** necessary to give a consistent instance of that data structure. This is
	** to provide a finer degree of control over the pulling process, which is
	** likely to be needed by such a data structure.
	**
	** In other words, the data structure should not be automatically populated
	** after it is formed.
	**
	** @param task The task to execute
	*/
	public void pull(PullTask<T> task) throws TaskAbortException;

	/**
	** Execute a {@link PushTask}, returning only when the task is done.
	**
	** Implementations of this method which act on a recursive data structure
	** that is designed to be partially loaded (such as {@link
	** plugins.Library.util.SkeletonMap}), should ensure that the data passed
	** into this method is minimal in terms of that data structure. This is to
	** provide a finer degree of control over the pulling process, which is
	** likely to be needed by such a data structure.
	**
	** If the data is not minimal, implementations should throw {@link
	** IllegalArgumentException} rather than automatically depopulating
	** the data structure.
	**
	** @param task The task to execute
	*/
	public void push(PushTask<T> task) throws TaskAbortException;

}
