/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.serl.Serialiser.*;

import java.util.Map;

/**
** An interface that handles a map of {@link Serialiser.Task}s. As well as the
** metadata associated with each individual task, each map also has an
** associated metadata for the entire structure.
**
** Data structures which have a map that can be grouped into submaps that each
** have an associated metadata for the entire submap, can just use the methods
** provided here, for each submap-metadata pair.
**
** @author infinity0
*/
public interface MapSerialiser<K, T> extends Serialiser<T> {

	/**
	** Execute everything in a map of {@link PullTask}s, returning only when
	** they are all done.
	**
	** Implementations should only execute the tasks for which the metadata
	** is not null.
	**
	** @param tasks The map of tasks to execute
	** @param meta The map-wide metadata
	*/
	public void pull(Map<K, PullTask<T>> tasks, Object meta);

	/**
	** Execute everything in a map of {@link PushTask}s, returning only when
	** they are all done.
	**
	** Implementations should only execute the tasks for which the data is not
	** null, but may require that an entire submap (which might include tasks
	** with null data and non-null metadata) be passed into the method in order
	** to perform better optimisation.
	**
	** @param tasks The map of tasks to execute
	** @param meta The map-wide metadata
	*/
	public void push(Map<K, PushTask<T>> tasks, Object meta);

}
