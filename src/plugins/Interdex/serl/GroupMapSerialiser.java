/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.serl.Serialiser.*;

import java.util.Map;
import java.util.HashMap;

/**
** A class that handles a map of {@link Serialiser.Task}s that does not have
** the same map-wide metadata for each of the entries.
**
** @author infinity0
*/
abstract public class GroupMapSerialiser<T> implements MapSerialiser<T> {

	protected <K, P extends Task> Map<Object, Map<K, P>> partition(Map<K, P> tasks) {
		// map of metadata to combined-tasks
		Map<Object, Map<K, P>> mmap = new HashMap<Object, Map<K, P>>(4);

		for (Map.Entry<K, P> en: tasks.entrySet()) {
			P task = en.getValue();

			// find the combined-tasks map for the metadata for this task
			Map<K, P> map;
			map = mmap.get(task.meta);
			if (map == null) {
				// OPTIMISE HashMap constructor
				map = new HashMap<K, P>(tasks.size()*2/(mmap.size()+1));
				mmap.put(task.meta, map);
			}

			// put the task into the combined-tasks map
			map.put(en.getKey(), task);
		}

		return mmap;
	}

	public <K> void pull(Map<K, PullTask<T>> tasks) {
		Map<Object, Map<K, PullTask<T>>> mmap = partition(tasks);
		for (Map.Entry<Object, Map<K, PullTask<T>>> en: mmap.entrySet()) {
			pull(en.getValue(), en.getKey());
		}
	}

	public <K> void push(Map<K, PushTask<T>> tasks) {
		Map<Object, Map<K, PushTask<T>>> mmap = partition(tasks);
		for (Map.Entry<Object, Map<K, PushTask<T>>> en: mmap.entrySet()) {
			push(en.getValue(), en.getKey());
		}
	}

}
