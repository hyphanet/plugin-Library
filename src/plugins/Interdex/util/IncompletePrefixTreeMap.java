/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.PrefixTree.PrefixKey;

import java.util.TreeMap;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

import plugins.Interdex.util.Serialiser.SerialiseTask;
import plugins.Interdex.util.Serialiser.InflateTask;
import plugins.Interdex.util.Serialiser.DeflateTask;

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
** TODO pass Serialisers in the constructor
**
** @author infinity0
*/
public class IncompletePrefixTreeMap<K extends PrefixKey, V> extends PrefixTreeMap<K, V>
implements IncompleteMap<K, V> {

	/**
	** Represents a PrefixTreeMap which has not been loaded, but which a parent
	** IncompletePrefixTreeMap (that has been loaded) refers to.
	**
	** TODO make this contain a DummyValue
	**
	** @author infinity0
	*/
	public static class DummyPrefixTreeMap<K extends PrefixKey, V> extends PrefixTreeMap<K, V>
	implements IncompleteMap<K, V> {

		public DummyPrefixTreeMap(K p, int len, int maxsz, IncompletePrefixTreeMap<K, V> par) {
			super(p, len, maxsz, par);
		}

		public DummyPrefixTreeMap(K p, int maxsz) {
			super(p, 0, maxsz, null);
		}

		public DummyPrefixTreeMap(K p) {
			super(p, 0, p.symbols(), null);
		}

		/************************************************************************
		 * public interface Map
		 ************************************************************************/

		public void clear() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}
		public boolean containsKey(Object key) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}
		public boolean containsValue(Object value) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}
		public Set<Map.Entry<K,V>> entrySet() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}
		public boolean equals(Object o) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}
		public V get(Object key) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}
		public int hashCode() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}
		public boolean isEmpty() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}
		public Set<K> keySet() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}
		public V put(K key, V value) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}
		public void putAll(Map<? extends K,? extends V> t) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}
		public V remove(Object key) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}
		public int size() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}
		public Collection<V> values() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		/************************************************************************
		 * public interface IncompleteMap
		 ************************************************************************/

		public boolean isComplete() {
			return false;
		}

		public Map<K, V> complete() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public Object inflate() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public Object deflate() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public Object inflate(IncompleteMap<K, V> m) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public Object deflate(IncompleteMap<K, V> m) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public Object inflate(K key) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public Object deflate(K key) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public Serialiser getSerialiser() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public void setSerialiser(Serialiser s) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

	}

	/**
	** The constructor points this to the same object as tmap, so we don't have
	** to keep casting tmap when we want to access IncompleteTreeMap methods.
	*/
	final IncompleteTreeMap<K, V> itmap;

	public IncompletePrefixTreeMap(K p, int len, int maxsz, int sz, int subs, int[] szPre, boolean[] chd, K[] keys, V[] values, IncompletePrefixTreeMap<K, V> par) {
		this(p, len, maxsz, par);

		// check size == sum { sizePrefix }
		int s = 0;
		for (int pre: szPre) { s += pre; }
		if (sz != s) {
			throw new IllegalArgumentException("Invariant broken: size == sum{ sizePrefix }");
		}

		size = sz;
		subtrees = subs;
		for (int i=0; i<szPre.length; ++i) {
			sizePrefix[i] = szPre[i];
		}

		putDummyChildren(chd);
		putDummySubmap(keys, values);
	}

	public IncompletePrefixTreeMap(K p, int len, int maxsz, IncompletePrefixTreeMap<K, V> par) {
		super(p, len, maxsz, new IncompleteTreeMap<K, V>(), (PrefixTreeMap<K, V>[])new PrefixTreeMap[p.symbols()], par);
		itmap = (IncompleteTreeMap<K, V>)tmap;
	}

	public IncompletePrefixTreeMap(K p, int maxsz) {
		this(p, 0, maxsz, null);
	}

	public IncompletePrefixTreeMap(K p) {
		this(p, 0, p.symbols(), null);
	}

	/**
	** Puts DummyPrefixTreeMap objects into the child array.
	**
	** @param chd An array of booleans indicating whether to attach a dummy
	** @throws ArrayIndexOutOfBoundsException when the input array is smaller
	**         than the child array
	*/
	protected void putDummyChildren(boolean[] chd) {

		// check count{ i : chd[i] == true } == subtrees
		int s = 0;
		for (boolean b: chd) {
			if (b) { ++s; }
		}
		if (subtrees != s) {
			throw new IllegalArgumentException("Invariant broken: subtrees == count{ non-null children }");
		}

		// check that there exists sz such that for all i:
		// (chd[i] == false) => sizePrefix[i] <= sz
		// (chd[i] != false) => sizePrefix[i] >= sz
		int sz = sizeMax;
		// find the size of the smallest child. if the smallest child is larger
		// than maxSize, then maxSize will be returned instead, but this makes
		// no difference to the tests below (think about it...)
		for (int i=0; i<child.length; ++i) {
			if (chd[i]) {
				if (sizePrefix[i] < sz) {
					sz = sizePrefix[i];
				}
			}
		}
		// see if there are any non-child prefix groups larger than the
		// smallest child. whilst we're at it, calculate sum{ sizePrefix[j]:
		// chd[j] == false } for the next test.
		s = 0;
		for (int i=0; i<child.length; ++i) {
			if (!chd[i]) {
				s += sizePrefix[i];
				if (sizePrefix[i] > sz) {
					throw new IllegalArgumentException(
					"Invariant broken: there exists sz such that for all i: " +
					"(child[i] == null) => sizePrefix[i] <= sz and " +
					"(child[i] != null) => sizePrefix[i] >= sz"
					);
				}
			}
		}

		// check that sum{ sizePrefix[j] : chd[j] == false } + subtrees + sz > sizeMax
		if (s + subtrees + sz <= sizeMax) {
			throw new IllegalArgumentException("Invariant broken: count{ non-child prefix groups } + subtrees + sz > maxSize");
		}

		for (int i=0; i<child.length; ++i) {
			if (chd[i]) { putDummyChild(i); }
		}
	}

	/**
	** Put a DummyPrefixTreeMap object into the child array.
	**
	** @param i The index to attach the dummy to
	*/
	protected void putDummyChild(int i) {
		child[i] = new DummyPrefixTreeMap((K)prefix.spawn(preflen, i), preflen+1, sizeMax, this);
	}

	/**
	** Put dummy mappings onto the submap. This method carries out certain
	** tests which assume that the child array has already been populated by
	** putDummyChildren.
	**
	** @param keys The array of keys of the map
	** @param values The array of values of the map
	*/
	protected void putDummySubmap(K[] keys, V[] values) {
		if (keys.length != values.length) {
			throw new IllegalArgumentException("keys/values length mismatch");
		}

		// check that keys agrees with child[i]
		int[] szPre = new int[sizePrefix.length];
		for (int i=0; i<keys.length; ++i) {
			int p = keys[i].get(preflen);
			if (child[p] != null) {
				throw new IllegalArgumentException("A subtree already exists for this key: " + keys[i]);
			}
			++szPre[p];
		}

		// check keys.length == sum{ sizePrefix[j] : child[j] == null } and
		// that keys agrees with sizePrefix
		int sz = 0;
		for (int i=0; i<sizePrefix.length; ++i) {
			if (child[i] == null) {
				sz += szPre[i];
				if (sizePrefix[i] != szPre[i]) {
					throw new IllegalArgumentException("The keys given contradicts the sizePrefix array");
				}
			}
		}
		if (sz != keys.length) {
			throw new IllegalArgumentException("The keys given contradicts the sizePrefix array");
		}

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
		// check t.size == sizePrefix[i]
		if (t.size != sizePrefix[i]) {
			throw new IllegalArgumentException("The size of this tree contradicts the parent's sizePrefix");
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
	 * public class PrefixTreeMap
	 ************************************************************************/

	// We override this method so that the correct serialiser is set
	protected PrefixTreeMap<K, V> makeSubTree(int msym) {
		IncompletePrefixTreeMap<K, V> ch = new IncompletePrefixTreeMap<K, V>((K)prefix.spawn(preflen, msym), preflen+1, sizeMax, this);
		ch.setSerialiser(serialiser);
		return ch;
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
		return true;
	}

	public Map<K, V> complete() {
		if (!isComplete()) {
			throw new DataNotLoadedException("PrefixTreeMap not fully loaded for " + prefix.toString(), this, this);
		} else {
			TreeMap<K, V> ntmap = (TreeMap<K, V>)itmap.complete();
			PrefixTreeMap<K, V>[] nchild = (PrefixTreeMap<K, V>[])new PrefixTreeMap[subtreesMax];

			for (int i=0; i<subtreesMax; ++i) {
				if (child[i] != null) {
					nchild[i] = (PrefixTreeMap<K, V>)((IncompletePrefixTreeMap<K, V>)child[i]).complete();
				}
			}

			return new PrefixTreeMap(prefix, preflen, sizeMax, ntmap, nchild, null);
		}
	}

	public Object inflate() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Object deflate() {
		DeflateTask de = serialiser.newDeflateTask(this);
		de.put("prefix", prefix.toString());
		de.put("preflen", preflen);
		de.put("sizeMax", sizeMax);
		de.put("size", size);
		de.put("subtreesMax", subtreesMax);
		de.put("subtrees", subtrees);
		// TODO snakeYAML says "Arrays of primitives are not fully supported."
		//de.put("sizePrefix", sizePrefix);

		//boolean chd[] = new boolean[subtreesMax];
		//for (int i=0; i<subtreesMax; ++i) { chd[i] = (child[i] != null); }
		//de.put("_child", chd);
		de.put("_tmap", itmap.deflate());

		de.start();
		for (PrefixTreeMap<K, V> t: child) {
			if (t != null) { ((IncompletePrefixTreeMap) t).deflate(); }
		}
		return de.join();
	}

	public Object inflate(IncompleteMap<K, V> m) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Object deflate(IncompleteMap<K, V> m) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Object inflate(K key) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Object deflate(K key) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	private Serialiser serialiser;

	public Serialiser getSerialiser() {
		return serialiser;
	}

	public void setSerialiser(Serialiser s) {
		serialiser = s;
		itmap.setSerialiser(s);
		for (PrefixTreeMap<K, V> ch: child) {
			if (ch != null && ch instanceof IncompletePrefixTreeMap) {
				((IncompletePrefixTreeMap)ch).setSerialiser(s);
			}
		}
	}

}
