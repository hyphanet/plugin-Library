/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import java.util.TreeMap;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

/**
** Emulates a PrefixTreeMap where some of the data has not been fully loaded.
** Operations on this data structure will throw a DataNotLoadedException when
** it encounters dummy objects such as DummyEntry.
**
** This object can be safely casted to a TreeMap if isComplete() returns true,
** which occurs if and only if no mappings point to null or DummyValue objects.
**
** @author infinity0
*/
public class IncompleteTreeMap<K, V> extends TreeMap<K, V>
implements IncompleteMap<K, V> {

	/**
	** Represents a value which has been loaded.
	**
	** @author infinity0
	*/
	public static class Value<V> {

		final V value;
		public Value(V v) {
			value = v;
		}
		public V get() {
			return value;
		}

	}

	/**
	** Represents a value which has not been loaded, but which a key refers to,
	** and we have a skeleton/dummy value for.
	**
	** @author infinity0
	*/
	public static class DummyValue<V> extends Value<V> {

		public DummyValue(V v) {
			super(v);
		}

	}

	final TreeMap<K, Value<V>> tmap = new TreeMap<K, Value<V>>();

	/************************************************************************
	 * public interface IncompleteMap
	 ************************************************************************/

	public boolean isComplete() {
		// TODO
		return false;
	}

	public Map<K, V> complete() {
		if (!isComplete()) {
			throw new DataNotLoadedException("TreeMap not fully loaded.", this, "*");
		} else {
			//TODO
			return null;
		}
	}

	/************************************************************************
	 * public class TreeMap
	 ************************************************************************/

	// TODO
	//public void clear()
	//public Object clone()
	//public Comparator<? super K> comparator()
	//public boolean containsKey(Object key)
	//public boolean containsValue(Object value)
	//public Set<Map.Entry<K,V>> entrySet()
	//public K firstKey()
	//public V get(Object key)
	//public SortedMap<K,V> headMap(K toKey)
	//public Set<K> keySet()
	//public K lastKey()
	//public V put(K key, V value)
	//public void putAll(Map<? extends K,? extends V> map)
	//public V remove(Object key)
	//public int size()
	//public SortedMap<K,V> subMap(K fromKey, K toKey)
	//public SortedMap<K,V> tailMap(K fromKey)
	//public Collection<V> values()

	/************************************************************************
	 * public class AbstractMap
	 ************************************************************************/

	// TODO
	//public boolean equals(Object o) { }
	public int hashCode() { return tmap.hashCode(); }
	public boolean isEmpty() { return tmap.isEmpty(); }
	public String toString() { return tmap.toString(); }

}
