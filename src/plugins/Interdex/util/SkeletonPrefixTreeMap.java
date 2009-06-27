/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.PrefixTree.PrefixKey;

import plugins.Interdex.serl.Serialiser.*;
import plugins.Interdex.serl.Translator;
import plugins.Interdex.serl.IterableSerialiser;
import plugins.Interdex.serl.MapSerialiser;

import java.util.TreeMap;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

/**
** A {@link SkeletonMap} of a {@link PrefixTreeMap}.
**
** @author infinity0
*/
public class SkeletonPrefixTreeMap<K extends PrefixKey, V>
extends PrefixTreeMap<K, V>
implements SkeletonMap<K, V> {

	/************************************************************************
	**
	** Represents a PrefixTreeMap which has not been loaded, but which a parent
	** SkeletonPrefixTreeMap (that has been loaded) refers to.
	**
	** TODO make this contain a meta value
	**
	** @author infinity0
	*/
	public static class DummyPrefixTreeMap<K extends PrefixKey, V> extends PrefixTreeMap<K, V> {

		public DummyPrefixTreeMap(K p, int len, int maxsz, SkeletonPrefixTreeMap<K, V> par) {
			super(p, len, maxsz, par);
		}

		public DummyPrefixTreeMap(K p, int maxsz) {
			super(p, 0, maxsz, null);
		}

		public DummyPrefixTreeMap(K p) {
			super(p, 0, p.symbols(), null);
		}

		Object dummy;

		/************************************************************************
		 * public class PrefixTree
		 ************************************************************************/

		protected DummyPrefixTreeMap<K, V> selectNode(int i) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		protected TreeMap<K, V> getLocalMap() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		protected void clearLocal() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		protected Set<K> keySetLocal() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public int sizeLocal() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		/************************************************************************
		 * public interface Map
		 ************************************************************************/

		public boolean containsValue(Object o) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public Set<Map.Entry<K,V>> entrySet() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public boolean isEmpty() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public Set<K> keySet() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public Collection<V> values() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

	}

	/**
	** The constructor points this to {@link PrefixTreeMap#tmap}, so we don't
	** have to keep casting when we want to access the methods of the subclass.
	*/
	final protected SkeletonTreeMap<K, V> tmap;

	/**
	** The meta data for this skeleton.
	*/
	protected Object meta = null;

	protected SkeletonPrefixTreeMap(K p, int len, int maxsz, int sz, int subs, int[] szPre, boolean[] chd, K[] keys, Object[] values, SkeletonPrefixTreeMap<K, V> par) {
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

	public SkeletonPrefixTreeMap(K p, int len, int maxsz, SkeletonPrefixTreeMap<K, V> par) {
		super(p, len, maxsz, new SkeletonTreeMap<K, V>(), (PrefixTreeMap<K, V>[])new PrefixTreeMap[p.symbols()], par);

		tmap = (SkeletonTreeMap<K, V>)super.tmap;
		String str = prefixString();
		setMeta(str);
		tmap.setMeta(str);
	}

	public SkeletonPrefixTreeMap(K p, int maxsz) {
		this(p, 0, maxsz, null);
	}

	public SkeletonPrefixTreeMap(K p) {
		this(p, 0, p.symbols(), null);
	}

	protected IterableSerialiser<SkeletonPrefixTreeMap<K, V>> serialiser;
	protected MapSerialiser<K, V> serialiserLocal;

	public void setSerialiser(IterableSerialiser<SkeletonPrefixTreeMap<K, V>> s, MapSerialiser<K, V> vs) {
		serialiser = s;
		serialiserLocal = vs;
		tmap.setSerialiser(vs);
		for (PrefixTreeMap<K, V> ch: child) {
			if (ch != null && ch instanceof SkeletonPrefixTreeMap) {
				((SkeletonPrefixTreeMap<K, V>)ch).setSerialiser(s, vs);
			}
		}
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
		int sz = capacityLocal;
		// find the size of the smallest child. if the smallest child is larger
		// than maxSize, then maxSize will be returned instead, but this makes no
		// difference to the tests below (think about it...)
		for (int i=0; i<child.length; ++i) {
			if (chd[i]) {
				if (sizePrefix[i] < sz) {
					sz = sizePrefix[i];
				}
			}
		}
		// see if there are any non-child prefix groups larger than the smallest
		// child. whilst we're at it, calculate sum{ sizePrefix[j]: !chd[j] }
		// for the next test.
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

		// check that sum{ sizePrefix[j] : !chd[j] } + subtrees + sz > capacityLocal
		if (s + subtrees + sz <= capacityLocal) {
			throw new IllegalArgumentException("Invariant broken: count{ non-child prefix groups } + subtrees + sz > maxSize");
		}

		for (int i=0; i<child.length; ++i) {
			if (chd[i]) { putDummyChild(i); }
		}
	}

	/**
	** Put a DummyPrefixTreeMap into the child array.
	**
	** @param i The index to attach the dummy to
	*/
	protected void putDummyChild(int i) {
		child[i] = new DummyPrefixTreeMap((K)prefix.spawn(preflen, i), preflen+1, capacityLocal, this);
	}

	/**
	** Put a DummyPrefixTreeMap into the child array, with a dummy inside it.
	** TODO make it actually use the dummy...
	**
	** @param i The index to attach the dummy to
	*/
	protected void putDummyChild(int i, Object dummy) {
		child[i] = new DummyPrefixTreeMap((K)prefix.spawn(preflen, i), preflen+1, capacityLocal, this);
	}

	/**
	** Put dummy mappings onto the submap. This method carries out certain
	** tests which assume that the child array has already been populated by
	** putDummyChildren.
	**
	** @param keys The array of keys of the map
	** @param values The array of meta values of the map
	*/
	protected void putDummySubmap(K[] keys, Object[] values) {
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

		// check keys.length == sum{ sizePrefix[j] : child[j] == null } and that
		// keys agrees with sizePrefix
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
			tmap.putDummy(keys[i], values[i]);
		}
	}

	/**
	** Assimilate an existing SkeletonPrefixTreeMap into this one. The prefix
	** must match and there must already be a DummyPrefixTreeMap in its place
	** in the child array.
	**
	** @param t The tree to assimilate
	*/
	public void assimilate(SkeletonPrefixTreeMap<K, V> t) {
		if (t.preflen <= preflen) {
			throw new IllegalArgumentException("Only subtrees can be spliced onto an SkeletonPrefixTreeMap.");
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
		} else if (child[i] instanceof SkeletonPrefixTreeMap) {
			if (t.preflen > child[i].preflen) {
				((SkeletonPrefixTreeMap)child[i]).assimilate(t);
			} else {
				// t.preflen == child.preflen since t.preflen > this.preflen
				throw new IllegalArgumentException("This tree has already assimilated a subtree with prefix " + t.prefix);
			}
		}
	}

	/************************************************************************
	 * public class PrefixTree
	 ************************************************************************/

	// We override this method so that the correct serialiser is set
	@Override protected PrefixTreeMap<K, V> makeSubTree(int msym) {
		SkeletonPrefixTreeMap<K, V> ch = new SkeletonPrefixTreeMap<K, V>((K)prefix.spawn(preflen, msym), preflen+1, capacityLocal, this);
		ch.setSerialiser(serialiser, serialiserLocal);
		return ch;
	}

	@Override protected Map<K, V> selectNode(int i) {
		if (child[i] == null) {
			return tmap;
		} else if (child[i] instanceof DummyPrefixTreeMap) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		} else {
			return child[i];
		}
	}

	/************************************************************************
	 * public interface SkeletonMap
	 ************************************************************************/

	@Override public boolean isLive() {
		// OPTIMISE use a counter
		if (!tmap.isLive()) { return false; }
		for (PrefixTreeMap t: child) {
			if (t instanceof DummyPrefixTreeMap) { return false; }
			if (t instanceof SkeletonPrefixTreeMap && !((SkeletonPrefixTreeMap)t).isLive()) {
				return false;
			}
		}
		return true;
	}

	@Override public boolean isBare() {
		// OPTIMISE use a counter
		if (!tmap.isBare()) { return false; }
		for (PrefixTreeMap t: child) {
			if (t instanceof SkeletonPrefixTreeMap) { return false; }
		}
		return true;
	}

	@Override public Object getMeta() {
		return meta;
	}

	@Override public void setMeta(Object m) {
		meta = m;
	}

	@Override public Map<K, V> complete() {
		if (!isLive()) {
			throw new DataNotLoadedException("PrefixTreeMap not fully loaded for " + prefix.toString(), this, this);
		} else {
			TreeMap<K, V> ntmap = (TreeMap<K, V>)tmap.complete();
			PrefixTreeMap<K, V>[] nchild = (PrefixTreeMap<K, V>[])new PrefixTreeMap[subtreesMax];

			for (int i=0; i<subtreesMax; ++i) {
				if (child[i] != null) {
					nchild[i] = (PrefixTreeMap<K, V>)((SkeletonPrefixTreeMap<K, V>)child[i]).complete();
				}
			}

			return new PrefixTreeMap(prefix, preflen, capacityLocal, ntmap, nchild, null);
		}
	}

	@Override public void inflate() {
		throw new UnsupportedOperationException("Not implemented.");
		//assert(isLive());
	}

	@Override public void deflate() {
		if (!tmap.isBare()) { tmap.deflate(); }

		java.util.List<PushTask<SkeletonPrefixTreeMap<K, V>>> tasks = new java.util.ArrayList<PushTask<SkeletonPrefixTreeMap<K, V>>>(subtrees);
		int[] indexes = new int[subtrees];
		int ii=0;

		for (PrefixTreeMap<K, V> chd: child) {
			if (chd == null || !(chd instanceof SkeletonPrefixTreeMap)) { continue; }
			SkeletonPrefixTreeMap<K, V> ch = (SkeletonPrefixTreeMap<K, V>)chd;
			if (!ch.isBare()) { ch.deflate(); }
			tasks.add(new PushTask<SkeletonPrefixTreeMap<K, V>>(ch));
			indexes[ii++] = ch.lastIndex();
		}
		serialiser.push(tasks);

		ii=0;
		for (PushTask<SkeletonPrefixTreeMap<K, V>> t: tasks) {
			putDummyChild(indexes[ii++], t.meta);
		}

		assert(isBare());
	}

	@Override public void inflate(K key) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override public void deflate(K key) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	/************************************************************************
	** {@link Translator} with access to the members of {@link PrefixTreeMap}.
	**
	** This implementation provides static methods to translate between this
	** class and a map from ({@link String} forms of the field names) to
	** (object forms of the field values that are serialisable as defined in
	** {@link Serialiser}).
	*/
	abstract public static class PrefixTreeMapTranslator<K extends PrefixKey, V>
	implements Translator<SkeletonPrefixTreeMap<K, V>, Map<String, Object>> {

		// TODO code backwards translation

		/**
		** Forward translation.
		**
		** @param skel The data structue to translate
		** @param intml A map to populate with the translation of the local map
		** @param intm A map to populate with the translated mappings
		*/
		public static <K extends PrefixKey, V> void app(SkeletonPrefixTreeMap<K, V> skel, Map<String, Object> intml, Map<String, Object> intm) {
			if (!skel.isBare()) {
				throw new IllegalArgumentException("Data structure is not bare. Try calling deflate() first.");
			}
			// OPTMISE make the keys use String.intern()
			intm.put("prefix", skel.prefix.toString());
			intm.put("preflen", skel.preflen);
			intm.put("capacityLocal", skel.capacityLocal);
			intm.put("size", skel.size);
			intm.put("subtreesMax", skel.subtreesMax);
			intm.put("subtrees", skel.subtrees);
			intm.put("sizePrefix", skel.sizePrefix);

			boolean chd[] = new boolean[skel.subtreesMax];
			for (int i=0; i<skel.subtreesMax; ++i) { chd[i] = (skel.child[i] != null); }
			intm.put("_child", chd);

			SkeletonTreeMap.TreeMapTranslator.app(skel.tmap, intml);
			intm.put("_tmap", intml);
		}

	}

}
