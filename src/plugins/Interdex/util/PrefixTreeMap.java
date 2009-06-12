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
	public interface PrefixKey extends Cloneable, Comparable {

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

		public int compareTo(Object o) throws ClassCastException {
			PrefixKey p = (PrefixKey) o;
			int a = size();
			int b = p.size();
			int c = (a < b)? a: b;
			for (int i=0; i<c; ++i) {
				int x = get(i);
				int y = p.get(i);
				if (x != y) { return x-y; }
			}
			return a-b;
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
	final TreeMap<K, V> tmap;

	/**
	** Maximum size of (tmap + child) before we start to create subtrees.
	*/
	final int sizeMax;

	/**
	** Number of subtrees. At all times, this should equal the number of
	** non-null members of the child array.
	*/
	protected int subtrees = 0;

	/**
	** Number of elements contained in the tree. At all times, it should equal
	** the sum of the size of the subtrees, plus the size of the local tmap.
	*/
	protected int size = 0;

	/**
	** Counts the number of elements with the next element of the prefix being
	** the index into the array. At all times, the sum of all array elements
	** should equal the size field. Additionally,
	** for all i: (child[i] != null) implies (sizePrefix[i] == child[i].size())
	** and tmap.size() == sum{ sizePrefix[j] : child[j] == null }
	*/
	final protected int sizePrefix[];

	/**
	** Cache for the smallestChild. At all times, this should either be null,
	** or point to the subtree with the smallest size. If null, then either
	** there are no subtrees (check subtrees == 0) or the cache has been
	** invalidated.
	*/
	transient protected PrefixTreeMap<K, V> smallestChild_ = null;


	protected PrefixTreeMap(K p, int len, int maxsz, TreeMap<K, V> tm, PrefixTreeMap<K, V>[] chd) {
		if (tm.size() > maxsz) {
			throw new IllegalArgumentException("The TreeMap being attached has too many children (> " + maxsz + ")");
		}
		if (chd.length != p.symbols()) {
			throw new IllegalArgumentException("The child array must be able to exactly hold all its potential children, of which there are " + p.symbols());
		}
		if (maxsz < p.symbols()) {
			throw new IllegalArgumentException("This tree must be able to hold all its potential children, of which there are " + p.symbols());
		}
		if (len > p.size()) {
			throw new IllegalArgumentException("The key is shorter than the length specified.");
		}

		for (PrefixTreeMap<K, V> c: chd) {
			if (c != null) { ++subtrees; }
		}

		prefix = p;
		prefix.prefix(len);
		preflen = len;

		tmap = tm;
		subtreesMax = p.symbols();
		child = chd;
		sizePrefix = new int[child.length];

		sizeMax = maxsz;
	}

	public PrefixTreeMap(K p, int len, int maxsz) {
		this(p, len, maxsz, new TreeMap<K, V>(), (PrefixTreeMap<K, V>[])new PrefixTreeMap[p.symbols()]);
	}

	public PrefixTreeMap(K p, int maxsz) {
		this(p, 0, maxsz);
	}

	public PrefixTreeMap(K p) {
		this(p, 0, p.symbols());
	}

	/**
	** Returns the value at the last significant index of the prefix
	*/
	public int lastIndex() {
		return prefix.get(preflen-1);
	}

	/**
	** Build a subtree from the largest subset of tmap consisting of (keys that
	** share a common prefix of length one greater than that of this tree).
	**
	** For efficiency, this method *assumes* that such a set exists and is
	** non-empty; it is up to the calling code to make sure this is true.
	**
	** @return The index of the new subtree.
	*/
	protected int bindSubTree() {
		assert(tmap.size() > 0);
		assert(subtrees < subtreesMax);

		K[] mkeys = (K[]) new Object[sizeMax];
		K[] ckeys = (K[]) new Object[sizeMax];
		int msym = 0;
		int csym = tmap.firstKey().get(preflen);
		int mi = 0;
		int ci = 0;

		// TODO make this use SortedMap methods after this class implements SortedMap
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

		// update cache
		if (smallestChild_ == null || child[msym].size() < smallestChild_.size()) {
			smallestChild_ = child[msym];
		}

		return msym;
	}

	/**
	** Put all entries of a subtree into this node, if there is enough space.
	** (If there is enough space, then this implies that the subtree itself has
	** no child trees, due to the size constraints in the constructor.)
	**
	** @param i Index of the subtree to free
	** @return Whether there was enough space, and the operation completed.
	*/
	protected boolean freeSubTree(int i) {
		if (tmap.size() + subtrees + child[i].size() - 1 <= sizeMax) {

			// cache invalidated due to child removal
			if (child[i] == smallestChild_) { smallestChild_ = null; }

			tmap.putAll(child[i].tmap);
			child[i] = null;
			--subtrees;
			return true;
		}
		return false;
	}

	/**
	** Returns the smallest child.
	*/
	protected PrefixTreeMap<K, V> smallestChild() {
		if (smallestChild_ == null && subtrees > 0) {
			for (PrefixTreeMap<K, V> ch: child) {
				if (ch != null && (smallestChild_ == null || ch.size() < smallestChild_.size())) {
					smallestChild_ = ch;
				}
			}
		}
		return smallestChild_;
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

	/**
	** Standard put operation on a map. This implementation puts the mapping
	** into the local tmap for this node, unless there is no space left (as
	** defined by sizeMax). In these cases, the algorithm will move some
	** entries into new subtrees, with big subtrees taking priority, until
	** there is enough space to fit the remaining entries. In other words, the
	** algorithm will ensure that there exists sz such that for all i:
	** (chd[i] == null) implies sizePrefix[i] <= sz
	** (chd[i] != null) implies sizePrefix[i] >= sz
	** smallestChild.size() == sz and tmap.size() + subtrees + sz > maxSize
	*/
	public V put(K key, V value) {
		if (!key.match(prefix, preflen)) {
			throw new IllegalArgumentException("Key does not match prefix for this tree.");
		}

		int i = key.get(preflen);
		Map<K, V> map = (child[i] == null)? tmap: child[i];

		int s = map.size();
		V v = map.put(key, value);
		if (map.size() == s) { return v; } // size hasn't changed, do nothing

		++sizePrefix[i]; ++size;

		if (child[i] != null) {
			// cache possibly invalidated due to put operation on child
			if (child[i] == smallestChild_) { smallestChild_ = null; }

		} else {

			if (tmap.size() + subtrees > sizeMax) {
				bindSubTree();
				// smallestChild is not null due to bindSubTree
				freeSubTree(smallestChild().lastIndex());

			} else {
				PrefixTreeMap<K, V> sm = smallestChild();
				if (sm != null && sm.size() < sizePrefix[i]) {
					// TODO: make a bindSubTree(i) method
					int j = bindSubTree();
					assert(i == j);
					freeSubTree(sm.lastIndex());
				}
			}
		}

		return v;
	}

	//public void putAll(Map<? extends K,? extends V> t);

	public V remove(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return null; }

		int i = k.get(preflen);
		Map<K, V> map = (child[i] == null)? tmap: child[i];

		int s = map.size();
		V v = map.remove(key);
		if (map.size() == s) { return v; } // size hasn't changed, do nothing

		--sizePrefix[i]; --size;

		if (child[i] != null) {

			if (sizePrefix[i] < smallestChild_.size()) {
				// we potentially have a new smallestChild, but wait and see...

				if (!freeSubTree(i)) {
					// if the tree wasn't freed then we have a new smallestChild
					smallestChild_ = child[i];
				}
			} else {
				// smallestChild is not null due to previous if block
				freeSubTree(smallestChild().lastIndex());
			}

		} else {
			PrefixTreeMap<K, V> t = smallestChild();
			if (t != null) { freeSubTree(t.lastIndex()); }
		}

		return v;
	}

	public int size() {
		return size;
	}

	//public Collection<V> values();

}
