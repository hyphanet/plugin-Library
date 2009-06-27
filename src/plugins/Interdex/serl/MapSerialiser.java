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
** metadata, see {@link GroupMapSerialiser}.
**
** @author infinity0
** @see GroupMapSerialiser
*/
public interface MapSerialiser<T> extends Serialiser<T> {

	/**
	** Execute everything in a map of {@link PullTask}s, returning only when
	** they are all done.
	*/
	public <K> void pull(Map<K, PullTask<T>> tasks, Object meta);

	/**
	** Execute everything in a map of {@link PushTask}s, returning only when
	** they are all done.
	*/
	public <K> void push(Map<K, PushTask<T>> tasks, Object meta);

}
