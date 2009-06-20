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
** Implementation of PrefixTree backed by a Map.
**
** TODO: make this implement SortedSetMultiMap
**
** @author infinity0
*/
public class PrefixTreeMultimap<K extends PrefixKey, V extends Comparable>
extends PrefixTree<K, V>
implements SetMultimap<K, V>/*, SortedSetMultimap<K,V>,
/*, Cloneable, Serializable*/ {

	/**
	** TreeMultimap holding the entries which don't need to be stored in their own
	** tree yet.
	*/
	final TreeMultimap<K, V> tmap;

	final PrefixTreeMultimap<K, V> parent;
	final PrefixTreeMultimap<K, V>[] child;

	protected PrefixTreeMultimap(K p, int len, int maxsz, TreeMultimap<K, V> tm, PrefixTreeMultimap<K, V>[] chd, PrefixTreeMultimap<K, V> par) {
		super(p, len, maxsz, chd, par);
		if (tm.size() + subtrees > maxsz) {
			throw new IllegalArgumentException("The TreeMultimap being attached is too big (> " + (maxsz-subtrees) + ")");
		}

		if (tm == null) {
			tmap = TreeMultimap.create();
		} else {
			tmap = tm;
		}
		//tmap = (tm == null)? TreeMultimap.create(): tm; // java sucks, so this doesn't work
		parent = (PrefixTreeMultimap<K, V>)super.parent;
		child = (PrefixTreeMultimap<K, V>[])super.child;
	}

	public PrefixTreeMultimap(K p, int len, int maxsz, PrefixTreeMultimap<K, V> par) {
		this(p, len, maxsz, null, (PrefixTreeMultimap<K, V>[])new PrefixTreeMultimap[p.symbols()], par);
	}

	public PrefixTreeMultimap(K p, int maxsz) {
		this(p, 0, maxsz, null);
	}

	public PrefixTreeMultimap(K p) {
		this(p, 0, p.symbols(), null);
	}

	/************************************************************************
	 * public class PrefixTree
	 ************************************************************************/

	protected PrefixTreeMultimap<K, V> makeSubTree(int msym) {
		return new PrefixTreeMultimap<K, V>((K)prefix.spawn(preflen, msym), preflen+1, sizeMax, this);
	}

	protected void transferLocalToSubtree(int i, K key) {
		child[i].putAll(key, tmap.removeAll(key));
	}

	protected void transferSubtreeToLocal(PrefixTree<K, V> ch) {
		tmap.putAll(((PrefixTreeMultimap<K, V>)ch).tmap);
	}

	protected SetMultimap<K, V> selectNode(int i) {
		return (child[i] == null)? tmap: child[i];
	}

	protected TreeMultimap<K, V> getLocalMap() {
		return tmap;
	}

	protected void clearLocal() {
		tmap.clear();
	}

	protected Set<K> keySetLocal() {
		return tmap.keySet();
	}

	protected int sizeLocal() {
		return tmap.size();
	}


	/************************************************************************
	 * public interface Multimap
	 ************************************************************************/

	public Map<K, Collection<V>> asMap() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public boolean containsEntry(Object key, Object value) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return false; }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		return map.containsEntry(k, value);
	}

	public boolean containsKey(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return false; }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		return map.containsKey(k);
	}

	public boolean containsValue(Object value) {
		if (tmap.containsValue(value)) { return true; }
		for (PrefixTreeMultimap<K, V> t: child) {
			if (t.containsValue(value)) { return true; }
		}
		return false;
	}

	public Set<Map.Entry<K,V>> entries() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Set<V> get(K key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return null; }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		return map.get(k);
	}

	public Multiset<K> keys() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Set<K> keySet() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public boolean put(K key, V value) {
		if (!key.match(prefix, preflen)) {
			throw new IllegalArgumentException("Key does not match prefix for this tree.");
		}

		int i = key.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		boolean v = map.put(key, value);

		if (v) { reshuffleAfterPut(i); }
		return v;
	}

	public boolean putAll(K key, Iterable<? extends V> values) {
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

	public boolean putAll(Multimap<? extends K,? extends V> multimap) {
		//return putAllOhAndBTWThisMethodExistsBecauseJavaSucks(multimap); } private <J extends K, U extends V> boolean putAllOhAndBTWThisMethodExistsBecauseJavaSucks(Multimap<J,U> multimap) {
		// LA LA LA JAVA SUCKS LA LA LA
		// apparently public <J extends K, U extends V> boolean putAll(Multimap<J,U> multimap)
		// does not implement public boolean putAll(Multimap<? extends K,? extends V> multimap)
		// even though http://java.sun.com/j2se/1.5/pdf/generics-tutorial.pdf says
		// they do the same thing. :| facepalm.jpg
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

	public boolean remove(Object key, Object value) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return false; }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		boolean v = map.remove(key, value);

		if (v) { reshuffleAfterRemove(i); }
		return v;
	}

	public Set<V> removeAll(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return new java.util.TreeSet(tmap.valueComparator()); }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		Set<V> vs = map.removeAll(key);

		if (vs.size() > 0) { reshuffleAfterRemove(i, vs.size()); }
		return vs;
	}

	public Set<V> replaceValues(K key, Iterable<? extends V> values) {
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

	public Collection<V> values() {
		throw new UnsupportedOperationException("Not implemented.");
	}

}
