/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import java.util.TreeMap;
import java.util.AbstractMap;
import java.util.Map;
//import java.util.Comparator;
//import java.util.SortedMap;
//import java.util.NavigableSet;
//import java.util.NavigableMap;
//import java.util.ConcurrentMap;
//import java.util.ConcurrentNavigableMap;
import java.util.Set;
import java.util.Collection;

/**
** Trie-like map with each node mapping keys that match a given prefix. Each
** node in the tree will admit a certain maximum number of mappings before it
** starts creating subtrees.
**
** TODO: make this implement SortedMap
**
** @author infinity0
*/
public class PrefixTreeMap<K extends PrefixTreeMap.PrefixKey, V> extends AbstractMap<K, V>
implements Map<K, V>/*, SortedMap<K,V>, NavigableMap<K,V>
/*, ConcurrentMap<K,V>, ConcurrentNavigableMap<K,V>
/*, Cloneable, Serializable*/ {

	/**
	** Defines an interface that provides prefix-related operations, such as
	** returning the next component of a key,
	**
	** @author infinity0
	*/
	public interface PrefixKey extends Cloneable {

		public Object clone();

		/**
		** Returns the number of possible symbols at each cell of the key.
		*/
		public int symbols();

		/**
		** Returns the size of the key.
		*/
		public int size();

		/**
		** Gets one cell of the key.
		*/
		public int get(int i);

		/**
		** Sets one cell of the key.
		*/
		public void set(int i, int v);

		/**
		** Clears one cell of the key.
		*/
		public void clear(int i);

		/**
		** Returns a new key matching a given-sized prefix of this key, with
		** the other cells blank.
		**
		** @param len The number of cells to match
		*/
		public void prefix(int len);

		/**
		** Whether two keys have matching prefixes.
		**
		** @param p The key to match against
		** @param len Length of prefix to match
		*/
		public boolean match(PrefixKey p, int len);

	}

	/**
	** Provides implementations of various higher-level functionalities of
	** PrefixKey in terms of lower-level ones.
	**
	** @author infinity0
	*/
	abstract public static class AbstractPrefixKey implements PrefixKey {

		abstract public Object clone();

		public void prefix(int len) {
			for (int i=len; i<size(); ++i) { clear(i); }
		}

		public boolean match(PrefixKey p, int len) {
			for (int i=0; i<len; ++i) {
				if (get(i) != p.get(i)) { return false; }
			}
			return true;
		}

	}

	/**
	** The prefix that all keys in this tree must match.
	*/
	final K prefix;

	/**
	** The length of the prefix for this tree.
	*/
	final int preflen;

	/**
	** Array holding the child trees. There is one cell in the array for each
	** symbol in the alphabet of the key.
	*/
	final PrefixTreeMap<K, V>[] child; // prefix_bytes + i == child[i].prefix_bytes

	/**
	** The size of the child array.
	*/
	final int subtreesMax;

	/**
	** TreeMap holding the entries which don't need to be stored in their own
	** tree yet.
	*/
	final TreeMap<K, V> tmap = new TreeMap<K, V>();

	/**
	** Maximum size of the TreeMap before we start to create subtrees.
	*/
	final int sizeMax;

	/**
	** Number of subtrees. By definition it <= subtreesMax.
	*/
	int subtrees = 0;

	public PrefixTreeMap(K p, int len, int maxsz) {
		prefix = p;
		prefix.prefix(len);
		preflen = len;

		subtreesMax = p.symbols();
		child = (PrefixTreeMap<K, V>[])new PrefixTreeMap[subtreesMax];

		if (maxsz < subtreesMax) {
			throw new IllegalArgumentException("Tree must be able to hold all its potential children, of which there are " + subtreesMax);
		}
		sizeMax = maxsz;
	}
	public PrefixTreeMap(K p, int maxsz) {
		this(p, 0, maxsz);
	}
	public PrefixTreeMap(K p) {
		this(p, 0, p.symbols());
	}

	/**
	** Build a subtree from the largest subset of tmap consisting of (keys that
	** share a common prefix of length one greater than that of this tree).
	**
	** For efficiency, this method *assumes* that such a set exists and is
	** non-empty; it is up to the calling code to make sure this is correct.
	**
	** @return The index of the new subtree.
	*/
	private int makeSubTree() {
		assert(tmap.size() > 0);
		assert(subtrees < subtreesMax);

		K[] mkeys = (K[]) new Object[sizeMax];
		K[] ckeys = (K[]) new Object[sizeMax];
		int msym = 0;
		int csym = tmap.firstKey().get(preflen);
		int mi = 0;
		int ci = 0;

		for (K ikey: tmap.keySet()) {
			int isym = ikey.get(preflen);
			if (isym != csym) {
				if (ci > mi) {
					mkeys = ckeys;
					msym = csym;
					mi = ci;
					ckeys = (K[]) new Object[sizeMax];
				}
				csym = isym;
				ci = 0;
			}
			ckeys[ci] = ikey;
			++ci;
		}
		if (ci > mi) {
			mkeys = ckeys;
			msym = csym;
			mi = ci;
		}

		assert(child[msym] == null);
		K newprefix = (K)prefix.clone();
		newprefix.set(preflen, msym);
		child[msym] = new PrefixTreeMap<K, V>(newprefix, preflen+1, sizeMax);
		++subtrees;

		for (int i=0; i<mi; ++i) {
			child[msym].put(mkeys[i], tmap.remove(mkeys[i]));
		}

		return msym;
	}

	/************************************************************************
	 * public interface Map
	 ************************************************************************/

	public void clear() {
		for (int i=0; i<child.length; ++i) {
			child[i] = null;
		}
		subtrees = 0;
		tmap.clear();
	}

	public boolean containsKey(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return false; }

		int i = k.get(preflen);
		if (child[i] != null) {
			return child[i].containsKey(k);
		} else {
			return tmap.containsKey(k);
		}
	}

	public boolean containsValue(Object value) {
		if (tmap.containsValue(value)) { return true; }
		for (PrefixTreeMap<K, V> t: child) {
			if (t.containsValue(value)) { return true; }
		}
		return false;
	}

	public Set<Map.Entry<K,V>> entrySet() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	//public boolean equals(Object o);

	public V get(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return null; }

		int i = k.get(preflen);
		if (child[i] != null) {
			return child[i].get(k);
		} else {
			return tmap.get(k);
		}
	}

	//public int hashCode();

	public boolean isEmpty() {
		if (!tmap.isEmpty()) { return false; }
		for (PrefixTreeMap<K, V> t: child) {
			if (!t.isEmpty()) { return false; }
		}
		return true;
	}

	//public Set<K> keySet();

	public V put(K key, V value) {
		if (!key.match(prefix, preflen)) {
			throw new IllegalArgumentException("Key does not match prefix for this tree.");
		}

		int i = key.get(preflen);
		if (child[i] != null) {
			return child[i].put(key, value);
		} else if (tmap.size() < sizeMax - subtrees) {
			return tmap.put(key, value);
		} else {
			/* create a new tree. satisfying conditions:
			** - tmap.size >= sizeMax - subtrees (above else-if condition)
			** - sizeMax >= subtreesMax (constructor)
			** - subtreesMax >= subtrees (by definition)
			** so tmap.size >= 0, but tmap.size != 0 (otherwise we would be
			** executing the (child[i] != null) block). So tmap.size > 0.
			*/
			int j = makeSubTree();

			// same as the above
			if (i == j) {
				return child[i].put(key, value);
			} else {
				return tmap.put(key, value);
			}
		}
	}

	//public void putAll(Map<? extends K,? extends V> t);

	public V remove(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return null; }

		int i = k.get(preflen);
		if (child[i] != null) {
			V v = child[i].remove(k);

			if (child[i].subtrees == 0 &&
				tmap.size() + child[i].tmap.size() <= sizeMax - subtrees) {
				// remove the subtree since it is now small enough to fit here
				tmap.putAll(child[i].tmap);
				child[i] = null;
				--subtrees;
			}
			return v;

		} else {
			return tmap.remove(k);
		}
	}

	public int size() {
		// TODO: could be more efficient if we cache this in a "size" field
		// and detect changes to tmap and propagate this up the tree...
		int s = tmap.size();
		for (PrefixTreeMap<K, V> t: child) {
			s += t.size();
		}
		return s;
	}

	//public Collection<V> values();

}
