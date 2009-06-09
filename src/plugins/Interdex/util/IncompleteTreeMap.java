/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import java.util.TreeMap;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedMap;
import java.util.Collection;

/**
** Emulates a TreeMap where some of the data has not been fully loaded.
** Operations on this data structure will throw a DataNotLoadedException when
** it encounters dummy objects such as DummyValue.
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

		/************************************************************************
		 * public class Object
		 ************************************************************************/

		public boolean equals(Object o) {
			if (o instanceof Value) { return value.equals(((Value)o).get()); }
			return value.equals(o);
		}

		public int hashCode() {
			return value.hashCode();
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

	/**
	** A TreeMap of Value containers backing up this map.
	*/
	final TreeMap<K, Value<V>> tmap;

	public IncompleteTreeMap() {
		tmap = new TreeMap<K, Value<V>>();
	}

	public IncompleteTreeMap(Comparator<? super K> c) {
		tmap = new TreeMap<K, Value<V>>(c);
	}

	public IncompleteTreeMap(Map<? extends K,? extends V> m) {
		tmap = new TreeMap<K, Value<V>>(m.comparator());
		for (K key: m.keySet()) {
			tmap.put(key, new Value<V>(m.get(key)));
		}
	}

	public IncompleteTreeMap(SortedMap<K,? extends V> m) {
		tmap = new TreeMap<K, Value<V>>(m.comparator());
		for (K key: m.keySet()) {
			tmap.put(key, new Value<V>(m.get(key)));
		}
	}

	public IncompleteTreeMap(TreeMap<K, Value<V>> m, boolean clone) {
		tmap = (clone)? (TreeMap<K, Value<V>>)m.clone(): m;
	}

	public V putDummy(K key) {
		Value<V> v = tmap.put(key, new DummyValue<V>(null));
		return (v == null)? null: v.get();
	}

	public V putDummy(K key, V value) {
		Value<V> v = tmap.put(key, new DummyValue<V>(value));
		return (v == null)? null: v.get();
	}


	/************************************************************************
	 * public interface IncompleteMap
	 ************************************************************************/

	public boolean isComplete() {
		for (Value<V> v: tmap.values()) {
			if (v == null || v instanceof DummyValue) {
				return false;
			}
		}
		return true;
	}

	public Map<K, V> complete() {
		if (!isComplete()) {
			throw new DataNotLoadedException("TreeMap not fully loaded.", this, "*");
		} else {
			Map<K, V> ctree = new TreeMap<K, V>(comparator());
			for (K k: tmap.keySet()) {
				ctree.put(k, tmap.get(k).get());
			}
			return ctree;
		}
	}

	/************************************************************************
	 * public class TreeMap
	 ************************************************************************/

	public void clear() { tmap.clear(); }

	public Object clone() {
		return new IncompleteTreeMap(tmap, true);
	}

	public Comparator<? super K> comparator() { return tmap.comparator(); }

	public boolean containsKey(Object key) { return tmap.containsKey(key); }

	public boolean containsValue(Object value) {
		if (!isComplete()) {
			throw new DataNotLoadedException("TreeMap not fully loaded.", this, "*");
		} else {
			return tmap.containsValue(new Value(value));
		}
	}

	public Set<Map.Entry<K,V>> entrySet() {
		// TODO: The best/easiest way to implement this (and the other Map
		// methods that involve the V parameter) would probably be to throw
		// DataNotFoundException if isComplete() is false. Otherwise, have a
		// cache for complete() and return cache.entrySet()
		//
		// We should NOT return an entrySet when isComplete() is false, because
		// then it would be possible to iterate over this entrySet and call
		// entry.getValue(), possibly returning a DummyValue, which is against
		// the point of this class. There would have to be some major hackery
		// involved to make this throw DataNotFoundException instead.
		//
		// (for the record, sdiz thinks it's a bad idea to have made this into
		// a collections class in the first place.)
		throw new UnsupportedOperationException("Not implemented.");
	}

	public K firstKey() { return tmap.firstKey(); }

	public V get(Object key) {
		Value<V> v = tmap.get(key);
		if (v instanceof DummyValue) {
			throw new DataNotLoadedException("Value not loaded for key " + key, ((DummyValue)v).get(), key);
		}
		return (v == null)? null: v.get();
	}

	public SortedMap<K,V> headMap(K toKey) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Set<K> keySet() { return tmap.keySet(); }

	public K lastKey() { return tmap.lastKey(); }

	public V put(K key, V value) {
		Value<V> v = tmap.put(key, new Value<V>(value));
		return (v == null)? null: v.get();
	}

	public void putAll(Map<? extends K,? extends V> map) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public V remove(Object key) {
		Value<V> v = tmap.remove(key);
		return (v == null)? null: v.get();
	}

	public int size() { return tmap.size(); }

	public SortedMap<K,V> subMap(K fromKey, K toKey) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public SortedMap<K,V> tailMap(K fromKey) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Collection<V> values() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	/************************************************************************
	 * public class AbstractMap
	 ************************************************************************/

	public boolean equals(Object o) {
		if (o instanceof IncompleteTreeMap) {
			return tmap.equals(((IncompleteTreeMap)o).tmap);
		}
		return tmap.equals(o);
	}
	public int hashCode() { return tmap.hashCode(); }
	public boolean isEmpty() { return tmap.isEmpty(); }
	public String toString() { return tmap.toString(); }

}
