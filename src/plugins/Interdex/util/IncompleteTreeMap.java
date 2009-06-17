/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import java.util.TreeMap;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import java.util.AbstractSet;
import java.util.SortedMap;
import java.util.Collection;
import java.util.AbstractCollection;
import java.util.Iterator;

import plugins.Interdex.util.Serialiser.SerialiseTask;
import plugins.Interdex.util.Serialiser.InflateTask;
import plugins.Interdex.util.Serialiser.DeflateTask;

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
	** A TreeMap of objects tracking the load status of each key.
	*/
	final TreeMap<K, Object> loaded;

	public IncompleteTreeMap() {
		super();
		loaded = new TreeMap<K, Object>();
	}

	public IncompleteTreeMap(Comparator<? super K> c) {
		super(c);
		loaded = new TreeMap<K, Object>(c);
	}

	public IncompleteTreeMap(Map<? extends K,? extends V> m) {
		super(m);
		loaded = new TreeMap<K, Object>();
		for (K key: m.keySet()) {
			loaded.put(key, Boolean.FALSE);
		}
	}

	public IncompleteTreeMap(SortedMap<K,? extends V> m) {
		super(m);
		loaded = new TreeMap<K, Object>(m.comparator());
		for (K key: m.keySet()) {
			loaded.put(key, Boolean.FALSE);
		}
	}

	public IncompleteTreeMap(IncompleteTreeMap<K, V> m) {
		super(m);
		loaded = (TreeMap<K, Object>)m.loaded.clone();
	}

	public IncompleteTreeMap(K[] keys) {
		loaded = new TreeMap<K, Object>();
		for (K k: keys) {
			putDummy(k);
		}
	}

	public Object putDummy(K key) {
		put(key, null);
		return loaded.put(key, Boolean.FALSE);
	}

	public Object putDummy(K key, Object o) {
		put(key, null);
		return loaded.put(key, o);
	}


	/************************************************************************
	 * public interface IncompleteMap
	 ************************************************************************/

	public boolean isComplete() {
		for (Object o: loaded.values()) {
			if (o != null) { return false; }
		}
		return true;
	}

	public Map<K, V> complete() {
		if (!isComplete()) {
			throw new DataNotLoadedException("TreeMap not fully loaded.", this);
		} else {
			return new TreeMap(this);
		}
	}

	public Object inflate() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Object deflate() {
		TreeMap<K, DeflateTask> tm = new TreeMap<K, DeflateTask>();
		DeflateTask de = serialiser.newDeflateTask(this);
		for (K k: keySet()) {
			V v = get(k);
			DeflateTask d = serialiser.newDeflateTask(v);
			d.put(null, v);
			d.start();
			tm.put(k, d);
		}
		for (K k: keySet()) {
			Object o = tm.get(k).join();
			de.put(k.toString(), o);
		}
		de.start();
		return de.join();
	}

	public Object inflate(IncompleteMap<K, V> m) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Object deflate(IncompleteMap<K, V> m) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Object inflate(K key) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Object deflate(K key) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	private Serialiser serialiser;

	public Serialiser getSerialiser() {
		return serialiser;
	}

	public void setSerialiser(Serialiser s) {
		serialiser = s;
	}

	/************************************************************************
	 * public class TreeMap
	 ************************************************************************/

	public void clear() {
		super.clear();
		loaded.clear();
	}

	public Object clone() {
		return new IncompleteTreeMap(this);
	}

	public Comparator<? super K> comparator() { return super.comparator(); }

	public boolean containsKey(Object key) { return loaded.containsKey(key); }

	public boolean containsValue(Object value) {
		if (!isComplete()) {
			throw new DataNotLoadedException("TreeMap not fully loaded.", this);
		} else {
			return super.containsValue(value);
		}
	}

	private Set<Map.Entry<K,V>> entries;
	public Set<Map.Entry<K,V>> entrySet() {
		if (entries == null) {
			entries = new AbstractSet<Map.Entry<K, V>>() {

				public int size() { return IncompleteTreeMap.this.size(); }

				public Iterator<Map.Entry<K, V>> iterator() {
					return new CombinedIterator(IncompleteTreeMap.super.entrySet().iterator(), IncompleteTreeMap.this.loaded.entrySet().iterator(), CombinedIterator.ENTRY);
				}

				public void clear() {
					IncompleteTreeMap.this.clear();
				}

				public boolean contains(Object o) {
					if (!(o instanceof Map.Entry)) { return false; }
					Map.Entry e = (Map.Entry)o;
					return IncompleteTreeMap.this.get(e.getKey()).equals(e.getValue());
				}

				public boolean remove(Object o) {
					boolean c = contains(o);
					if (c) {
						Map.Entry e = (Map.Entry)o;
						IncompleteTreeMap.this.remove(e.getKey());
					}
					return c;
				}

			};
		}
		return entries;
	}

	public K firstKey() { return loaded.firstKey(); }

	public V get(Object key) {
		Object o = loaded.get(key);
		if (o != null) {
			throw new DataNotLoadedException("Data not loaded for key " + key + ": " + o, this, key, o);
		}
		return super.get(key);
	}

	public SortedMap<K,V> headMap(K toKey) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	private Set<K> keys;
	public Set<K> keySet() {
		if (keys == null) {
			keys = new AbstractSet<K>() {

				public int size() { return IncompleteTreeMap.this.size(); }

				public Iterator<K> iterator() {
					return new CombinedIterator(IncompleteTreeMap.super.keySet().iterator(), IncompleteTreeMap.this.loaded.keySet().iterator(), CombinedIterator.KEY);
				}

				public void clear() { IncompleteTreeMap.this.clear(); }

				public boolean contains(Object o) {
					return IncompleteTreeMap.this.containsKey(o);
				}

				public boolean remove(Object o) {
					boolean c = contains(o);
					IncompleteTreeMap.this.remove(o);
					return c;
				}

			};
		}
		return keys;
	}

	public K lastKey() { return loaded.lastKey(); }

	public V put(K key, V value) {
		loaded.put(key, null);
		return super.put(key, value);
	}

	//public void putAll(Map<? extends K,? extends V> map);

	public V remove(Object key) {
		loaded.remove(key);
		return super.remove(key);
	}

	public int size() { return loaded.size(); }

	public SortedMap<K,V> subMap(K fromKey, K toKey) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public SortedMap<K,V> tailMap(K fromKey) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	private Collection<V> values;
	public Collection<V> values() {
		if (values == null) {
			values = new AbstractCollection<V>() {

				public int size() { return IncompleteTreeMap.this.size(); }

				public Iterator<V> iterator() {
					return new CombinedIterator(IncompleteTreeMap.super.values().iterator(), IncompleteTreeMap.this.loaded.values().iterator(), CombinedIterator.VALUE);
				}

				public void clear() { IncompleteTreeMap.this.clear(); }

			};
		}
		return values;
	}

	/************************************************************************
	 * public class AbstractMap
	 ************************************************************************/

	public boolean equals(Object o) {
		if (o instanceof IncompleteTreeMap) {
			return super.equals(o) && loaded.equals(((IncompleteTreeMap)o).loaded);
		}
		return super.equals(o);
	}
	public int hashCode() { return super.hashCode() ^ loaded.hashCode(); }
	public boolean isEmpty() { return loaded.isEmpty(); }
	// public String toString() { return super.toString(); }


	/**
	** Transparent iterator that throws DataNotLoadedException
	** TODO expand this doc
	**
	** @author infinity0
	*/
	private static class CombinedIterator<T> implements Iterator<T> {

		final private Iterator<T> iter;
		final private Iterator<?> iterloaded;

		final private int type;
		final private static int KEY = 0;
		final private static int VALUE = 1;
		final private static int ENTRY = 2;

		RuntimeException exceptionThrown;

		CombinedIterator(Iterator<T> it, Iterator<?> itl, int t) {
			iter = it;
			iterloaded = itl;
			type = t;
		}

		public boolean hasNext() {
			return iterloaded.hasNext();
		}

		public T next() throws DataNotLoadedException {

			Object o = iterloaded.next();

			switch(type) {
			case ENTRY:
				Map.Entry e = (Map.Entry)o;
				if (e.getValue() != null) {
					exceptionThrown = new DataNotLoadedException("Data not loaded for key " + e.getKey() + ": " + e.getValue(), this, e.getKey(), e.getValue());
				}
				break;
			case VALUE:
				if (o != null) {
					exceptionThrown = new DataNotLoadedException("Data not loaded: " + o, this, null, o);
				}
				break;
			}

			if (exceptionThrown != null) {
				throw exceptionThrown;
			}

			return iter.next();
		}

		public void remove() {
			iterloaded.remove();
			iter.remove();
		}

	}

}
