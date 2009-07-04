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
public class PrefixTreeMap<K extends PrefixKey, V>
extends PrefixTree<K, V>
implements Map<K, V>/*, SortedMap<K,V>, NavigableMap<K,V>
/*, Cloneable, Serializable*/ {

	/**
	** {@link TreeMap} holding the entries which don't need to be stored in
	** their own tree yet.
	*/
	final protected TreeMap<K, V> tmap;

	/**
	** The constructor points this to {@link PrefixTree#parent}, so we don't
	** have to keep casting when we want to access the methods of the subclass.
	*/
	final protected PrefixTreeMap<K, V> parent;

	/**
	** The constructor points this to {@link PrefixTree#child}, so we don't
	** have to keep casting when we want to access the methods of the subclass.
	*/
	final protected PrefixTreeMap<K, V>[] child;

	protected PrefixTreeMap(K p, int len, int caplocal, TreeMap<K, V> tm, PrefixTreeMap<K, V>[] chd, PrefixTreeMap<K, V> par) {
		super(p, len, caplocal, chd, par);
		if (tm.size() + subtrees > caplocal) {
			throw new IllegalArgumentException("The TreeMap being attached is too big (> " + (caplocal-subtrees) + ")");
		}

		tmap = tm;
		parent = (PrefixTreeMap<K, V>)super.parent;
		child = (PrefixTreeMap<K, V>[])super.child;
	}

	protected PrefixTreeMap(K p, int len, int caplocal, PrefixTreeMap<K, V> par) {
		this(p, len, caplocal, new TreeMap<K, V>(), (PrefixTreeMap<K, V>[])new PrefixTreeMap[p.symbols()], par);
	}

	public PrefixTreeMap(K p, int caplocal) {
		this(p, 0, caplocal, null);
	}

	public PrefixTreeMap(K p) {
		this(p, 0, p.symbols(), null);
	}

	/*========================================================================
	  public class PrefixTree
	 ========================================================================*/

	@Override protected PrefixTreeMap<K, V> makeSubTree(int msym) {
		return new PrefixTreeMap<K, V>((K)prefix.spawn(preflen, msym), preflen+1, capacityLocal, this);
	}

	@Override protected void transferLocalToSubtree(int i, K key) {
		child[i].put(key, tmap.remove(key));
	}

	@Override protected void transferSubtreeToLocal(PrefixTree<K, V> ch) {
		tmap.putAll(((PrefixTreeMap<K, V>)ch).tmap);
	}

	@Override protected Map<K, V> selectNode(int i) {
		return (child[i] == null)? tmap: child[i];
	}

	@Override protected TreeMap<K, V> getLocalMap() {
		return tmap;
	}

	@Override protected void clearLocal() {
		tmap.clear();
	}

	@Override protected Set<K> keySetLocal() {
		return tmap.keySet();
	}

	@Override public int sizeLocal() {
		return tmap.size();
	}

	/*========================================================================
	  public interface Map
	 ========================================================================*/

	@Override public boolean containsKey(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return false; }

		int i = k.get(preflen);
		Map<K, V> map = selectNode(i);
		return map.containsKey(k);
	}

	@Override public boolean containsValue(Object value) {
		if (tmap.containsValue(value)) { return true; }
		for (PrefixTreeMap<K, V> ch: child) {
			if (ch != null && ch.containsValue(value)) { return true; }
		}
		return false;
	}

	@Override public Set<Map.Entry<K,V>> entrySet() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override public V get(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return null; }

		int i = k.get(preflen);
		Map<K, V> map = selectNode(i);
		return map.get(k);
	}

	@Override public Set<K> keySet() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override public V put(K key, V value) {
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

	@Override public void putAll(Map<? extends K,? extends V> t) {
		for (Map.Entry<K, V> e: ((Map<K, V>)t).entrySet()) {
			// TODO need to implement entrySet for PrefixTreeMap
			// if we want to be able to merge two PTMs!
			put(e.getKey(), e.getValue());
		}
	}

	@Override public V remove(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return null; }

		int i = k.get(preflen);
		Map<K, V> map = selectNode(i);
		int s = map.size();
		V v = map.remove(key);

		if (map.size() != s) { reshuffleAfterRemove(i); }
		return v;
	}

	@Override public Collection<V> values() {
		throw new UnsupportedOperationException("Not implemented.");
	}

}
