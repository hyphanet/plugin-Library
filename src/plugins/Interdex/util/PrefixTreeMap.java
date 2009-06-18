/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.PrefixTree.PrefixKey;

import java.util.TreeMap;
import java.util.AbstractMap;
import java.util.Map;
//import java.util.SortedMap;
//import java.util.NavigableSet;
//import java.util.NavigableMap;
import java.util.Set;
import java.util.Collection;

/**
** Implementation of PrefixTree backed by a Multimap.
**
** TODO: make this implement SortedMap
**
** @author infinity0
*/
public class PrefixTreeMap<K extends PrefixKey, V> extends PrefixTree<K, V>
implements Map<K, V>/*, SortedMap<K,V>, NavigableMap<K,V>
/*, ConcurrentMap<K,V>, ConcurrentNavigableMap<K,V>
/*, Cloneable, Serializable*/ {

	/**
	** TreeMap holding the entries which don't need to be stored in their own
	** tree yet.
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

	protected Set<K> keySetLocal() {
		return tmap.keySet();
	}

	protected int sizeLocal() {
		return tmap.size();
	}

	protected void transferLocalToSubtree(int i, K key) {
		child[i].put(key, tmap.remove(key));
	}

	protected void transferSubtreeToLocal(PrefixTree<K, V> ch) {
		tmap.putAll(((PrefixTreeMap)ch).tmap);
	}

	protected Map<K, V> selectNode(int i) {
		return (child[i] == null)? tmap: child[i];
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

	public boolean equals(Object o) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public V get(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return null; }

		int i = k.get(preflen);
		Map<K, V> map = selectNode(i);
		return map.get(k);
	}

	public int hashCode() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public boolean isEmpty() {
		if (!tmap.isEmpty()) { return false; }
		for (PrefixTreeMap<K, V> t: child) {
			if (!t.isEmpty()) { return false; }
		}
		return true;
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

		if (map.size() == s) { return v; } // size hasn't changed, do nothing
		++sizePrefix[i]; ++size;

		reshuffleAfterPut(i);
		return v;
	}

	public void putAll(Map<? extends K,? extends V> t) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public V remove(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return null; }

		int i = k.get(preflen);
		Map<K, V> map = selectNode(i);
		int s = map.size();
		V v = map.remove(key);

		if (map.size() == s) { return v; } // size hasn't changed, do nothing
		--sizePrefix[i]; --size;

		reshuffleAfterRemove(i);
		return v;
	}

	public int size() {
		return size;
	}

	public Collection<V> values() {
		throw new UnsupportedOperationException("Not implemented.");
	}

}
