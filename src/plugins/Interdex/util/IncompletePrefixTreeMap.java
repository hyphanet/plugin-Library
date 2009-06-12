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
** it encounters dummy objects such as DummyPrefixTreeMap. This data structure
** can be populated without the node population restrictions of PrefixTreeMap
** via the constructors and assimilate().
**
** This object can be safely casted to a PrefixTreeMap if isComplete() returns
** true, which occurs if and only if it contains no IncompleteTreeMap or
** DummyPrefixTreeMap objects, and it follows the node population restrictions
** of a PrefixTreeMap.
**
** @author infinity0
*/
public class IncompletePrefixTreeMap<K extends PrefixTreeMap.PrefixKey, V> extends PrefixTreeMap<K, V>
implements IncompleteMap<K, V> {

	/**
	** Represents a PrefixTreeMap which has not been loaded, but which a parent
	** IncompletePrefixTreeMap (that has been loaded) refers to.
	**
	** @author infinity0
	*/
	public static class DummyPrefixTreeMap<K extends PrefixTreeMap.PrefixKey, V> extends PrefixTreeMap<K, V>
	implements IncompleteMap<K, V> {

		public DummyPrefixTreeMap(K p, int len, int maxsz) {
			super(p, len, maxsz);
		}

		public DummyPrefixTreeMap(K p, int maxsz) {
			super(p, 0, maxsz);
		}

		public DummyPrefixTreeMap(K p) {
			super(p, 0, p.symbols());
		}

		/************************************************************************
		 * public interface Map
		 ************************************************************************/

		public void clear() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public boolean containsKey(Object key) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public boolean containsValue(Object value) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public Set<Map.Entry<K,V>> entrySet() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public boolean equals(Object o) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public V get(Object key) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public int hashCode() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public boolean isEmpty() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public Set<K> keySet() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public V put(K key, V value) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public void putAll(Map<? extends K,? extends V> t) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public V remove(Object key) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public int size() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public Collection<V> values() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}

		/************************************************************************
		 * public interface IncompleteMap
		 ************************************************************************/

		public boolean isComplete() {
			return false;
		}

		public Map<K, V> complete() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}

	}

	/**
	** The constructor points this to the same object as tmap, so we don't have
	** to keep casting tmap when we want to access IncompleteTreeMap methods.
	*/
	final IncompleteTreeMap<K, V> itmap;

	public IncompletePrefixTreeMap(K p, int len, int maxsz, boolean[] chd, K[] keys, V[] values) {
		this(p, len, maxsz);
		// TODO make this take into account all the non-transient check vars of PrefixTreeMap
		putDummyChildren(chd);
		putDummySubmap(keys, values);
	}

	public IncompletePrefixTreeMap(K p, int len, int maxsz, int[] chd, K[] keys, V[] values) {
		this(p, len, maxsz);
		putDummyChildren(chd);
		putDummySubmap(keys, values);
	}

	public IncompletePrefixTreeMap(K p, int len, int maxsz) {
		super(p, len, maxsz, new IncompleteTreeMap<K, V>(), (PrefixTreeMap<K, V>[])new PrefixTreeMap[p.symbols()]);
		itmap = (IncompleteTreeMap<K, V>)tmap;
	}

	public IncompletePrefixTreeMap(K p, int maxsz) {
		this(p, 0, maxsz);
	}

	public IncompletePrefixTreeMap(K p) {
		this(p, 0, p.symbols());
	}

	/**
	** Puts DummyPrefixTreeMap objects into the child array.
	**
	** @param chd An array of booleans indicating whether to attach a dummy
	** @throws ArrayIndexOutOfBoundsException when the input array is smaller
	**         than the child array
	*/
	public void putDummyChildren(boolean[] chd) {
		for (int i=0; i<child.length; ++i) {
			if (chd[i]) { putDummyChild(i); }
		}
	}

	/**
	** Puts DummyPrefixTreeMap objects into the child array.
	**
	** @param chd An array of ints indicating the indexes to attach a dummy to
	*/
	public void putDummyChildren(int[] chd) {
		for (int i: chd) {
			putDummyChild(i);
		}
	}

	/**
	** Put a DummyPrefixTreeMap object into the child array.
	**
	** @param i The index to attach the dummy to
	*/
	public void putDummyChild(int i) {
		K newprefix = (K)prefix.clone();
		newprefix.set(preflen, i);
		child[i] = new DummyPrefixTreeMap(newprefix, preflen+1, sizeMax);
		++subtrees;
	}

	/**
	** Put dummy mappings onto the submap.
	**
	** @param keys The array of keys of the map
	** @param values The array of values of the map
	*/
	public void putDummySubmap(K[] keys, V[] values) {
		for (int i=0; i<keys.length; ++i) {
			itmap.putDummy(keys[i], values[i]);
		}
	}

	/**
	** Assimilate an existing IncompletePrefixTreeMap into this one. The prefix
	** must match and there must already be a DummyPrefixTreeMap in its place
	** in the child array.
	**
	** @param subtree The tree to assimilate
	*/
	public void assimilate(IncompletePrefixTreeMap<K, V> t) {
		if (t.preflen <= preflen) {
			throw new IllegalArgumentException("Only subtrees can be spliced onto an IncompletePrefixTreeMap.");
		}
		if (!t.prefix.match(prefix, preflen)) {
			throw new IllegalArgumentException("Key does not match prefix for this tree.");
		}

		int i = t.prefix.get(preflen);
		if (child[i] == null) {
			throw new IllegalArgumentException("This tree does not have a subtree with prefix " + t.prefix);
		}
		if (child[i] instanceof DummyPrefixTreeMap) {
			child[i] = t;
		} else if (child[i] instanceof IncompletePrefixTreeMap) {
			if (t.preflen > child[i].preflen) {
				((IncompletePrefixTreeMap)child[i]).assimilate(t);
			} else {
				// t.preflen == child.preflen since t.preflen > this.preflen
				throw new IllegalArgumentException("This tree has already assimilated a subtree with prefix " + t.prefix);
			}
		}
	}

	/************************************************************************
	 * public interface IncompleteMap
	 ************************************************************************/

	public boolean isComplete() {
		if (!itmap.isComplete()) { return false; }
		for (PrefixTreeMap t: child) {
			if (t instanceof DummyPrefixTreeMap) { return false; }
			if (t instanceof IncompletePrefixTreeMap && !((IncompletePrefixTreeMap)t).isComplete()) {
				return false;
			}
		}
		// TODO verify node population restrictions
		// int space = sizeMax - subtrees;
		return true;
	}

	public Map<K, V> complete() {
		if (!isComplete()) {
			throw new DataNotLoadedException("PrefixTreeMap not fully loaded for " + prefix.toString(), this, prefix);
		} else {
			TreeMap<K, V> ntmap = (TreeMap<K, V>)itmap.complete();
			PrefixTreeMap<K, V>[] nchild = (PrefixTreeMap<K, V>[])new PrefixTreeMap[subtreesMax];

			for (int i=0; i<subtreesMax; ++i) {
				if (child[i] != null) {
					nchild[i] = (PrefixTreeMap<K, V>)((IncompletePrefixTreeMap<K, V>)child[i]).complete();
				}
			}

			return new PrefixTreeMap(prefix, preflen, sizeMax, ntmap, nchild);
		}
	}

}
