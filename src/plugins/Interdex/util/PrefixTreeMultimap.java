/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.PrefixTree.PrefixKey;

import com.google.common.collect.TreeMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
//import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.Multiset;

import java.util.Map;
import java.util.Set;
import java.util.Collection;

/**
** Implementation of {@link PrefixTree} backed by a {@link TreeMultimap}.
**
** TODO: make this implement SortedSetMultiMap
**
** TODO: starting to think this class is useless because:
** - the premise of PrefixTreeMap is to balance out keys, regardless of how
**   many values each key maps to
** - the tree-balancing algorithm assumes that the tree will not run out of
**   space to put keys, but this doesn't hold for multimaps (eg. if there are
**   >capLocal values for a key, we won't be able to create a subtrees to hold
**   it, since we don't split values for the same key over different nodes)
** - the above dilemma is sufficiently well-handled by SplitMapSerialiser
**
** Changed sizeLocal() to report the number of keys - might be useful again?
**
** @author infinity0
*/
public class PrefixTreeMultimap<K extends PrefixKey, V extends Comparable>
extends PrefixTree<K, V>
implements SetMultimap<K, V>/*, SortedSetMultimap<K,V>,
/*, Cloneable, Serializable*/ {

	/**
	** {@link TreeMultimap} holding the entries which don't need to be stored
	** in their own tree yet.
	*/
	final protected TreeMultimap<K, V> tmap;

	/**
	** The constructor points this to {@link PrefixTree#child}, so we don't
	** have to keep casting when we want to access the methods of the subclass.
	*/
	final protected PrefixTreeMultimap<K, V>[] child;

	protected PrefixTreeMultimap(K p, int len, int caplocal, TreeMultimap<K, V> tm, PrefixTreeMultimap<K, V>[] chd) {
		super(p, len, caplocal, chd);
		if (tm.size() + subtrees > caplocal) {
			throw new IllegalArgumentException("The TreeMultimap being attached is too big (> " + (caplocal-subtrees) + ")");
		}

		if (tm == null) {
			tmap = TreeMultimap.create();
		} else {
			tmap = tm;
		}
		//tmap = (tm == null)? TreeMultimap.create(): tm; // java sucks, so this doesn't work
		child = (PrefixTreeMultimap<K, V>[])super.child;
	}

	public PrefixTreeMultimap(K p, int len, int caplocal) {
		this(p, len, caplocal, null, (PrefixTreeMultimap<K, V>[])new PrefixTreeMultimap[p.symbols()]);
	}

	public PrefixTreeMultimap(K p, int caplocal) {
		this(p, 0, caplocal);
	}

	public PrefixTreeMultimap(K p) {
		this(p, 0, p.symbols());
	}

	/*========================================================================
	  public class PrefixTree
	 ========================================================================*/

	@Override protected PrefixTreeMultimap<K, V> makeSubTree(int msym) {
		return new PrefixTreeMultimap<K, V>((K)prefix.spawn(preflen, msym), preflen+1, capacityLocal);
	}

	@Override protected void transferLocalToSubtree(int i, K key) {
		child[i].putAll(key, tmap.removeAll(key));
	}

	@Override protected void transferSubtreeToLocal(PrefixTree<K, V> ch) {
		tmap.putAll(((PrefixTreeMultimap<K, V>)ch).tmap);
	}

	@Override protected SetMultimap<K, V> selectNode(int i) {
		return (child[i] == null)? tmap: child[i];
	}

	@Override protected TreeMultimap<K, V> getLocalMap() {
		return tmap;
	}

	@Override protected void clearLocal() {
		tmap.clear();
	}

	@Override protected Set<K> keySetLocal() {
		return tmap.keySet();
	}

	@Override public int sizeLocal() {
		return tmap.keySet().size();
	}

	/*========================================================================
	  public interface Multimap
	 ========================================================================*/

	@Override public Map<K, Collection<V>> asMap() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override public boolean containsEntry(Object key, Object value) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return false; }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		return map.containsEntry(k, value);
	}

	@Override public boolean containsKey(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return false; }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		return map.containsKey(k);
	}

	@Override public boolean containsValue(Object value) {
		if (tmap.containsValue(value)) { return true; }
		for (PrefixTreeMultimap<K, V> ch: child) {
			if (ch != null && ch.containsValue(value)) { return true; }
		}
		return false;
	}

	@Override public Set<V> get(K key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return null; }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		return map.get(k);
	}

	@Override public boolean put(K key, V value) {
		if (!key.match(prefix, preflen)) {
			throw new IllegalArgumentException("Key does not match prefix for this tree.");
		}

		int i = key.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		boolean v = map.put(key, value);

		if (v) { reshuffleAfterPut(i); }
		return v;
	}

	@Override public boolean putAll(K key, Iterable<? extends V> values) {
		if (!key.match(prefix, preflen)) {
			throw new IllegalArgumentException("Key does not match prefix for this tree.");
		}

		int i = key.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		int prevsz = map.size();
		boolean v = map.putAll(key, values);

		if (v) { reshuffleAfterPut(i, map.size()-prevsz); }
		return v;
	}

	@Override public boolean putAll(Multimap<? extends K,? extends V> multimap) {
		boolean changed = false;
		Multimap<K, V> mmap = (Multimap<K, V>)multimap;
		for (K k : mmap.keySet()) {
			// adding each collection all at once is more efficient than adding each
			// entry separately, since then we can use reshuffleAfterPut(int i, int s);
			Collection<V> vs = mmap.get(k);
			changed |= putAll(k, vs);
		}
		return changed;
	}

	@Override public boolean remove(Object key, Object value) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return false; }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		boolean v = map.remove(key, value);

		if (v) { reshuffleAfterRemove(i); }
		return v;
	}

	@Override public Set<V> removeAll(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return new java.util.TreeSet(tmap.valueComparator()); }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		Set<V> vs = map.removeAll(key);

		if (vs.size() > 0) { reshuffleAfterRemove(i, vs.size()); }
		return vs;
	}

	@Override public Set<V> replaceValues(K key, Iterable<? extends V> values) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return new java.util.TreeSet(tmap.valueComparator()); }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		int prevsz = map.size();
		Set<V> vs = map.replaceValues(key, values);
		int newsz = map.size();

		if (prevsz > newsz) { reshuffleAfterRemove(i, prevsz-newsz); }
		else if (prevsz < newsz) { reshuffleAfterPut(i, newsz-prevsz); }
		return vs;
	}

	@Override public Set<Map.Entry<K,V>> entries() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override public Multiset<K> keys() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override public Set<K> keySet() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override public Collection<V> values() {
		throw new UnsupportedOperationException("Not implemented.");
	}

}
