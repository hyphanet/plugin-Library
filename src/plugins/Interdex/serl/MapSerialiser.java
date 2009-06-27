/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.serl.Serialiser.*;

import java.util.Map;

/**
** An interface that handles a map of {@link Serialiser.Task}s. Implementations
** should assume a single metadata all of the tasks in the whole map. The input
** metadata for each task can be ignored (they should all be the same);
** however, output metadata may be different.
**
** For a class that can handle maps which do not have a single map-wide
** metadata, see {@link GroupMapSerialiser}. In the special case where each
** task in the map has its own unique metadata, use {@link IterableSerialiser}
** on the values of that map instead. (In such a case, the keys can be placed
** in the metadata if the serialiser needs to access them; this would probably
** be the most natural arrangement anyway.)
**
** @author infinity0
** @see GroupMapSerialiser
*/
public interface MapSerialiser<K, T> extends Serialiser<T> {

	/**
	** Execute everything in a map of {@link PullTask}s, returning only when
	** they are all done.
	**
	** @param tasks The map of tasks to execute
	** @param meta The map-wide metadata
	*/
	public void pull(Map<K, PullTask<T>> tasks, Object meta);

	/**
	** Execute everything in a map of {@link PushTask}s, returning only when
	** they are all done.
	**
	** @param tasks The map of tasks to execute
	** @param meta The map-wide metadata
	*/
	public void push(Map<K, PushTask<T>> tasks, Object meta);

}
