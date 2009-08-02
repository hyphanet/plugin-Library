/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.util.SkeletonTreeMap;
import plugins.Library.util.SkeletonBTreeMap;
import plugins.Library.util.SkeletonBTreeSet;

import plugins.Library.serial.Serialiser.*;
import plugins.Library.serial.Serialiser;
import plugins.Library.serial.Translator;
import plugins.Library.serial.ProgressTracker;
import plugins.Library.serial.Archiver;
import plugins.Library.serial.IterableSerialiser;
import plugins.Library.serial.MapSerialiser;
import plugins.Library.serial.LiveArchiver;
import plugins.Library.serial.ParallelSerialiser;
import plugins.Library.serial.Packer;
import plugins.Library.serial.Progress;
import plugins.Library.serial.SimpleProgress;
import plugins.Library.serial.CompoundProgress;
import plugins.Library.serial.YamlArchiver;
import plugins.Library.serial.DataFormatException;
import plugins.Library.serial.TaskAbortException;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.Date;

import freenet.keys.FreenetURI;

/**
** Test Serialiser for the new B-tree based index
**
** @author infinity0
*/
public class BIndexSerialiser
implements Archiver<ProtoIndex>,
           Serialiser.Composite<Archiver<Map<String, Object>>>,
           Serialiser.Translate<ProtoIndex, Map<String, Object>>/*,
           Serialiser.Trackable<Index>*/ {

	/*========================================================================
	  Serialisers and Translators - main Index
	 ========================================================================*/

	final protected Archiver<Map<String, Object>> subsrl;
	final protected Translator<ProtoIndex, Map<String, Object>> trans;

	public BIndexSerialiser() {
		// for DEBUG use; the freenet version would save to SSK@key/my_index/index.yml
		//subsrl = new YamlArchiver<Map<String, Object>>("index", "");
		subsrl = new YamlArchiver<Map<String, Object>>(true);
		//tmsrl = new BTreeMapSerialiser<String, SkeletonBTreeSet<TermEntry>>();
		//usrl = new BTreeMapSerialiser<URIKey, SortedMap<FreenetURI, URIEntry>>(new URIKeyTranslator());
		trans = new IndexTranslator();
	}

	@Override public Archiver<Map<String, Object>> getChildSerialiser() {
		return subsrl;
	}

	@Override public Translator<ProtoIndex, Map<String, Object>> getTranslator() {
		return trans;
	}

	@Override public void pull(PullTask<ProtoIndex> task) throws TaskAbortException {
		PullTask<Map<String, Object>> serialisable = new PullTask<Map<String, Object>>(task.meta);
		subsrl.pull(serialisable);
		task.meta = serialisable.meta; task.data = trans.rev(serialisable.data);
	}

	@Override public void push(PushTask<ProtoIndex> task) throws TaskAbortException {
		PushTask<Map<String, Object>> serialisable = new PushTask<Map<String, Object>>(trans.app(task.data));
		subsrl.push(serialisable);
		task.meta = serialisable.meta;
	}


	public static class IndexTranslator
	implements Translator<ProtoIndex, Map<String, Object>> {

		/**
		** Term-table translator
		*/
		Translator<SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>>, Map<String, Object>> ttrans = new
		SkeletonBTreeMap.TreeTranslator<String, SkeletonBTreeSet<TermEntry>>(null, new
		TreeMapTranslator<String, SkeletonBTreeSet<TermEntry>>(null));

		/**
		** URI-table translator
		*/
		Translator<SkeletonBTreeMap<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>, Map<String, Object>> utrans = new
		SkeletonBTreeMap.TreeTranslator<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>(null, new
		TreeMapTranslator<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>(null));


		@Override public Map<String, Object> app(ProtoIndex idx) {
			if (!idx.ttab.isBare() || !idx.utab.isBare()) {
				throw new IllegalArgumentException("Data structure is not bare. Try calling deflate() first.");
			}
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			map.put("serialVersionUID", idx.serialVersionUID);
			map.put("id", idx.id);
			map.put("name", idx.name);
			map.put("modified", idx.modified);
			map.put("extra", idx.extra);
			map.put("utab", utrans.app(idx.utab));
			map.put("ttab", ttrans.app(idx.ttab));
			return map;
		}

		@Override public ProtoIndex rev(Map<String, Object> map) {
			long magic = (Long)map.get("serialVersionUID");

			if (magic == ProtoIndex.serialVersionUID) {
				try {
					FreenetURI id = (FreenetURI)map.get("id");
					String name = (String)map.get("name");
					Date modified = (Date)map.get("modified");
					Map<String, Object> extra = (Map<String, Object>)map.get("extra");
					SkeletonBTreeMap<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>> utab = utrans.rev((Map<String, Object>)map.get("utab"));
					SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> ttab = ttrans.rev((Map<String, Object>)map.get("ttab"));

					return setSerialiserFor(new ProtoIndex(id, name, modified, extra, utab, ttab));

				} catch (ClassCastException e) {
					// TODO maybe find a way to pass the actual bad data to the exception
					throw new DataFormatException("Badly formatted data: " + e, e, null);
				}

			} else {
				throw new DataFormatException("Unrecognised magic number", magic, map, "serialVersionUID");
			}
		}

	}

	/*========================================================================
	  Serialisers and Translators - term table, uri table
	 ========================================================================*/

	/**
	** Translator for the local entries of a node of the ''term table''.
	*/
	final protected static Translator<SkeletonTreeMap<String, SkeletonBTreeSet<TermEntry>>, Map<String, Object>>
	ttab_keys_mtr = new TreeMapTranslator<String, SkeletonBTreeSet<TermEntry>>(null);

	/**
	** Translator for the local entries of a node of the ''B-tree'' for a
	** ''term''.
	*/
	final protected static Translator<SkeletonTreeMap<TermEntry, TermEntry>, Collection<TermEntry>>
	term_data_mtr = new SkeletonBTreeSet.TreeSetTranslator<TermEntry>();

	/**
	** Serialiser for the ''targets'' of the values stored in a node of the
	** ''term table''. In this case, each target is the root node of the
	** ''B-tree'' that holds ''term entries'' for the ''term'' mapping to the
	** value.
	*/
	final protected static BTreePacker<String, SkeletonBTreeSet<TermEntry>, EntryGroupSerialiser<String, SkeletonBTreeSet<TermEntry>>>
	ttab_data = new BTreePacker(
		new EntryGroupSerialiser<String, SkeletonBTreeSet<TermEntry>>(
			null,
			new SkeletonBTreeSet.TreeTranslator<TermEntry, TermEntry>(null, term_data_mtr) {
				@Override public SkeletonBTreeSet<TermEntry> rev(Map<String, Object> tree) {
					return BIndexSerialiser.setSerialiserFor(super.rev(tree));
				}
			}
		),
		new Packer.Scale<SkeletonBTreeSet<TermEntry>>() {
			@Override public int weigh(SkeletonBTreeSet<TermEntry> element) {
				return element.rootSize();
			}
		},
		ProtoIndex.BTREE_ENT_MAX
	);

	/**
	** Translator for {@link URIKey}.
	*/
	final protected static Translator<URIKey, String>
	utab_keys_ktr = new Translator<URIKey, String>() {
		public String app(URIKey k) { return k.toString(); }
		public URIKey rev(String s) { return new URIKey(URIKey.hexToBytes(s)); }
	};

	/**
	** Translator for the local entries of a node of the ''uri table''.
	*/
	final protected static Translator<SkeletonTreeMap<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>, Map<String, Object>>
	utab_keys_mtr = new TreeMapTranslator<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>(utab_keys_ktr);

	/**
	** Serialiser for the ''targets'' of the values stored in a node of the
	** ''uri table''. In this case, each target is the root node of the
	** ''B-tree'' that holds ''uri-entry mappings'' for the ''urikey'' mapping
	** to the value.
	*/
	final protected static BTreePacker<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>, EntryGroupSerialiser<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>>
	utab_data = new BTreePacker(
		new EntryGroupSerialiser<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>(
			null,
			new SkeletonBTreeMap.TreeTranslator<FreenetURI, URIEntry>(null, null) {
				@Override public SkeletonBTreeMap<FreenetURI, URIEntry> rev(Map<String, Object> tree) {
					return BIndexSerialiser.setSerialiserFor(super.rev(tree));
				}
			}
		),
		new Packer.Scale<SkeletonBTreeMap<FreenetURI, URIEntry>>() {
			@Override public int weigh(SkeletonBTreeMap<FreenetURI, URIEntry> element) {
				return element.rootSize();
			}
		},
		ProtoIndex.BTREE_ENT_MAX
	);

	/**
	** Set the serialisers for the ''uri table'' and the ''term table'' on an
	** index.
	*/
	public static ProtoIndex setSerialiserFor(ProtoIndex index) {
		// set serialisers on the ttab
		BTreeNodeSerialiser ttab_keys = new BTreeNodeSerialiser<String, SkeletonBTreeSet<TermEntry>>(
			"term listings",
			index.ttab.makeNodeTranslator(null, ttab_keys_mtr)
		);
		index.ttab.setSerialiser(ttab_keys, ttab_data);

		// set serialisers on the utab
		BTreeNodeSerialiser utab_keys = new BTreeNodeSerialiser<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>(
			"uri listings",
			index.utab.makeNodeTranslator(utab_keys_ktr, utab_keys_mtr)
		);
		index.utab.setSerialiser(utab_keys, utab_data);

		// set index trackables
		index.trackables[ProtoIndex.TTAB_KEYS] = ttab_keys;
		index.trackables[ProtoIndex.TTAB_DATA] = ttab_data;
		index.trackables[ProtoIndex.UTAB_KEYS] = utab_keys;
		index.trackables[ProtoIndex.UTAB_DATA] = utab_data;
		return index;
	}

	/**
	** Serialiser for the ''targets'' of the values stored in a node of the
	** ''B-tree'' for a ''term''. In this case, the values are the actual
	** targets and are stored inside the node, so we use a dummy.
	*/
	final protected static MapSerialiser<TermEntry, TermEntry> term_dummy = new DummySerialiser<TermEntry, TermEntry>();

	/**
	** Set the serialiser for the ''B-tree'' that holds the ''uri-entry
	** mappings'' for a ''urikey''.
	*/
	public static SkeletonBTreeMap<FreenetURI, URIEntry> setSerialiserFor(SkeletonBTreeMap<FreenetURI, URIEntry> entries) {
		BTreeNodeSerialiser uri_keys = new BTreeNodeSerialiser<FreenetURI, URIEntry>(
			"uri entries",
			entries.makeNodeTranslator(null, null) // no translator needed as FreenetURI and URIEntry are both directly serialisable by YamlArchiver
		);
		entries.setSerialiser(uri_keys, uri_dummy);
		return entries;
	}

	/**
	** Serialiser for the ''targets'' of the values stored in a node of the
	** ''B-tree'' for a ''urikey''. In this case, the values are the actual
	** targets and are stored inside the node, so we use a dummy.
	*/
	final protected static MapSerialiser<FreenetURI, URIEntry> uri_dummy = new DummySerialiser<FreenetURI, URIEntry>();

	/**
	** Set the serialiser for the ''B-tree'' that holds the ''term entries''
	** for a ''term''.
	*/
	public static SkeletonBTreeSet<TermEntry> setSerialiserFor(SkeletonBTreeSet<TermEntry> entries) {
		BTreeNodeSerialiser term_keys = new BTreeNodeSerialiser<TermEntry, TermEntry>(
			"term entries",
			entries.makeNodeTranslator(null, term_data_mtr)
		);
		entries.setSerialiser(term_keys, term_dummy);
		return entries;
	}


	/*========================================================================
	  static class - templates
	 ========================================================================*/


	/************************************************************************
	** Generic {@link SkeletonTreeMap} translator.
	**
	** @author infinity0
	*/
	public static class TreeMapTranslator<K, V>
	extends SkeletonTreeMap.TreeMapTranslator<K, V> {

		final protected Translator<K, String> ktr;

		public TreeMapTranslator(Translator<K, String> k) {
			ktr = k;
		}

		@Override public Map<String, Object> app(SkeletonTreeMap<K, V> map) {
			return app(map, new TreeMap<String, Object>(), ktr);
		}

		@Override public SkeletonTreeMap<K, V> rev(Map<String, Object> map) {
			return rev(map, new SkeletonTreeMap<K, V>(), ktr);
		}
	}


	/************************************************************************
	** Serialiser for a general B-tree node. This can be used for both a
	** {@link SkeletonBTreeMap} and a {@link SkeletonBTreeSet}; they use the
	** same node class.
	**
	** @author infinity0
	*/
	public static class BTreeNodeSerialiser<K, V>
	extends ParallelSerialiser<SkeletonBTreeMap<K, V>.SkeletonNode, SimpleProgress>
	implements Archiver<SkeletonBTreeMap<K, V>.SkeletonNode>,
	           Serialiser.Translate<SkeletonBTreeMap<K, V>.SkeletonNode, Map<String, Object>>,
	           Serialiser.Composite<LiveArchiver<Map<String, Object>, SimpleProgress>> {

		final protected Translator<SkeletonBTreeMap<K, V>.SkeletonNode, Map<String, Object>> trans;
		final protected LiveArchiver<Map<String, Object>, SimpleProgress> subsrl;

		protected String name;

		/**
		** Constructs a new serialiser. A new one must be constructed for each
		** BTree being serialised.
		**
		** @param n Description of what the map stores. This is used in the
		**        progress report.
		** @param t Translator for the node - create this using {@link
		**        SkeletonBTreeMap#makeNodeTranslator(Translator, Translator)}.
		*/
		public BTreeNodeSerialiser(String n, SkeletonBTreeMap<K, V>.NodeTranslator<?, ?> t) {
			super(new ProgressTracker<SkeletonBTreeMap<K, V>.SkeletonNode, SimpleProgress>(SimpleProgress.class));
			subsrl = new YamlArchiver<Map<String, Object>>(true);
			trans = t;
			name = n;
		}

		@Override public Translator<SkeletonBTreeMap<K, V>.SkeletonNode, Map<String, Object>> getTranslator() {
			return trans;
		}

		@Override public LiveArchiver<Map<String, Object>, SimpleProgress> getChildSerialiser() {
			return subsrl;
		}

		@Override public void pullLive(PullTask<SkeletonBTreeMap<K, V>.SkeletonNode> task, SimpleProgress p) {
			SkeletonBTreeMap<K, V>.GhostNode ghost = (SkeletonBTreeMap.GhostNode)task.meta;
			PullTask<Map<String, Object>> serialisable = new PullTask<Map<String, Object>>(ghost.getMeta());
			p.setName("Pulling " + name + ": " + ghost.getRange());
			p.addTotal(1, false);
			subsrl.pullLive(serialisable, p);
			ghost.setMeta(serialisable.meta); task.data = trans.rev(serialisable.data);
			p.addPartDone();
		}

		@Override public void pushLive(PushTask<SkeletonBTreeMap<K, V>.SkeletonNode> task, SimpleProgress p) {
			Map<String, Object> intermediate = trans.app(task.data);
			PushTask<Map<String, Object>> serialisable = new PushTask<Map<String, Object>>(intermediate, task.meta);
			p.setName("Pushing " + name + ": " + task.data.getRange());
			p.addTotal(1, false);
			subsrl.pushLive(serialisable, p);
			task.meta = task.data.makeGhost(serialisable.meta);
			p.addPartDone();
		}

	}


	/************************************************************************
	** Serialiser for the entries contained within a B-tree node.
	**
	** @author infinity0
	*/
	public static class BTreePacker<K, V, S extends IterableSerialiser<Map<K, V>> & Serialiser.Trackable<Map<K, V>>>
	extends Packer<K, V>
	implements MapSerialiser<K, V>,
	           Serialiser.Trackable<V> {

		final protected ProgressTracker<V, CompoundProgress> tracker;
		final protected S subsrl;

		public BTreePacker(S s, Packer.Scale<V> sc, int cap) {
			super(s, sc, cap);
			subsrl = (S)super.subsrl;
			tracker = new ProgressTracker<V, CompoundProgress>(CompoundProgress.class);
		}

		@Override public ProgressTracker<V, ? extends Progress> getTracker() {
			return tracker;
		}

		@Override protected void preprocessPullBins(Map<K, PullTask<V>> tasks, Collection<PullTask<Map<K, V>>> bintasks) {
			List<Object> mib = new ArrayList<Object>(bintasks.size());
			for (PullTask<Map<K, V>> t: bintasks) {
				mib.add(t.meta);
			}

			for (Map.Entry<K, PullTask<V>> en: tasks.entrySet()) {
				CompoundProgress p = tracker.addPullProgress(en.getValue().meta);
				if (p != null) { p.setSubprogress(subsrl.getTracker().iterableOfPull(mib)); }
				p.setName("Pulling root container for " + en.getKey());
			}
		}

		@Override protected void preprocessPushBins(Map<K, PushTask<V>> tasks, Collection<PushTask<Map<K, V>>> bintasks) {
			List<Map<K, V>> dib = new ArrayList<Map<K, V>>(bintasks.size());
			for (PushTask<Map<K, V>> t: bintasks) {
				dib.add(t.data);
			}

			for (Map.Entry<K, PushTask<V>> en: tasks.entrySet()) {
				CompoundProgress p = tracker.addPushProgress(en.getValue().data);
				if (p != null) { p.setSubprogress(subsrl.getTracker().iterableOfPush(dib)); }
				p.setName("Pushing root container for " + en.getKey());
			}
		}

	}


	/************************************************************************
	** Serialiser for the bins containing (B-tree root nodes) that was packed
	** by the {@link BTreePacker}.
	**
	** @author infinity0
	*/
	public static class EntryGroupSerialiser<K, V>
	extends ParallelSerialiser<Map<K, V>, SimpleProgress>
	implements IterableSerialiser<Map<K, V>>,
	           Serialiser.Composite<LiveArchiver<Map<String, Object>, SimpleProgress>> {

		final protected LiveArchiver<Map<String, Object>, SimpleProgress> subsrl;
		final protected Translator<K, String> ktr;
		final protected Translator<V, Map<String, Object>> btr;

		public EntryGroupSerialiser(Translator<K, String> k, Translator<V, Map<String, Object>> b) {
			super(new ProgressTracker<Map<K, V>, SimpleProgress>(SimpleProgress.class));
			subsrl = new YamlArchiver<Map<String, Object>>(true);
			ktr = k;
			btr = b;
		}

		@Override public LiveArchiver<Map<String, Object>, SimpleProgress> getChildSerialiser() {
			return subsrl;
		}

		@Override public void pullLive(PullTask<Map<K, V>> task, SimpleProgress p) {
			PullTask<Map<String, Object>> t = new PullTask<Map<String, Object>>(task.meta);
			p.setName("Pulling root container " + task.meta);
			p.addTotal(1, false);
			try {
				subsrl.pullLive(t, p);

				Map<K, V> map = new HashMap<K, V>(t.data.size()<<1);
				try {
					for (Map.Entry<String, Object> en: t.data.entrySet()) {
						map.put((ktr == null)? (K)en.getKey(): ktr.rev(en.getKey()), btr.rev((Map<String, Object>)en.getValue()));
					}
				} catch (ClassCastException e) {
					// TODO more meaningful error message
					throw new DataFormatException("Exception in converting data", e, t.data, null, null);
				}

				task.data = map;
				p.addPartDone();
			} catch (RuntimeException e) {
				p.setAbort(new TaskAbortException("Could not retrieve data from bin " + t.data.keySet(), e));
			}
		}

		@Override public void pushLive(PushTask<Map<K, V>> task, SimpleProgress p) {
			Map<String, Object> conv = new HashMap<String, Object>();
			for (Map.Entry<K, V> mp: task.data.entrySet()) {
				conv.put((ktr == null)? (String)mp.getKey(): ktr.app(mp.getKey()), btr.app(mp.getValue()));
			}

			PushTask<Map<String, Object>> t = new PushTask<Map<String, Object>>(conv, task.meta);
			p.setName("Pushing root container for keys " + task.data.keySet());
			p.addTotal(1, false);
			subsrl.pushLive(t, p);
			task.meta = t.meta;
			p.addPartDone();
		}

	}


	/************************************************************************
	** Dummy serialiser.
	**
	** A SkeletonBTreeMap node was designed to hold values externally, whereas
	** (a B-tree node in the (BTreeSet container for a term's results)) and
	** (a B-tree node in the (BTreeMap container for a uri's results)) hold
	** them internally. This class simplifies the logic required; see the
	** source code of {@link SkeletonBTreeMap} for more details.
	**
	** @deprecated Avoid using this class. It will be removed after option 1
	**             (see the source code of {@link SkeletonBTreeMap}) is coded.
	** @author infinity0
	*/
	public static class DummySerialiser<K, T>
	implements IterableSerialiser<T>,
	           MapSerialiser<K, T> {


		public void pull(PullTask<T> task) {
			task.data = (T)task.meta;
		}

		public void push(PushTask<T> task) {
			task.meta = task.data;
		}

		public void pull(Iterable<PullTask<T>> tasks) {
			for (PullTask<T> task: tasks) {
				task.data = (T)task.meta;
			}
		}

		public void push(Iterable<PushTask<T>> tasks) {
			for (PushTask<T> task: tasks) {
				task.meta = task.data;
			}
		}

		public void pull(Map<K, PullTask<T>> tasks, Object mapmeta) {
			for (PullTask<T> task: tasks.values()) {
				task.data = (T)task.meta;
			}
		}

		public void push(Map<K, PushTask<T>> tasks, Object mapmeta) {
			for (PushTask<T> task: tasks.values()) {
				task.meta = task.data;
			}
		}

	}



}
