/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.Archiver.*;

import java.util.Map;
import java.util.HashMap;

/**
** DOCUMENT
**
** @author infinity0
*/
abstract public class PartitionSerialiser<T, I> extends AbstractSerialiser<T, I> {

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

	/**
	** DOCUMENT
	** assume: for all t in tasks: t.meta equals meta
	*/
	abstract public <K> void pullPartition(Map<K, PullTask<T>> tasks, Object meta);

	/**
	** DOCUMENT
	** assume: for all t in tasks: t.meta equals meta
	*/
	abstract public <K> void pushPartition(Map<K, PushTask<T>> tasks, Object meta);


	/************************************************************************
	 * abstract public class AbstractSerialiser
	 ************************************************************************/

	@Override public void pull(PullTask<T> tasks) {
		throw new UnsupportedOperationException("PartitionSerialiser only supports operations on a map of tasks.");
	}

	@Override public void push(PushTask<T> tasks) {
		throw new UnsupportedOperationException("PartitionSerialiser only supports operations on a map of tasks.");
	}

	@Override public void pull(Iterable<PullTask<T>> tasks) {
		throw new UnsupportedOperationException("PartitionSerialiser only supports operations on a map of tasks.");
	}

	@Override public void push(Iterable<PushTask<T>> tasks) {
		throw new UnsupportedOperationException("PartitionSerialiser only supports operations on a map of tasks.");
	}

	@Override public <K> void pull(Map<K, PullTask<T>> tasks) {
		Map<Object, Map<K, PullTask<T>>> mmap = partition(tasks);
		for (Map.Entry<Object, Map<K, PullTask<T>>> en: mmap.entrySet()) {
			pullPartition(en.getValue(), en.getKey());
		}
	}

	@Override public <K> void push(Map<K, PushTask<T>> tasks) {
		Map<Object, Map<K, PushTask<T>>> mmap = partition(tasks);
		for (Map.Entry<Object, Map<K, PushTask<T>>> en: mmap.entrySet()) {
			pushPartition(en.getValue(), en.getKey());
		}
	}

}
