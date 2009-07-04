/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.PrefixTree.PrefixKey;

import plugins.Interdex.serl.Serialiser.*;
import plugins.Interdex.serl.Serialiser;
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
	** TODO maybe make this implement SkeletonMap
	**
	** @author infinity0
	*/
	public static class DummyPrefixTreeMap<K extends PrefixKey, V> extends PrefixTreeMap<K, V> {

		public DummyPrefixTreeMap(K p, int len, int caplocal, SkeletonPrefixTreeMap<K, V> par) {
			super(p, len, caplocal, par);
		}

		public DummyPrefixTreeMap(K p, int caplocal) {
			super(p, 0, caplocal, null);
		}

		public DummyPrefixTreeMap(K p) {
			super(p, 0, p.symbols(), null);
		}

		Object meta;

		public Object getMeta() {
			return meta;
		}

		public void setMeta(Object m) {
			meta = m;
		}

		/*========================================================================
		  public class PrefixTree
		 ========================================================================*/

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

		/*========================================================================
		  public interface Map
		 ========================================================================*/

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

	protected SkeletonPrefixTreeMap(K p, int len, int caplocal, SkeletonPrefixTreeMap<K, V> par) {
		super(p, len, caplocal, new SkeletonTreeMap<K, V>(), (PrefixTreeMap<K, V>[])new PrefixTreeMap[p.symbols()], par);

		tmap = (SkeletonTreeMap<K, V>)super.tmap;
		String str = prefixString();
		setMeta(str);
		tmap.setMeta(str);
	}

	public SkeletonPrefixTreeMap(K p, int caplocal) {
		this(p, 0, caplocal, null);
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
	** Put a DummyPrefixTreeMap into the child array, with a dummy inside it.
	** TODO make it actually use the dummy...
	**
	** @param i The index to attach the dummy to.. UNUSED
	*/
	protected void putDummyChild(int i, Object dummy) {
		child[i] = new DummyPrefixTreeMap((K)prefix.spawn(preflen, i), preflen+1, capacityLocal, this);
	}

	/**
	** Attach an existing SkeletonPrefixTreeMap into this one. The prefix must
	** match and there must already be a DummyPrefixTreeMap in its place in the
	** child array.
	**
	** @param t The tree to assimilate
	*/
	protected void putChild(SkeletonPrefixTreeMap<K, V> t) {
		if (t.preflen != preflen+1) {
			throw new IllegalArgumentException("Only direct subtrees can be spliced.");
		}
		if (!t.prefix.match(prefix, preflen)) {
			throw new IllegalArgumentException("Key does not match prefix for this tree.");
		}
		if (t.parent != this) {
			throw new IllegalArgumentException("Subtree does not view this tree as its parent.");
		}

		int i = t.lastIndex();
		if (child[i] == null) {
			throw new IllegalArgumentException("This tree does not have a subtree with prefix " + t.prefix);
		}
		// check t.size == sizePrefix[i]
		if (t.size() != sizePrefix[i]) {
			throw new IllegalArgumentException("The size of the subtree contradicts its entry in sizePrefix");
		}

		if (child[i] instanceof DummyPrefixTreeMap) {
			child[i] = t;
		} else {
			throw new IllegalArgumentException("This tree does not need attach a subtree with prefix " + t.prefix);
		}
	}

	/*========================================================================
	  public class PrefixTree
	 ========================================================================*/

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

	/*========================================================================
	  public interface SkeletonMap
	 ========================================================================*/

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
		if (serialiser == null) {
			throw new IllegalStateException("No serialiser set for this structure.");
		}

		if (!tmap.isLive()) { tmap.inflate(); }

		java.util.List<PullTask<SkeletonPrefixTreeMap<K, V>>> tasks = new java.util.ArrayList<PullTask<SkeletonPrefixTreeMap<K, V>>>(subtrees);
		for (PrefixTreeMap<K, V> chd: child) {
			if (chd == null || !(chd instanceof DummyPrefixTreeMap)) { continue; }
			PullTask<SkeletonPrefixTreeMap<K, V>> task = new PullTask<SkeletonPrefixTreeMap<K, V>>(((DummyPrefixTreeMap<K, V>)chd).getMeta());
			task.data = this; // this is a bit of a hack, but oh well...
			// the only way to supply the parent pointer is via the constructor
			// and hence we must pass the parent pointer to the translator
			// the only way to do this is here. there is no other way to supply
			// the parent pointer - we could make PrefixTreeMap.parent
			// non-final and change this field, but we can't access
			// PrefixTree.parent to change that even if we make it non-final,
			// since it is a grand-superclass. it's too late to recode the
			// entire thing, sorry... :p
			tasks.add(task);
		}
		serialiser.pull(tasks);

		for (PullTask<SkeletonPrefixTreeMap<K, V>> t: tasks) {
			putChild(t.data);
			// TODO maybe find some way to make this concurrent, but not so important
			if (!t.data.isLive()) { t.data.inflate(); }
		}

		assert(isLive());
	}

	@Override public void deflate() {
		if (serialiser == null) {
			throw new IllegalStateException("No serialiser set for this structure.");
		}

		java.util.List<PushTask<SkeletonPrefixTreeMap<K, V>>> tasks = new java.util.ArrayList<PushTask<SkeletonPrefixTreeMap<K, V>>>(subtrees);
		for (PrefixTreeMap<K, V> chd: child) {
			if (chd == null || !(chd instanceof SkeletonPrefixTreeMap)) { continue; }
			SkeletonPrefixTreeMap<K, V> ch = (SkeletonPrefixTreeMap<K, V>)chd;
			// TODO maybe find some way to make this concurrent, but not so important
			if (!ch.isBare()) { ch.deflate(); }
			tasks.add(new PushTask<SkeletonPrefixTreeMap<K, V>>(ch));
		}
		serialiser.push(tasks);

		for (PushTask<SkeletonPrefixTreeMap<K, V>> t: tasks) {
			putDummyChild(t.data.lastIndex(), t.meta);
		}

		if (!tmap.isBare()) { tmap.deflate(); }

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

		/**
		** Forward translation. If the translator is given is {@code null},
		** it will use {@link Object#toString()}.
		**
		** @param skel The data structue to translate
		** @param intml A map to populate with the translation of the local map
		** @param intm A map to populate with the translated mappings
		** @param ktr An optional translator between key and {@link String}
		*/
		public static <K extends PrefixKey, V> Map<String, Object> app(SkeletonPrefixTreeMap<K, V> skel, Map<String, Object> intm, Map<String, Object> intml, Translator<K, String> ktr) {
			if (!skel.isBare()) {
				throw new IllegalArgumentException("Data structure is not bare. Try calling deflate() first.");
			}
			intm.put("prefix", (ktr == null)? skel.prefix.toString(): ktr.app(skel.prefix));
			intm.put("preflen", skel.preflen);
			intm.put("capacityLocal", skel.capacityLocal);
			intm.put("subtrees", skel.subtrees);
			intm.put("size", skel.size);
			intm.put("sizePrefix", skel.sizePrefix);

			boolean chd[] = new boolean[skel.subtreesMax];
			for (int i=0; i<skel.subtreesMax; ++i) { chd[i] = (skel.child[i] != null); }
			intm.put("_child", chd);

			SkeletonTreeMap.TreeMapTranslator.app(skel.tmap, intml, ktr);
			intm.put("_tmap", intml);
			return intm;
		}

		/**
		** Backwards translation. The translator is mandatory here. The parent
		** can be retrieved from {@link PullTask#data} before the task begins.
		**
		** @param intm A map of translated mappings to extract
		** @param par The tree to mark as the parent of the new tree
		** @param ktr A translator between key and {@link String}
		*/
		public static <K extends PrefixKey, V> SkeletonPrefixTreeMap<K, V> rev(Map<String, Object> intm, SkeletonPrefixTreeMap<K, V> par, Translator<K, String> ktr) {
			if (ktr == null) {
				throw new IllegalArgumentException("SkeletonPrefixTreeMap: Translator cannot be null for reverse translation.");
			}
			try {
				K p = ktr.rev((String)intm.get("prefix"));
				int pl = (Integer)intm.get("preflen");
				int cl = (Integer)intm.get("capacityLocal");
				int sb = (Integer)intm.get("subtrees");
				int sz = (Integer)intm.get("size");
				int[] sp = (int[])intm.get("sizePrefix");
				boolean[] chd = (boolean[])intm.get("_child");
				Map<String, Object> tm = (Map<String, Object>)intm.get("_tmap");

				SkeletonPrefixTreeMap<K, V> skel = new SkeletonPrefixTreeMap<K, V>(p, pl, cl, par);
				SkeletonTreeMap.TreeMapTranslator.rev(tm, skel.tmap, ktr);

				checkValid(p, pl, cl, sb, sz, sp, chd, skel.tmap.keySet());

				skel.subtrees = sb;
				skel.size = sz;
				skel.sizePrefix = sp;
				for (int i=0; i<chd.length; ++i) {
					if (chd[i]) { skel.putDummyChild(i, null); }
				}

				return skel;

			} catch (ClassCastException e) {
				throw e; // throw data corrupt?
			} catch (NullPointerException e) {
				throw e; // throw data corrupt?
			} catch (IllegalArgumentException e) {
				throw e; // throw data corrupt?
			}

		}

		/**
		** Checks the integrity of the given data. Tests:
		**
		** - prefix.symbols() == chd.length == sizePrefix.length
		** - size == sum{ sizePrefix }
		** - count{ i: chd[i] } == subtrees
		** - if there are subtrees, then:
		**   - let sz be the size of the smallest child; then for all i:
		**     - (chd[i] == false) => sizePrefix[i] <= sz
		**     - (chd[i] != false) => sizePrefix[i] >= sz
		**   - sz > spaceLeft
		** - for all k in keySetLocal: !chd[prefix group of k]
		** - count of keys for each group in keySetLocal agrees with sizePrefix
		** - keySetLocal.size() == sum{ sizePrefix[j] : !chd[j] }
		**
		** We skip testing this data against the parent as this is already done
		** in {@link #putChild(SkeletonPrefixTreeMap)} (and here we would have
		** to code for the null case).
		*/
		public static <K extends PrefixKey> void checkValid(K prefix, int preflen, int capacityLocal, int subtrees, int size, int[] sizePrefix, boolean[] chd, Set<K> keySetLocal) {
			int s;

			// check chd has correct size
			if (chd.length != prefix.symbols()) {
				throw new IllegalArgumentException("Child array has incompatible size for the given prefix.");
			}

			// check sizePrefix has correct size
			if (sizePrefix.length != prefix.symbols()) {
				throw new IllegalArgumentException("Size prefix array has incompatible size for the given prefix.");
			}

			// check size == sum{ sizePrefix }
			s = 0;
			for (int pre: sizePrefix) { s += pre; }
			if (size != s) {
				throw new IllegalArgumentException("Invariant broken: size == sum{ sizePrefix }");
			}

			// check count{ i: chd[i] } == subtrees
			s = 0;
			for (boolean b: chd) { if (b) { ++s; } }
			if (subtrees != s) {
				throw new IllegalArgumentException("Invariant broken: subtrees == count{ non-null children }");
			}

			if (subtrees > 0) {
				// check that there exists sz such that for all i:
				// (chd[i] == false) => sizePrefix[i] <= sz
				// (chd[i] != false) => sizePrefix[i] >= sz
				int sz = size;
				// find the size of the smallest child
				for (int i=0; i<chd.length; ++i) {
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
				for (int i=0; i<chd.length; ++i) {
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
				// check that sz > spaceLeft == capacityLocal - sum{ sizePrefix[j] : !chd[j] } - subtrees
				if (sz <= capacityLocal - s - subtrees) {
					throw new IllegalArgumentException("Invariant broken: size(smallest child) > spaceLeft == capacityLocal - size{ non-child prefix groups } - subtrees");
				}
			}

			// check that for all k in keySetLocal: !chd[prefix group of k]
			// whilst we're at it, construct the array for the next test
			int[] szPre = new int[sizePrefix.length];
			for (K k: keySetLocal) {
				int p = k.get(preflen);
				if (chd[p]) {
					throw new IllegalArgumentException("A subtree already exists for this key: " + k.toString());
				}
				++szPre[p];
			}

			// check that keySetLocal agrees with sizePrefix and that
			// keySetLocal.size() == sum{ sizePrefix[j] : !chd[j] }
			s = 0;
			for (int i=0; i<sizePrefix.length; ++i) {
				if (!chd[i]) {
					s += szPre[i];
					if (sizePrefix[i] != szPre[i]) {
						throw new IllegalArgumentException("The keys given contradicts the sizePrefix array");
					}
				}
			}
			if (s != keySetLocal.size()) {
				throw new IllegalArgumentException("The keys given contradicts the sizePrefix array");
			}

		}


	}

}
