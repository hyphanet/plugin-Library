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
** URGENT this class needs serious testing. but do after inflate/deflate is
** done.
**
** PRIORITY perhaps override equals and hashcode
**
** @author infinity0
*/
public class SkeletonPrefixTreeMap<K extends PrefixKey, V>
extends PrefixTreeMap<K, V>
implements SkeletonMap<K, V> {

	protected static class DummyChild<K extends PrefixKey, V> extends SkeletonPrefixTreeMap<K, V> {

		SkeletonPrefixTreeMap<K, V> parent;

		protected DummyChild(K p, int len, int caplocal, SkeletonPrefixTreeMap<K, V> par) {
			super(p, len, caplocal, null, null);
		}

		@Override public void clear() { throw childNotLoaded(); }
		@Override public boolean isEmpty() { throw childNotLoaded(); }
		@Override public boolean containsKey(Object key) { throw childNotLoaded(); }
		@Override public boolean containsValue(Object value) { throw childNotLoaded(); }
		@Override public Set<Map.Entry<K,V>> entrySet() { throw childNotLoaded(); }
		@Override public V get(Object key) { throw childNotLoaded(); }
		@Override public Set<K> keySet() { throw childNotLoaded(); }
		@Override public V put(K key, V value) { throw childNotLoaded(); }
		@Override public void putAll(Map<? extends K,? extends V> t) { throw childNotLoaded(); }
		@Override public V remove(Object key) { throw childNotLoaded(); }
		@Override public int size() { throw childNotLoaded(); }
		@Override public Collection<V> values() { throw childNotLoaded(); }

		@Override public boolean isLive() { return false; }
		@Override public boolean isBare() { return true; }

		@Override public void inflate() { throw childNotLoaded(); }
		@Override public void deflate() { throw childNotLoaded(); }
		@Override public void inflate(K key) { throw childNotLoaded(); }
		@Override public void deflate(K key) { throw childNotLoaded(); }

		final protected DataNotLoadedException childNotLoaded() {
			return new DataNotLoadedException("Child tree " + prefix + " not loaded for PrefixTreeMap " + parent.prefix, parent, prefix, meta);
		}

	}

	/**
	** The constructor points this to {@link PrefixTreeMap#child}, so we don't
	** have to keep casting when we want to access the methods of the subclass.
	*/
	final protected SkeletonPrefixTreeMap<K, V>[] child;

	/**
	** The constructor points this to {@link PrefixTreeMap#tmap}, so we don't
	** have to keep casting when we want to access the methods of the subclass.
	*/
	final protected SkeletonTreeMap<K, V> tmap;

	/**
	** The meta data for this skeleton. Passed to the local map, which uses it
	** as the map-wide metadata for {@link MapSerialiser}'s push and pull
	** methods.
	*/
	protected Object meta;

	/**
	** Keeps track of the number of dummies in the map.
	*/
	protected transient int dummyCount;

	protected SkeletonPrefixTreeMap(K p, int len, int caplocal, SkeletonTreeMap<K, V> tm, SkeletonPrefixTreeMap<K, V>[] chd) {
		super(p, len, caplocal, tm, chd);

		child = (SkeletonPrefixTreeMap<K, V>[])super.child;
		tmap = (SkeletonTreeMap<K, V>)super.tmap;

		String str = prefixString();
		setMeta(str);
		if (tmap != null) { tmap.setMeta(str); }
	}

	protected SkeletonPrefixTreeMap(K p, int len, int caplocal) {
		this(p, len, caplocal, new SkeletonTreeMap<K, V>(), new SkeletonPrefixTreeMap[p.symbols()]);
	}

	public SkeletonPrefixTreeMap(K p, int caplocal) {
		this(p, 0, caplocal);
	}

	public SkeletonPrefixTreeMap(K p) {
		this(p, 0, p.symbols());
	}

	protected IterableSerialiser<SkeletonPrefixTreeMap<K, V>> serialiser;
	protected MapSerialiser<K, V> serialiserLocal;

	public void setSerialiser(IterableSerialiser<SkeletonPrefixTreeMap<K, V>> s, MapSerialiser<K, V> vs) {
		serialiser = s;
		serialiserLocal = vs;
		tmap.setSerialiser(vs);
		for (PrefixTreeMap<K, V> ch: child) {
			if (ch == null) { continue; }
			((SkeletonPrefixTreeMap<K, V>)ch).setSerialiser(s, vs);
		}
	}

	/**
	** Attach a dummy subtree. If there is already a dummy in its place, does
	** nothing.
	**
	** @param i The index to attach to
	** @param meta The meta to associate the child with
	*/
	protected void putDummyChild(int i, Object meta) {
		if (child[i] != null && child[i] instanceof DummyChild) { return; }
		child[i] = new DummyChild<K, V>((K)prefix.spawn(preflen, i), preflen, capacityLocal, this);
		child[i].setMeta(meta);
		++dummyCount;
	}

	/**
	** Attach an existing SkeletonPrefixTreeMap into this one. The prefix must
	** match and there must already be a dummy in the child array.
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

		int i = t.lastIndex();
		if (child[i] == null) {
			throw new IllegalArgumentException("This tree does not have a subtree with prefix " + t.prefix);
		}
		// check t.size == sizePrefix[i]
		if (t.size() != sizePrefix[i]) {
			throw new IllegalArgumentException("The size of the subtree contradicts its entry in sizePrefix");
		}

		if (!(child[i] instanceof DummyChild)) {
			throw new IllegalArgumentException("This tree does not need attach a subtree with prefix " + t.prefix);
		}
		child[i] = t;
		child[i].setSerialiser(serialiser, serialiserLocal);
		--dummyCount;
	}

	/*========================================================================
	  public class PrefixTree
	 ========================================================================*/

	// We override this method so that the correct serialiser is set
	@Override protected PrefixTreeMap<K, V> makeSubTree(int msym) {
		SkeletonPrefixTreeMap<K, V> ch = new SkeletonPrefixTreeMap<K, V>((K)prefix.spawn(preflen, msym), preflen+1, capacityLocal);
		ch.setSerialiser(serialiser, serialiserLocal);
		return ch;
	}

	/*========================================================================
	  public class PrefixTreeMap
	 ========================================================================*/

	@Override public V put(K key, V value) {
		if (!key.match(prefix, preflen)) {
			throw new IllegalArgumentException("Key does not match prefix for this tree.");
		}

		int i = key.get(preflen);
		Map<K, V> map = selectNode(i);
		int s = map.size();
		V v = map.put(key, value);

		if (map.size() != s) {
			// attempts to detect a situation where a reshuffle of the tree would occur
			// but not all the data for this reshuffle has been loaded
			if (map == tmap && !isLive() && child[smallestChild()] instanceof DummyChild) {
				// TODO use the logic below instead...
				map.remove(key);
				throw ((DummyChild)child[smch_]).childNotLoaded();
				// if smallest child is not loaded, if map is local map,
				// if smallestChild exists and its size == this subgroup's old size (sizePrefix hasn't been updated yet)
				// then smallest child is going to be freed, so remove the value and
				// throw DNL ex
			}
			reshuffleAfterPut(i);
		}
		return v;
	}

	@Override public V remove(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return null; }

		int i = k.get(preflen);
		Map<K, V> map = selectNode(i);
		int s = map.size();
		V v = map.remove(key);

		if (map.size() != s) {
			// attempts to detect a situation where a reshuffle of the tree would occur
			// but not all the data for this reshuffle has been loaded
			if (map == tmap && !isLive() && child[smallestChild()] instanceof DummyChild) {
				// TODO use the logic below instead...
				map.put(k, v);
				throw ((DummyChild)child[smch_]).childNotLoaded();
				// if smallest child is not loaded,
				// if map is local map, or if this subgroup's size is smallestChild's size
				// and spaceleft is smallestChild's size - 2,
				// then something is going to be freed, so put the value back in
				// and throw DNL ex
			}
			reshuffleAfterRemove(i);
		}
		return v;
	}

	/*========================================================================
	  public interface SkeletonMap
	 ========================================================================*/

	/**
	** {@inheritDoc}
	**
	** This implemenation returns negative results quicker than positive ones.
	** (NP vs coNP)
	*/
	@Override public boolean isLive() {
		if (!tmap.isLive() || dummyCount > 0) { return false; }
		// no dummy children, check they are all live
		for (SkeletonPrefixTreeMap<K, V> ch: child) {
			if (ch == null) { continue; }
			if (!ch.isLive()) { return false; }
		}
		return true;
	}

	/**
	** {@inheritDoc}
	**
	** This implemenation assumes that non-dummy childs are not bare. (This is
	** enforced in {@link #deflate(K)}.)
	*/
	@Override public boolean isBare() {
		return (tmap.isBare() && dummyCount == subtrees);
	}

	@Override public Object getMeta() {
		return meta;
	}

	@Override public void setMeta(Object m) {
		meta = m;
	}

	@Override public void inflate() {
		if (serialiser == null) { throw new IllegalStateException("No serialiser set for this structure."); }

		if (!tmap.isLive()) { tmap.inflate(); }

		java.util.List<PullTask<SkeletonPrefixTreeMap<K, V>>> tasks = new java.util.ArrayList<PullTask<SkeletonPrefixTreeMap<K, V>>>(subtrees);
		for (SkeletonPrefixTreeMap<K, V> ch: child) {
			if (ch == null || !(ch instanceof DummyChild)) { continue; }
			PullTask<SkeletonPrefixTreeMap<K, V>> task = new PullTask<SkeletonPrefixTreeMap<K, V>>(ch.getMeta());
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
		if (serialiser == null) { throw new IllegalStateException("No serialiser set for this structure."); }

		java.util.List<PushTask<SkeletonPrefixTreeMap<K, V>>> tasks = new java.util.ArrayList<PushTask<SkeletonPrefixTreeMap<K, V>>>(subtrees);
		for (SkeletonPrefixTreeMap<K, V> ch: child) {
			if (ch == null || ch instanceof DummyChild) { continue; }
			// TODO maybe find some way to make this concurrent, but not so important
			if (!ch.isBare()) { ch.deflate(); }
			tasks.add(new PushTask<SkeletonPrefixTreeMap<K, V>>(ch, ch.getMeta()));
		}
		serialiser.push(tasks);

		for (PushTask<SkeletonPrefixTreeMap<K, V>> t: tasks) {
			putDummyChild(t.data.lastIndex(), t.meta);
		}

		if (!tmap.isBare()) { tmap.deflate(); }

		assert(isBare());
	}

	@Override public void inflate(K key) {
		if (serialiser == null) { throw new IllegalStateException("No serialiser set for this structure."); }

		int i = key.get(preflen);
		if (child[i] == null) {
			tmap.inflate(key);

		} else {
			if (child[i] instanceof DummyChild) {
				PullTask<SkeletonPrefixTreeMap<K, V>> task = new PullTask<SkeletonPrefixTreeMap<K, V>>(child[i].getMeta());
				serialiser.pull(task);
				putChild(task.data);
			}

			child[i].inflate(key);
		}
	}

	@Override public void deflate(K key) {
		if (serialiser == null) { throw new IllegalStateException("No serialiser set for this structure."); }

		int i = key.get(preflen);
		if (child[i] == null) {
			tmap.deflate(key);

		} else if (!(child[i] instanceof DummyChild)) {
			child[i].deflate(key);

			// if the child is now bare, push it to disk too; a bare tree is useless
			if (child[i].isBare()) {
				PushTask<SkeletonPrefixTreeMap<K, V>> task = new PushTask<SkeletonPrefixTreeMap<K, V>>(child[i], child[i].getMeta());
				serialiser.push(task);
				putDummyChild(i, task.meta);
			}

		}
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
		** Backwards translation. The translator is mandatory here.
		**
		** @param intm A map of translated mappings to extract
		** @param ktr A translator between key and {@link String}
		*/
		public static <K extends PrefixKey, V> SkeletonPrefixTreeMap<K, V> rev(Map<String, Object> intm, Translator<K, String> ktr) {
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

				SkeletonPrefixTreeMap<K, V> skel = new SkeletonPrefixTreeMap<K, V>(p, pl, cl);
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
