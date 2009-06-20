/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.PrefixTree.PrefixKey;

import java.util.TreeMap;
import java.util.Map;
//import java.util.SortedMap;
//import java.util.NavigableSet;
//import java.util.NavigableMap;
import java.util.Set;
import java.util.Collection;

/**
** Implementation of {@link PrefixTree} backed by a {@link TreeMap}.
**
** TODO: make this implement SortedMap
**
** @author infinity0
*/
public class PrefixTreeMap<K extends PrefixKey, V> extends PrefixTree<K, V>
implements Map<K, V>/*, SortedMap<K,V>, NavigableMap<K,V>
/*, Cloneable, Serializable*/ {

	/**
	** {@link TreeMap} holding the entries which don't need to be stored in
	** their own tree yet.
	*/
	final TreeMap<K, V> tmap;

	final PrefixTreeMap<K, V> parent;
	final PrefixTreeMap<K, V>[] child;

	protected PrefixTreeMap(K p, int len, int maxsz, TreeMap<K, V> tm, PrefixTreeMap<K, V>[] chd, PrefixTreeMap<K, V> par) {
		super(p, len, maxsz, chd, par);
		if (tm.size() + subtrees > maxsz) {
			throw new IllegalArgumentException("The TreeMap being attached is too big (> " + (maxsz-subtrees) + ")");
		}

		tmap = tm;
		parent = (PrefixTreeMap<K, V>)super.parent;
		child = (PrefixTreeMap<K, V>[])super.child;
	}

	public PrefixTreeMap(K p, int len, int maxsz, PrefixTreeMap<K, V> par) {
		this(p, len, maxsz, new TreeMap<K, V>(), (PrefixTreeMap<K, V>[])new PrefixTreeMap[p.symbols()], par);
	}

	public PrefixTreeMap(K p, int maxsz) {
		this(p, 0, maxsz, null);
	}

	public PrefixTreeMap(K p) {
		this(p, 0, p.symbols(), null);
	}

	/************************************************************************
	 * public class PrefixTree
	 ************************************************************************/

	protected PrefixTreeMap<K, V> makeSubTree(int msym) {
		return new PrefixTreeMap<K, V>((K)prefix.spawn(preflen, msym), preflen+1, sizeMax, this);
	}

	protected void transferLocalToSubtree(int i, K key) {
		child[i].put(key, tmap.remove(key));
	}

	protected void transferSubtreeToLocal(PrefixTree<K, V> ch) {
		tmap.putAll(((PrefixTreeMap<K, V>)ch).tmap);
	}

	protected Map<K, V> selectNode(int i) {
		return (child[i] == null)? tmap: child[i];
	}

	protected TreeMap<K, V> getLocalMap() {
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
	 * public interface Map
	 ************************************************************************/

	public boolean containsKey(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return false; }

		int i = k.get(preflen);
		Map<K, V> map = selectNode(i);
		return map.containsKey(k);
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

	public V get(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return null; }

		int i = k.get(preflen);
		Map<K, V> map = selectNode(i);
		return map.get(k);
	}

	public Set<K> keySet() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public V put(K key, V value) {
		if (!key.match(prefix, preflen)) {
			throw new IllegalArgumentException("Key does not match prefix for this tree.");
		}

		int i = key.get(preflen);
		Map<K, V> map = selectNode(i);
		int s = map.size();
		V v = map.put(key, value);

		if (map.size() != s) { reshuffleAfterPut(i); }
		return v;
	}

	public void putAll(Map<? extends K,? extends V> t) {
		for (Map.Entry<K, V> e: ((Map<K, V>)t).entrySet()) {
			// TODO implement entrySet for PrefixTreeMap
			put(e.getKey(), e.getValue());
		}
	}

	public V remove(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return null; }

		int i = k.get(preflen);
		Map<K, V> map = selectNode(i);
		int s = map.size();
		V v = map.remove(key);

		if (map.size() != s) { reshuffleAfterRemove(i); }
		return v;
	}

	public Collection<V> values() {
		throw new UnsupportedOperationException("Not implemented.");
	}

}
