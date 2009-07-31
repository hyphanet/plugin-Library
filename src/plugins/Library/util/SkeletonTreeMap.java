/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import plugins.Library.serial.Serialiser.*;
import plugins.Library.serial.Translator;
import plugins.Library.serial.MapSerialiser;
import plugins.Library.serial.DataFormatException;
import plugins.Library.serial.TaskAbortException;

import java.util.Comparator;
import java.util.Set;
import java.util.AbstractSet;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Collection;
import java.util.AbstractCollection;
import java.util.Iterator;

/**
** A {@link SkeletonMap} of a {@link TreeMap}. DOCUMENT.
**
** This implementation is NOT thread-safe.
**
** @author infinity0
*/
public class SkeletonTreeMap<K, V>
extends TreeMap<K, V>
implements SkeletonMap<K, V> {

	/**
	** A TreeMap of objects tracking the load status of each key. This map
	** always contains the same keys as the main map, and for all keys k:
	** (loaded.get(k) != null) if and only if the value for k has not been
	** loaded into the map, and implies that (get(k) == null). (However, note
	** that (get(k) == null) could also be the actual loaded value for key k.)
	*/
	final TreeMap<K, Object> loaded;

	/**
	** The meta data for this skeleton.
	*/
	protected Object mapmeta;

	/**
	** Keeps track of the number of dummies in the map.
	*/
	protected transient int dummyCount;

	public SkeletonTreeMap() {
		super();
		loaded = new TreeMap<K, Object>();
	}

	public SkeletonTreeMap(Comparator<? super K> c) {
		super(c);
		loaded = new TreeMap<K, Object>(c);
	}

	public SkeletonTreeMap(Map<? extends K,? extends V> m) {
		super(m);
		loaded = new TreeMap<K, Object>();
		for (K key: m.keySet()) {
			loaded.put(key, null);
		}
	}

	public SkeletonTreeMap(SortedMap<K,? extends V> m) {
		super(m);
		loaded = new TreeMap<K, Object>(m.comparator());
		for (K key: m.keySet()) {
			loaded.put(key, null);
		}
	}

	public SkeletonTreeMap(SkeletonTreeMap<K, V> m) {
		super(m);
		loaded = (TreeMap<K, Object>)m.loaded.clone();
	}

	/**
	** DOCUMENT
	*/
	public Object putDummy(K key, Object o) {
		if (o == null) {
			throw new IllegalArgumentException("Cannot put a null dummy into the map. Use put(K, V) to mark an object as loaded.");
		}
		put(key, null);
		Object d = loaded.put(key, o);
		if (d == null) { ++dummyCount; }
		return d;
	}

	protected MapSerialiser<K, V> serialiser;

	public void setSerialiser(MapSerialiser<K, V> s) {
		if (serialiser != null && !isLive()) {
			throw new IllegalStateException("Cannot change the serialiser when the structure is not live.");
		}
		serialiser = s;
	}


	/*========================================================================
	  public interface SkeletonMap
	 ========================================================================*/

	@Override public boolean isLive() {
		return dummyCount == 0;
	}

	@Override public boolean isBare() {
		return dummyCount == size();
	}

	@Override public Object getMeta() {
		return mapmeta;
	}

	@Override public void setMeta(Object m) {
		mapmeta = m;
	}

	@Override public void inflate() throws TaskAbortException {
		if (serialiser == null) { throw new IllegalStateException("No serialiser set for this structure."); }

		Map<K, PullTask<V>> tasks = new HashMap<K, PullTask<V>>(size()*2);
		for (K k: keySet()) {
			Object o = loaded.get(k);
			if (o == null) { continue; }
			tasks.put(k, new PullTask<V>(o));
		}
		serialiser.pull(tasks, mapmeta);

		for (Map.Entry<K, PullTask<V>> en: tasks.entrySet()) {
			put(en.getKey(), en.getValue().data);
		}
	}

	@Override public void deflate() throws TaskAbortException {
		if (serialiser == null) { throw new IllegalStateException("No serialiser set for this structure."); }

		Map<K, PushTask<V>> tasks = new HashMap<K, PushTask<V>>(size()*2);
		for (K k: keySet()) {
			tasks.put(k, new PushTask<V>(get(k), loaded.get(k)));
		}
		//try {
			serialiser.push(tasks, mapmeta);
		//} catch (DataNotLoadedException e) {
		//	throw new DataNotLoadedException("The deflate operation requires some extra data to be loaded first", this, (K)e.getKey(), e.getValue());
		//}

		for (Map.Entry<K, PushTask<V>> en: tasks.entrySet()) {
			putDummy(en.getKey(), en.getValue().meta);
		}
	}

	@Override public void inflate(K key) throws TaskAbortException {
		if (serialiser == null) { throw new IllegalStateException("No serialiser set for this structure."); }

		Object keymeta = loaded.get(key);
		if (keymeta == null) { return; }
		Map<K, PullTask<V>> tasks = new HashMap<K, PullTask<V>>();
		tasks.put(key, new PullTask<V>(keymeta));

		serialiser.pull(tasks, mapmeta);

		put(key, tasks.remove(key).data);
		if (tasks.isEmpty()) { return; }

		for (Map.Entry<K, PullTask<V>> en: tasks.entrySet()) {
			// other keys may also have been inflated, so add them if the metadata
			// match. if they don't match, then the data was only partially inflated
			// so ignore for now. (needs a new data structure to allow partial
			// inflates of values...)
			K k = en.getKey();
			PullTask<V> t = en.getValue();
			Object m = loaded.get(k);
			if (m.equals(t.meta)) {
				put(k, t.data);
			}
		}
	}

	@Override public void deflate(K key) throws TaskAbortException {
		// TODO: redesign this, or the bin packer
		if (serialiser == null) { throw new IllegalStateException("No serialiser set for this structure."); }

		Map<K, PushTask<V>> tasks = new HashMap<K, PushTask<V>>(size()*2);
		for (K k: keySet()) {
			// PRIORITY there ought to be a better way of doing things than this. the
			// whole system could be redesigned at some point to allow smoother partial
			// writes of indexes

			// also, at the moment, even the Packer does not support this
			tasks.put(k, new PushTask<V>(k.equals(key)? get(k): null, loaded.get(k)));
		}
		//try {
			serialiser.push(tasks, mapmeta);
		//} catch (DataNotLoadedException e) {
		//	throw new DataNotLoadedException("The deflate operation requires some extra data to be loaded first", this, (K)e.getKey(), e.getValue());
		//}

		for (Map.Entry<K, PushTask<V>> en: tasks.entrySet()) {
			if (en.getValue().data != null) {
				putDummy(en.getKey(), en.getValue().meta);
			}
		}
	}

	/************************************************************************
	** {@link Translator} with access to the members of {@link TreeMap}.
	**
	** This implementation provides static methods to translate between this
	** class and a map from ({@link String} forms of the key) to ({@link
	** Task#meta metadata} of the values).
	**
	** TODO atm this can't handle custom comparators
	*/
	abstract public static class TreeMapTranslator<K, V>
	implements Translator<SkeletonTreeMap<K, V>, Map<String, Object>> {

		/**
		** Forward translation. If the translator is given is {@code null},
		** it will use {@link Object#toString()}.
		**
		** @param map The data structue to translate
		** @param intm A map to populate with the translated mappings
		** @param ktr An optional translator between key and {@link String}.
		*/
		public static <K, V> Map<String, Object> app(SkeletonTreeMap<K, V> map, Map<String, Object> intm, Translator<K, String> ktr) {
			if (!map.isBare()) {
				throw new IllegalArgumentException("Data structure is not bare. Try calling deflate() first.");
			}
			if (map.comparator() != null) {
				throw new UnsupportedOperationException("Sorry, this translator does not (yet) support comparators");
			}
			// OPTIMISE maybe get rid of intm and just always use IdentityHashMap
			if (ktr != null) {
				for (Map.Entry<K, Object> en: map.loaded.entrySet()) {
					intm.put(ktr.app(en.getKey()), en.getValue());
				}
			} else {
				for (Map.Entry<K, Object> en: map.loaded.entrySet()) {
					intm.put(en.getKey().toString(), en.getValue());
				}
			}
			return intm;
		}

		/**
		** Backward translation. If no translator is given, a direct cast will
		** be attempted.
		**
		** @param intm The map of translated mappings to extract
		** @param map The data structue to populate with metadata
		** @param ktr A translator between key and {@link String}
		*/
		public static <K, V> SkeletonTreeMap<K, V> rev(Map<String, Object> intm, SkeletonTreeMap<K, V> map, Translator<K, String> ktr) {
			if (ktr == null) {
				try {
					for (Map.Entry<String, Object> en: intm.entrySet()) {
						map.putDummy((K)en.getKey(), en.getValue());
					}
				} catch (ClassCastException e) {
					throw new DataFormatException("TreeMapTranslator: reverse translation failed. Try supplying a non-null key-translator.", e, null, null, null);
				}
			} else {
				for (Map.Entry<String, Object> en: intm.entrySet()) {
					map.putDummy(ktr.rev(en.getKey()), en.getValue());
				}
			}
			return map;
		}

	}

	/*========================================================================
	  public class TreeMap
	 ========================================================================*/

	@Override public void clear() {
		super.clear();
		loaded.clear();
	}

	@Override public int size() { return loaded.size(); }

	@Override public Object clone() {
		return new SkeletonTreeMap(this);
	}

	@Override public Comparator<? super K> comparator() { return super.comparator(); }

	@Override public boolean containsKey(Object key) { return loaded.containsKey(key); }

	@Override public boolean containsValue(Object value) {
		// TODO maybe make this iterate through values()
		if (!isLive()) {
			throw new DataNotLoadedException("TreeMap not fully loaded.", this);
		} else {
			return super.containsValue(value);
		}
	}

	@Override public V get(Object key) {
		Object o = loaded.get(key);
		if (o != null) {
			throw new DataNotLoadedException("Data not loaded for key " + key + ": " + o, this, (K)key, o);
		}
		return super.get(key);
	}

	/**
	** {@inheritDoc}
	**
	** NOTE: if the value for the key hasn't been loaded yet, then this method
	** will return **null** instead of returning the actual previous value
	** (that hasn't been loaded yet).
	**
	** TODO: could code a setStrictChecksMode() or something to have this
	** method throw {@link DataNotLoadedException} in such circumstances, at
	** the user's discretion.
	*/
	@Override public V put(K key, V value) {
		Object o = loaded.put(key, null);
		if (o != null) { --dummyCount; }
		return super.put(key, value);
	}

	//public void putAll(Map<? extends K,? extends V> map);

	/**
	** {@inheritDoc}
	**
	** NOTE: if the value for the key hasn't been loaded yet, then this method
	** will return **null** instead of returning the actual previous value
	** (that hasn't been loaded yet).
	**
	** TODO: could code a setStrictChecksMode() or something to have this
	** method throw {@link DataNotLoadedException} in such circumstances, at
	** the user's discretion.
	*/
	@Override public V remove(Object key) {
		loaded.remove(key);
		return super.remove(key);
	}

	private Set<Map.Entry<K,V>> entries;
	@Override public Set<Map.Entry<K,V>> entrySet() {
		if (entries == null) {
			entries = new AbstractSet<Map.Entry<K, V>>() {

				public int size() { return SkeletonTreeMap.this.size(); }

				public Iterator<Map.Entry<K, V>> iterator() {
					return new CombinedIterator(SkeletonTreeMap.super.entrySet().iterator(), SkeletonTreeMap.this.loaded.entrySet().iterator(), CombinedIterator.ENTRY);
				}

				public void clear() {
					SkeletonTreeMap.this.clear();
				}

				public boolean contains(Object o) {
					if (!(o instanceof Map.Entry)) { return false; }
					Map.Entry e = (Map.Entry)o;
					return SkeletonTreeMap.this.get(e.getKey()).equals(e.getValue());
				}

				public boolean remove(Object o) {
					boolean c = contains(o);
					if (c) {
						Map.Entry e = (Map.Entry)o;
						SkeletonTreeMap.this.remove(e.getKey());
					}
					return c;
				}

			};
		}
		return entries;
	}

	private Set<K> keys;
	@Override public Set<K> keySet() {
		if (keys == null) {
			keys = new AbstractSet<K>() {

				public int size() { return SkeletonTreeMap.this.size(); }

				public Iterator<K> iterator() {
					return new CombinedIterator(SkeletonTreeMap.super.keySet().iterator(), SkeletonTreeMap.this.loaded.entrySet().iterator(), CombinedIterator.KEY);
				}

				public void clear() { SkeletonTreeMap.this.clear(); }

				public boolean contains(Object o) {
					return SkeletonTreeMap.this.containsKey(o);
				}

				public boolean remove(Object o) {
					boolean c = contains(o);
					SkeletonTreeMap.this.remove(o);
					return c;
				}

			};
		}
		return keys;
	}

	private Collection<V> values;
	@Override public Collection<V> values() {
		if (values == null) {
			values = new AbstractCollection<V>() {

				public int size() { return SkeletonTreeMap.this.size(); }

				public Iterator<V> iterator() {
					return new CombinedIterator(SkeletonTreeMap.super.values().iterator(), SkeletonTreeMap.this.loaded.entrySet().iterator(), CombinedIterator.VALUE);
				}

				public void clear() { SkeletonTreeMap.this.clear(); }

			};
		}
		return values;
	}

	@Override public K firstKey() { return loaded.firstKey(); }

	@Override public K lastKey() { return loaded.lastKey(); }

	/**
	** {@inheritDoc}
	**
	** Not yet implemented - throws {@link UnsupportedOperationException}
	*/
	@Override public SortedMap<K,V> headMap(K toKey) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	/**
	** {@inheritDoc}
	**
	** Not yet implemented - throws {@link UnsupportedOperationException}
	*/
	@Override public SortedMap<K,V> subMap(K fromKey, K toKey) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	/**
	** {@inheritDoc}
	**
	** '''THIS IMPLEMENTATION IS INCOMPLETE AND INCORRECT''' and is only
	** provided as a temporary solution to make {@link SkeletonBTreeMap} work.
	** That class only needs the keys of the tailmap; not the values, so the
	** hack is fine.
	**
	** Attempting to retrieve any values from this tailmap will result in
	** incorrect behaviour, specifically, {@code null} will be returned for
	** non-loaded data, instead of {@link DataNotLoadedException} being thrown.
	*/
	@Override public SortedMap<K,V> tailMap(K fromKey) {
		return super.tailMap(fromKey);
		// URGENT: this is ONLY here for SkeletonBTreeMap
		//throw new UnsupportedOperationException("Not implemented.");
	}


	/*========================================================================
	  public class AbstractMap
	 ========================================================================*/

	@Override public boolean equals(Object o) {
		if (!(o instanceof SkeletonTreeMap)) { return false; }
		return super.equals(o) && loaded.equals(((SkeletonTreeMap)o).loaded);
	}

	@Override public int hashCode() { return super.hashCode() ^ loaded.hashCode(); }

	@Override public boolean isEmpty() { return loaded.isEmpty(); }

	// public String toString() { return super.toString(); }

	/************************************************************************
	** Iterator that goes through both the loaded map and the object map at the
	** same time, throwing {@link DataNotLoadedException} when it encounters
	** meta elements. After this occurs, all subsequent attempts to fetch the
	** next value will fail with {@link IllegalStateException} with the cause
	** set to the {@link DataNotLoadedException} that was thrown.
	**
	** @author infinity0
	*/
	private class CombinedIterator<T> implements Iterator<T> {

		final private Iterator<T> iter;
		final private Iterator<Map.Entry<K, V>> iterloaded;

		final private int type;
		final private static int KEY = 0;
		final private static int VALUE = 1;
		final private static int ENTRY = 2;

		RuntimeException exceptionThrown;

		CombinedIterator(Iterator<T> it, Iterator<Map.Entry<K, V>> itl, int t) {
			iter = it;
			iterloaded = itl;
			type = t;
		}

		public boolean hasNext() {
			return iterloaded.hasNext();
		}

		public T next() throws DataNotLoadedException {

			if (exceptionThrown != null) {
				throw new IllegalStateException(exceptionThrown);
			}

			Map.Entry<K, V> e = iterloaded.next();
			T n = iter.next();

			switch(type) {
			case ENTRY:
			case VALUE:
				if (e.getValue() != null) {
					throw exceptionThrown = new DataNotLoadedException("Data not loaded for key " + e.getKey() + ": " + e.getValue(), SkeletonTreeMap.this, e.getKey(), e.getValue());
				}
				break;
			}

			return n;
		}

		public void remove() {
			iterloaded.remove();
			iter.remove();
		}

	}

}
