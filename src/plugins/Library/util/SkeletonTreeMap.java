/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import plugins.Library.io.serial.Serialiser.*;
import plugins.Library.io.serial.Translator;
import plugins.Library.io.serial.MapSerialiser;
import plugins.Library.io.DataFormatException;
import plugins.Library.util.exec.TaskAbortException;
import plugins.Library.util.exec.TaskCompleteException;

import java.util.Iterator;
import java.util.Comparator;
import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.AbstractMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.HashMap;

/**
** A {@link SkeletonMap} of a {@link TreeMap}. DOCUMENT
**
** Note: this implementation, like {@link TreeMap}, is not thread-safe.
**
** @author infinity0
*/
public class SkeletonTreeMap<K, V>
extends TreeMap<K, V>
implements Map<K, V>, SortedMap<K, V>, SkeletonMap<K, V>, Cloneable {

	// We don't actually use any of the methods or fields of TreeMap directly,
	// but we need to extend it to pretend that this "is" a TreeMap.

	/*static {
		// DEBUG
		System.out.println("Using the new SkeletonTreeMap implementation");
	}*/

	/**
	** The backing map. This map is only ever modified structurally - ie. the
	** {@link SkeletonValue} for a given key is never overwritten; only its
	** contents are. This ensures correct behaviour for the {@link
	** UnwrappingIterator} class.
	*/
	final protected TreeMap<K, SkeletonValue<V>> skmap;

	/**
	** The meta data for this skeleton.
	*/
	protected Object mapmeta;

	/**
	** Keeps track of the number of ghost values in the map.
	*/
	protected transient int ghosts;

	public SkeletonTreeMap() {
		skmap = new TreeMap<K, SkeletonValue<V>>();
	}

	public SkeletonTreeMap(Comparator<? super K> c) {
		skmap = new TreeMap<K, SkeletonValue<V>>(c);
	}

	public SkeletonTreeMap(Map<? extends K,? extends V> m) {
		skmap = new TreeMap<K, SkeletonValue<V>>();
		putAll(m);
	}

	public SkeletonTreeMap(SortedMap<K,? extends V> m) {
		skmap = new TreeMap<K, SkeletonValue<V>>(m.comparator());
		putAll(m);
	}

	public SkeletonTreeMap(SkeletonTreeMap<K, V> m) {
		skmap = new TreeMap<K, SkeletonValue<V>>(m.comparator());
		for (Map.Entry<K, SkeletonValue<V>> en: m.skmap.entrySet()) {
			skmap.put(en.getKey(), en.getValue().clone());
		}
		ghosts = m.ghosts;
	}

	/************************************************************************
	** A class that wraps the associated value (which may not be loaded) of a
	** key in a {@link SkeletonTreeMap}, along with metadata and its loaded
	** status.
	**
	** @author infinity0
	*/
	protected static class SkeletonValue<V> implements Cloneable {

		public Object meta;
		public V data;
		/**
		** Thes is true by default, to make the {@link #putGhost(Object,
		** Object)} and {@link #put(Object, Object)} algorithms simpler.
		*/
		protected boolean isLoaded = true;

		/**
		** Set the data and mark the value as loaded.
		*/
		public V set(V v) {
			V old = data;
			data = v;
			// meta = null; TODO decide what to do with this.
			isLoaded = true;
			return old;
		}

		/**
		** Set the metadata and mark the value as not loaded.
		*/
		public Object setGhost(Object m) {
			Object old = meta;
			meta = m;
			data = null;
			isLoaded = false;
			return old;
		}

		@Override public SkeletonValue<V> clone() {
			try {
				return (SkeletonValue<V>)super.clone();
			} catch (CloneNotSupportedException e) {
				throw new AssertionError(e);
			}
		}

	}

	/**
	** Set the metadata for the {@link SkeletonValue} for a given key.
	**
	** OPTIMISE code a version that throws {@link IllegalArgumentException} if
	** the key is not already in the map. (Useful for the *flate methods.)
	*/
	public Object putGhost(K key, Object o) {
		if (o == null) {
			throw new IllegalArgumentException("Cannot put a null dummy into the map. Use put(K, V) to mark an object as loaded.");
		}
		SkeletonValue<V> sk = skmap.get(key);
		if (sk == null) { skmap.put(key, sk = new SkeletonValue<V>()); }
		if (sk.isLoaded) { ++ghosts; }
		return sk.setGhost(o);
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

	/*@Override**/ public boolean isLive() {
		return ghosts == 0;
	}

	/*@Override**/ public boolean isBare() {
		return ghosts == skmap.size();
	}

	/*@Override**/ public MapSerialiser<K, V> getSerialiser() {
		return serialiser;
	}

	/*@Override**/ public Object getMeta() {
		return mapmeta;
	}

	/*@Override**/ public void setMeta(Object m) {
		mapmeta = m;
	}

	/*@Override**/ public void inflate() throws TaskAbortException {
		if (serialiser == null) { throw new IllegalStateException("No serialiser set for this structure."); }
		if (isLive()) { return; }

		Map<K, PullTask<V>> tasks = new HashMap<K, PullTask<V>>(size()<<1);
		// load only the tasks that need pulling
		for (Map.Entry<K, SkeletonValue<V>> en: skmap.entrySet()) {
			SkeletonValue<V> skel = en.getValue();
			if (skel.isLoaded) { continue; }
			tasks.put(en.getKey(), new PullTask<V>(skel.meta));
		}
		serialiser.pull(tasks, mapmeta);

		for (Map.Entry<K, PullTask<V>> en: tasks.entrySet()) {
			// TODO make this an efficient test that throws an exception
			assert(skmap.get(en.getKey()) != null);
			// TODO atm old metadata is retained, could update?
			put(en.getKey(), en.getValue().data);
		}
	}

	/*@Override**/ public void deflate() throws TaskAbortException {
		if (serialiser == null) { throw new IllegalStateException("No serialiser set for this structure."); }
		if (isBare()) { return; }

		Map<K, PushTask<V>> tasks = new HashMap<K, PushTask<V>>(size()<<1);
		// load all the tasks, most MapSerialisers need this info
		for (Map.Entry<K, SkeletonValue<V>> en: skmap.entrySet()) {
			SkeletonValue<V> skel = en.getValue();
			tasks.put(en.getKey(), new PushTask<V>(skel.data, skel.meta));
		}
		serialiser.push(tasks, mapmeta);

		for (Map.Entry<K, PushTask<V>> en: tasks.entrySet()) {
			// TODO make this an efficient test that throws an exception
			assert(skmap.get(en.getKey()) != null);
			putGhost(en.getKey(), en.getValue().meta);
		}
	}

	/*@Override**/ public void inflate(K key) throws TaskAbortException {
		if (serialiser == null) { throw new IllegalStateException("No serialiser set for this structure."); }

		SkeletonValue<V> skel = skmap.get(key);
		if (skel == null) {
			throw new IllegalArgumentException("Key " + key + " does not belong to the map");
		} else if (skel.isLoaded) {
			return;
		}

		Map<K, PullTask<V>> tasks = new HashMap<K, PullTask<V>>();
		tasks.put(key, new PullTask<V>(skel.meta));

		try {
			serialiser.pull(tasks, mapmeta);

			put(key, tasks.remove(key).data);
			// TODO atm old metadata is retained, could update?
			if (tasks.isEmpty()) { return; }

			for (Map.Entry<K, PullTask<V>> en: tasks.entrySet()) {
				// other keys may also have been inflated, so add them, but only if the
				// generated metadata match.
				PullTask<V> t = en.getValue();
				SkeletonValue<V> sk = skmap.get(en.getKey());
				if (sk == null) { throw new DataFormatException("SkeletonTreeMap got unexpected extra data from the serialiser.", null, sk, tasks, en.getKey()); }
				if (sk.meta.equals(t.meta)) {
					if (!sk.isLoaded) { --ghosts; }
					sk.set(t.data);
				}
				// if they don't match, then the data was only partially inflated
				// (note: at the time of coding, only SplitPacker does this, and
				// that is deprecated) so ignore for now. (needs a new data
				// structure to allow partial inflates of values...)
			}

		} catch (TaskCompleteException e) {
			assert(skmap.get(key).isLoaded);
		} catch (DataFormatException e) {
			throw new TaskAbortException("Could not complete inflate operation", e);
		}
	}

	/*@Override**/ public void deflate(K key) throws TaskAbortException {
		if (serialiser == null) { throw new IllegalStateException("No serialiser set for this structure."); }

		SkeletonValue<V> skel = skmap.get(key);
		if (skel == null || !skel.isLoaded) { return; }

		Map<K, PushTask<V>> tasks = new HashMap<K, PushTask<V>>(size()<<1);
		// add the data we want to deflate, plus all already-deflated data
		// OPTMISE think of a way to let the serialiser signal what
		// information it needs? will be quite painful to implement that...
		tasks.put(key, new PushTask<V>(skel.data, skel.meta));
		for (Map.Entry<K, SkeletonValue<V>> en: skmap.entrySet()) {
			skel = en.getValue();
			if (skel.isLoaded) { continue; }
			assert(skel.data == null);
			tasks.put(en.getKey(), new PushTask<V>(null, skel.meta));
		}
		serialiser.push(tasks, mapmeta);

		for (Map.Entry<K, PushTask<V>> en: tasks.entrySet()) {
			// TODO make this an efficient test that throws an exception
			assert(skmap.get(en.getKey()) != null);
			// serialiser may have pulled and re-pushed some of the
			// eg. Packer does this. so set all the metadata for all the tasks
			// (unchanged tasks should have been discarded anyway)
			putGhost(en.getKey(), en.getValue().meta);
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
	**
	** @author infinity0
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
				for (Map.Entry<K, SkeletonValue<V>> en: map.skmap.entrySet()) {
					intm.put(ktr.app(en.getKey()), en.getValue().meta);
				}
			} else {
				for (Map.Entry<K, SkeletonValue<V>> en: map.skmap.entrySet()) {
					intm.put(en.getKey().toString(), en.getValue().meta);
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
		public static <K, V> SkeletonTreeMap<K, V> rev(Map<String, Object> intm, SkeletonTreeMap<K, V> map, Translator<K, String> ktr) throws DataFormatException {
			if (ktr == null) {
				try {
					for (Map.Entry<String, Object> en: intm.entrySet()) {
						map.putGhost((K)en.getKey(), en.getValue());
					}
				} catch (ClassCastException e) {
					throw new DataFormatException("TreeMapTranslator: reverse translation failed. Try supplying a non-null key-translator.", e, intm, null, null);
				}
			} else {
				for (Map.Entry<String, Object> en: intm.entrySet()) {
					map.putGhost(ktr.rev(en.getKey()), en.getValue());
				}
			}
			return map;
		}

	}

	/*========================================================================
	  public interface Map
	 ========================================================================*/

	@Override public int size() { return skmap.size(); }

	@Override public boolean isEmpty() { return skmap.isEmpty(); }

	@Override public void clear() {
		skmap.clear();
		ghosts = 0;
	}

	@Override public Comparator<? super K> comparator() { return skmap.comparator(); }

	@Override public boolean containsKey(Object key) { return skmap.containsKey(key); }

	@Override public boolean containsValue(Object value) {
		if (!isLive()) {
			// TODO maybe make this work even when map is partially loaded
			throw new DataNotLoadedException("TreeMap not fully loaded.", this);
		} else {
			for (SkeletonValue<V> v: skmap.values()) {
				if (value.equals(v.data)) { return true; }
			}
			return false;
		}
	}

	@Override public V get(Object key) {
		SkeletonValue<V> sk = skmap.get(key);
		if (sk == null) { return null; }
		if (!sk.isLoaded) { throw new DataNotLoadedException("Data not loaded for key " + key + ": " + sk.meta, this, key, sk.meta); }
		return sk.data;
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
	** the user's discretion. This applies for CombinedEntry.setValue() too.
	**
	** OPTIMISE code a version that throws {@link IllegalArgumentException} if
	** the key is not already in the map. (Useful for the *flate methods.)
	*/
	@Override public V put(K key, V value) {
		SkeletonValue<V> sk = skmap.get(key);
		if (sk == null) { skmap.put(key, sk = new SkeletonValue<V>()); }
		if (!sk.isLoaded) { --ghosts; }
		return sk.set(value);
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
		SkeletonValue<V> sk = skmap.remove(key);
		if (sk == null) { return null; }
		if (!sk.isLoaded) { --ghosts; }
		return sk.data;
	}

	private Set<Map.Entry<K,V>> entries;
	@Override public Set<Map.Entry<K,V>> entrySet() {
		if (entries == null) {
			entries = new AbstractSet<Map.Entry<K, V>>() {

				@Override public int size() { return skmap.size(); }

				@Override public Iterator<Map.Entry<K, V>> iterator() {
					return new UnwrappingIterator<Map.Entry<K, V>>(skmap.entrySet().iterator(), UnwrappingIterator.ENTRY);
				}

				@Override public void clear() {
					SkeletonTreeMap.this.clear();
				}

				@Override public boolean contains(Object o) {
					if (!(o instanceof Map.Entry)) { return false; }
					Map.Entry e = (Map.Entry)o;
					return SkeletonTreeMap.this.get(e.getKey()).equals(e.getValue());
				}

				@Override public boolean remove(Object o) {
					// SkeletonTreeMap.remove() called, which automatically updates _ghosts
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

				@Override public int size() { return skmap.size(); }

				@Override public Iterator<K> iterator() {
					return new UnwrappingIterator<K>(skmap.entrySet().iterator(), UnwrappingIterator.KEY);
				}

				@Override public void clear() { SkeletonTreeMap.this.clear(); }

				@Override public boolean contains(Object o) {
					return SkeletonTreeMap.this.containsKey(o);
				}

				@Override public boolean remove(Object o) {
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

				@Override public int size() { return SkeletonTreeMap.this.size(); }

				@Override public Iterator<V> iterator() {
					return new UnwrappingIterator<V>(skmap.entrySet().iterator(), UnwrappingIterator.VALUE);
				}

				@Override public void clear() { SkeletonTreeMap.this.clear(); }

			};
		}
		return values;
	}

	@Override public K firstKey() {
		return skmap.firstKey();
	}

	@Override public K lastKey() {
		return skmap.lastKey();
	}

	/**
	** {@inheritDoc}
	**
	** NOTE: the sorted map returned by this method is limited in its
	** functionality, and will throw {@link UnsupportedOperationException} for
	** most of its methods. For details, see {@link UnwrappingSortedSubMap}.
	*/
	@Override public SortedMap<K, V> subMap(K fr, K to) {
		return new UnwrappingSortedSubMap<K, V>(skmap.subMap(fr, to));
	}

	/**
	** {@inheritDoc}
	**
	** NOTE: the sorted map returned by this method is limited in its
	** functionality, and will throw {@link UnsupportedOperationException} for
	** most of its methods. For details, see {@link UnwrappingSortedSubMap}.
	*/
	@Override public SortedMap<K, V> headMap(K to) {
		return new UnwrappingSortedSubMap<K, V>(skmap.headMap(to));
	}

	/**
	** {@inheritDoc}
	**
	** NOTE: the sorted map returned by this method is limited in its
	** functionality, and will throw {@link UnsupportedOperationException} for
	** most of its methods. For details, see {@link UnwrappingSortedSubMap}.
	*/
	@Override public SortedMap<K, V> tailMap(K fr) {
		return new UnwrappingSortedSubMap<K, V>(skmap.tailMap(fr));
	}


	/*========================================================================
	  public class Object
	 ========================================================================*/

	@Override public boolean equals(Object o) {
		if (o instanceof SkeletonTreeMap) {
			return skmap.equals(((SkeletonTreeMap<K, V>)o).skmap);
		}
		return super.equals(o);
	}

	/* provided by AbstractMap
	@Override public int hashCode() {
		throw new UnsupportedOperationException("not implemented");
	}*/

	@Override public Object clone() {
		return new SkeletonTreeMap<K, V>(this);
	}

	// public String toString() { return super.toString(); }


	/************************************************************************
	** {@link Iterator} that will throw {@link DataNotLoadedException} when it
	** encounters such data. Precise behaviour:
	**
	** * The key iterator will never throw an exception
	** * The entry iterator will never throw an exception, but the {@link
	**   UnwrappingEntry#getValue()} method of the entry will throw one if
	**   data is not loaded at the time of the method call. This allows you to
	**   load ''and retrieve'' the data during the middle of an iteration, by
	**   polling the {@code getValue()} method of the entry.
	** * The value iterator will throw an exception if the value is not loaded,
	**   and keep throwing this exception for subsequent method calls, pausing
	**   the iteration until the value is loaded.
	**
	** The {@link #remove()} method will set the {@link #ghosts} field
	** correctly as long as (and only if) access to (the {@link SkeletonValue}
	** corresponding to the item being removed) is either synchronized, or
	** contained within one thread.
	**
	** @author infinity0
	*/
	protected class UnwrappingIterator<T> implements Iterator<T> {

		final protected Iterator<Map.Entry<K, SkeletonValue<V>>> iter;

		/**
		** Last entry returned by the backing iterator. Used by {@link
		** #remove()} to update the {@link #ghosts} counter correctly.
		*/
		protected Map.Entry<K, SkeletonValue<V>> last;

		final protected static int KEY = 0;
		final protected static int VALUE = 1;
		final protected static int ENTRY = 2;

		protected boolean gotvalue;

		/**
		** Type of iterator.
		**
		** ;{@link #KEY} : Key iterator
		** ;{@link #VALUE} : Value iterator
		** ;{@link #ENTRY} : Entry iterator
		*/
		final protected int type;

		/**
		** Construct an iterator backed by the given iterator over the entries
		** of the backing map.
		**
		** @param it The backing iterator
		** @param t The {@link #type} of iterator
		*/
		protected UnwrappingIterator(Iterator<Map.Entry<K, SkeletonValue<V>>> it, int t) {
			assert(t == KEY || t == VALUE || t == ENTRY);
			type = t;
			iter = it;
		}

		public boolean hasNext() {
			return iter.hasNext();
		}

		public T next() {
			switch (type) {
			case KEY:
				last = iter.next();
				return (T)last.getKey();
			case VALUE:
				if (last == null || gotvalue) { last = iter.next(); }
				SkeletonValue<V> skel = last.getValue();
				if (!(gotvalue = skel.isLoaded)) {
					throw new DataNotLoadedException("SkeletonTreeMap: Data not loaded for key " + last.getKey() + ": " + skel.meta, SkeletonTreeMap.this, last.getKey(), skel.meta);
				}
				return (T)skel.data;
			case ENTRY:
				last = iter.next();
				return (T)new UnwrappingEntry(last);
			}
			throw new AssertionError();
		}

		public void remove() {
			if (last == null) { throw new IllegalStateException("Iteration has not yet begun, or the element has already been removed."); }
			if (!last.getValue().isLoaded) { --ghosts; }
			iter.remove();
			last = null;
		}


		/************************************************************************
		** {@link java.util.Map.Entry} backed by one whose value is wrapped inside a {@link
		** SkeletonValue}. This class will unwrap the value when it is required,
		** throwing {@link DataNotLoadedException} as necessary.
		**
		** @author infinity0
		*/
		protected class UnwrappingEntry implements Map.Entry<K, V> {

			final K key;
			final SkeletonValue<V> skel;

			protected UnwrappingEntry(Map.Entry<K, SkeletonValue<V>> en) {
				key = en.getKey();
				skel = en.getValue();
			}

			protected void verifyLoaded() {
				if (!skel.isLoaded) {
					throw new DataNotLoadedException("SkeletonTreeMap: Data not loaded for key " + key + ": " + skel.meta, SkeletonTreeMap.this, key, skel.meta);
				}
			}

			public K getKey() {
				return key;
			}

			public V getValue() {
				verifyLoaded();
				return skel.data;
			}

			/**
			** See also TODO note for {@link SkeletonTreeMap#put(Object,
			** Object)}.
			*/
			public V setValue(V value) {
				if (!skel.isLoaded) { --ghosts; }
				return skel.set(value);
			}

			@Override public int hashCode() {
				verifyLoaded();
				return (key==null? 0: key.hashCode()) ^
				       (skel.data==null? 0: skel.data.hashCode());
			}

			@Override public boolean equals(Object o) {
				if (!(o instanceof Map.Entry)) { return false; }
				verifyLoaded();
				Map.Entry<K, V> en = (Map.Entry<K, V>)o;
				return (key==null? en.getKey()==null: key.equals(en.getKey())) &&
				       (skel.data==null? en.getValue()==null: skel.data.equals(en.getValue()));
			}

		}

	}


	/************************************************************************
	** Submap of a {@link SkeletonTreeMap}. Currently, this only implements the
	** {@link #firstKey()}, {@link #lastKey()} and {@link #isEmpty()} methods.
	** This is because it's a bitch to implement an entire {@link SortedMap},
	** and I only needed those three methods for {@link SkeletonBTreeMap} to
	** work. Feel free to expand it.
	**
	** @author infinity0
	*/
	protected static class UnwrappingSortedSubMap<K, V>
	extends AbstractMap<K, V>
	implements Map<K, V>, SortedMap<K, V> {

		final SortedMap<K, SkeletonValue<V>> bkmap;

		protected UnwrappingSortedSubMap(SortedMap<K, SkeletonValue<V>> sub) {
			bkmap = sub;
		}

		/*@Override**/ public Comparator<? super K> comparator() {
			return bkmap.comparator();
		}

		@Override public boolean isEmpty() {
			return bkmap.isEmpty();
		}

		/*@Override**/ public K firstKey() {
			return bkmap.firstKey();
		}

		/*@Override**/ public K lastKey() {
			return bkmap.lastKey();
		}

		@Override public Set<Map.Entry<K, V>> entrySet() {
			throw new UnsupportedOperationException("not implemented");
		}

		/**
		** {@inheritDoc}
		**
		** NOTE: the sorted map returned by this method has the {@link
		** UnwrappingSortedSubMap same limitations} as this class.
		*/
		/*@Override**/ public SortedMap<K, V> subMap(K fr, K to) {
			return new UnwrappingSortedSubMap<K, V>(bkmap.subMap(fr, to));
		}

		/**
		** {@inheritDoc}
		**
		** NOTE: the sorted map returned by this method has the {@link
		** UnwrappingSortedSubMap same limitations} as this class.
		*/
		/*@Override**/ public SortedMap<K, V> headMap(K to) {
			return new UnwrappingSortedSubMap<K, V>(bkmap.headMap(to));
		}

		/**
		** {@inheritDoc}
		**
		** NOTE: the sorted map returned by this method has the {@link
		** UnwrappingSortedSubMap same limitations} as this class.
		*/
		/*@Override**/ public SortedMap<K, V> tailMap(K fr) {
			return new UnwrappingSortedSubMap<K, V>(bkmap.tailMap(fr));
		}

	}

}
