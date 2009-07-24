/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import plugins.Interdex.util.SkeletonTreeMap;
import plugins.Interdex.util.SkeletonBTreeMap;

import plugins.Interdex.serl.Serialiser.*;
import plugins.Interdex.serl.Serialiser;
import plugins.Interdex.serl.Translator;
import plugins.Interdex.serl.ProgressTracker;
import plugins.Interdex.serl.Archiver;
import plugins.Interdex.serl.IterableSerialiser;
import plugins.Interdex.serl.MapSerialiser;
import plugins.Interdex.serl.ParallelSerialiser;
import plugins.Interdex.serl.CollectionPacker;
import plugins.Interdex.serl.MapPacker;
import plugins.Interdex.serl.Progress;
import plugins.Interdex.serl.AtomicProgress;
import plugins.Interdex.serl.CompoundProgress;
import plugins.Interdex.serl.YamlArchiver;
import plugins.Interdex.serl.DataFormatException;
import plugins.Interdex.serl.TaskAbortException;

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


	final public static int TMBIN_MAX = 256;


	final protected Archiver<Map<String, Object>> subsrl;
	final protected Translator<ProtoIndex, Map<String, Object>> trans;



	/**
	** Generic map translator, for both utab and tmtab
	*/
	public static class TreeMapTranslator<K, V> implements Translator<SkeletonTreeMap<K, V>, Map<String, Object>> {

		final protected Translator<K, String> ktr;

		public TreeMapTranslator(Translator<K, String> k) {
			ktr = k;
		}

		@Override public Map<String, Object> app(SkeletonTreeMap<K, V> map) {
			return SkeletonTreeMap.TreeMapTranslator.app(map, new TreeMap<String, Object>(), ktr);
		}

		@Override public SkeletonTreeMap<K, V> rev(Map<String, Object> map) {
			return SkeletonTreeMap.TreeMapTranslator.rev(map, new SkeletonTreeMap<K, V>(), ktr);
		}
	}

	public BIndexSerialiser() {
		//subsrl = new YamlArchiver<Map<String, Object>>("index", "");
		// for DEBUG use; the freenet version would save to SSK@key/my_index/index.yml
		subsrl = new YamlArchiver<Map<String, Object>>(true);
		//trans = new IndexTranslator();
		//tmsrl = new BTreeMapSerialiser<String, SortedSet<TokenEntry>>();
		//usrl = new BTreeMapSerialiser<URIKey, SortedMap<FreenetURI, URIEntry>>(new URIKeyTranslator());
		trans = new IndexTranslator();
	}



	public static ProtoIndex setSerialiserFor(ProtoIndex index) {
		// TODO utab too
		index.tmtab.setSerialiser(new BTreeNodeSerialiser<String, SortedSet<TokenEntry>>(index.tmtab, null),
		                          new TermEntrySerialiser());
		return index;
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
		Translator<SkeletonBTreeMap<String, SortedSet<TokenEntry>>, Map<String, Object>> tmtrans = new
		SkeletonBTreeMap.TreeTranslator<String, SortedSet<TokenEntry>>(null, new
		TreeMapTranslator<String, SortedSet<TokenEntry>>(null));

		// URI-table translator too...

		@Override public Map<String, Object> app(ProtoIndex idx) {
			if (!idx.tmtab.isBare() /* || !idx.utab.isBare() */) {
				throw new IllegalArgumentException("Data structure is not bare. Try calling deflate() first.");
			}
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			map.put("MAGIC", idx.getMagic());
			map.put("id", idx.id);
			map.put("name", idx.name);
			map.put("modified", idx.modified);
			map.put("extra", idx.extra);
			// these are meant to be removed by the parent Serialiser and pushed
			//map.put("utab", idx.utab);
			map.put("tmtab", tmtrans.app(idx.tmtab));
			return map;
		}

		@Override public ProtoIndex rev(Map<String, Object> map) {
			long magic = (Long)map.get("MAGIC");

			if (magic == ProtoIndex.MAGIC) {
				try {
					FreenetURI id = (FreenetURI)map.get("id");
					String name = (String)map.get("name");
					Date modified = (Date)map.get("modified");
					Map<String, Object> extra = (Map<String, Object>)map.get("extra");
					//SkeletonBTreeMap<URIKey, SortedMap<FreenetURI, URIEntry>> utab = (SkeletonBTreeMap<URIKey, SortedMap<FreenetURI, URIEntry>>)map.get("utab");
					SkeletonBTreeMap<String, SortedSet<TokenEntry>> tmtab = tmtrans.rev((Map<String, Object>)map.get("tmtab"));

					return setSerialiserFor(new ProtoIndex(id, name, modified, extra, /*utab, */tmtab));

				} catch (ClassCastException e) {
					// TODO maybe find a way to pass the actual bad data to the exception
					throw new DataFormatException("Badly formatted data: " + e, e, null);
				}

			} else {
				throw new DataFormatException("Unrecognised magic number", magic, map, "magic");
			}
		}

	}






	public static class BTreeNodeSerialiser<K, V>
	extends ParallelSerialiser<SkeletonBTreeMap<K, V>.SkeletonNode, AtomicProgress>
	implements Archiver<SkeletonBTreeMap<K, V>.SkeletonNode>,
	           Serialiser.Translate<SkeletonBTreeMap<K, V>.SkeletonNode, Map<String, Object>>,
	           Serialiser.Composite<Archiver<Map<String, Object>>> {

		final protected Translator<SkeletonBTreeMap<K, V>.SkeletonNode, Map<String, Object>> trans;
		final protected Archiver<Map<String, Object>> subsrl;

		public BTreeNodeSerialiser(SkeletonBTreeMap<K, V> btreemap, final Translator<K, String> ktr) {
			super(new ProgressTracker<SkeletonBTreeMap<K, V>.SkeletonNode, AtomicProgress>(AtomicProgress.class));
			subsrl = new YamlArchiver<Map<String, Object>>(true);
			trans = btreemap.makeNodeTranslator(ktr, new TreeMapTranslator<K, V>(null));
		}

		@Override public Translator<SkeletonBTreeMap<K, V>.SkeletonNode, Map<String, Object>> getTranslator() {
			return trans;
		}

		@Override public Archiver<Map<String, Object>> getChildSerialiser() {
			return subsrl;
		}

		@Override public void pullAndUpdateProgress(PullTask<SkeletonBTreeMap<K, V>.SkeletonNode> task, AtomicProgress p) {
			SkeletonBTreeMap<K, V>.GhostNode ghost = (SkeletonBTreeMap.GhostNode)task.meta;
			PullTask<Map<String, Object>> serialisable = new PullTask<Map<String, Object>>(ghost.getMeta());
			p.setName("Pulling listings for " + ghost.getShortName());
			try {
				subsrl.pull(serialisable);
				ghost.setMeta(serialisable.meta); task.data = trans.rev(serialisable.data);
				p.setDone();
			} catch (TaskAbortException e) {
				p.setAbort(e);
			}
		}

		@Override public void pushAndUpdateProgress(PushTask<SkeletonBTreeMap<K, V>.SkeletonNode> task, AtomicProgress p) {
			Map<String, Object> intermediate = trans.app(task.data);
			PushTask<Map<String, Object>> serialisable = new PushTask<Map<String, Object>>(intermediate, task.meta);
			p.setName("Pushing listings for " + task.data.getShortName());
			try {
				subsrl.push(serialisable);
				task.meta = task.data.makeGhost(serialisable.meta);
				p.setDone();
			} catch (TaskAbortException e) {
				p.setAbort(e);
			}
		}

	}





	public static class TermEntrySerialiser
	extends CollectionPacker<String, SortedSet<TokenEntry>>
	implements MapSerialiser<String, SortedSet<TokenEntry>>,
	           Serialiser.Trackable<SortedSet<TokenEntry>> {

		final protected ProgressTracker<SortedSet<TokenEntry>, CompoundProgress> tracker;
		final protected TermEntryGroupSerialiser subsrl;

		public TermEntrySerialiser() {
			super(new TermEntryGroupSerialiser(), TMBIN_MAX, (Class<TreeSet<TokenEntry>>)((new TreeSet<TokenEntry>()).getClass()));
			subsrl = (TermEntryGroupSerialiser)super.subsrl;
			tracker = new ProgressTracker<SortedSet<TokenEntry>, CompoundProgress>(CompoundProgress.class);
		}

		@Override public ProgressTracker<SortedSet<TokenEntry>, ? extends Progress> getTracker() {
			return tracker;
		}

		@Override protected void preprocessPullBins(Map<String, PullTask<SortedSet<TokenEntry>>> tasks, Collection<PullTask<Map<String, SortedSet<TokenEntry>>>> bintasks) {
			List<Object> mib = new ArrayList<Object>(bintasks.size());
			for (PullTask<Map<String, SortedSet<TokenEntry>>> t: bintasks) {
				mib.add(t.meta);
			}

			for (PullTask<SortedSet<TokenEntry>> t: tasks.values()) {
				CompoundProgress p = tracker.addPullProgress(t.meta);
				if (p != null) { p.setSubprogress(subsrl.getTracker().iterableOfPull(mib)); }
			}
		}

		@Override protected void preprocessPushBins(Map<String, PushTask<SortedSet<TokenEntry>>> tasks, Collection<PushTask<Map<String, SortedSet<TokenEntry>>>> bintasks) {
			List<Map<String, SortedSet<TokenEntry>>> dib = new ArrayList<Map<String, SortedSet<TokenEntry>>>(bintasks.size());
			for (PushTask<Map<String, SortedSet<TokenEntry>>> t: bintasks) {
				dib.add(t.data);
			}

			for (PushTask<SortedSet<TokenEntry>> t: tasks.values()) {
				CompoundProgress p = tracker.addPushProgress(t.data);
				if (p != null) { p.setSubprogress(subsrl.getTracker().iterableOfPush(dib)); }
			}
		}

	}



	public static class TermEntryGroupSerialiser
	extends ParallelSerialiser<Map<String, SortedSet<TokenEntry>>, AtomicProgress>
	implements IterableSerialiser<Map<String, SortedSet<TokenEntry>>>,
	           Serialiser.Composite<Archiver<Map<String, Object>>>,
	           Serialiser.Trackable<Map<String, SortedSet<TokenEntry>>> {

		final protected Archiver<Map<String, Object>> subsrl;

		public TermEntryGroupSerialiser() {
			super(new ProgressTracker<Map<String, SortedSet<TokenEntry>>, AtomicProgress>(AtomicProgress.class));
			subsrl = new YamlArchiver<Map<String, Object>>(true);
		}

		@Override public Archiver<Map<String, Object>> getChildSerialiser() {
			return subsrl;
		}

		@Override public ProgressTracker<Map<String, SortedSet<TokenEntry>>, ? extends Progress> getTracker() {
			return tracker;
		}

		@Override public void pullAndUpdateProgress(PullTask<Map<String, SortedSet<TokenEntry>>> task, AtomicProgress p) {
			PullTask<Map<String, Object>> t = new PullTask<Map<String, Object>>(task.meta);
			p.setName("Pulling container " + task.meta);
			try {
				subsrl.pull(t);

				Map<String, SortedSet<TokenEntry>> map = new HashMap<String, SortedSet<TokenEntry>>(t.data.size()<<1);
				try {
					for (Map.Entry<String, Object> en: t.data.entrySet()) {
						SortedSet<TokenEntry> entries = new TreeSet<TokenEntry>();
						for (Object o: (List)en.getValue()) {
							entries.add((TokenEntry)o);
						}
						map.put(en.getKey(), entries);
					}
				} catch (ClassCastException e) {
					// TODO more meaningful error message
					throw new DataFormatException("Exception in converting data", e, null, null, null);
				}

				task.data = map;
				p.setDone();
			} catch (TaskAbortException e) {
				p.setAbort(e);
			} catch (RuntimeException e) {
				p.setAbort(new TaskAbortException("Could not retrieve data from bin " + t.data.keySet(), e));
			}
		}

		@Override public void pushAndUpdateProgress(PushTask<Map<String, SortedSet<TokenEntry>>> task, AtomicProgress p) {
			Map<String, Object> conv = new HashMap<String, Object>();
			for (Map.Entry<String, SortedSet<TokenEntry>> mp: task.data.entrySet()) {
				conv.put(mp.getKey(), new ArrayList(mp.getValue()));
			}

			PushTask<Map<String, Object>> t = new PushTask<Map<String, Object>>(conv, task.meta);
			p.setName("Pushing container for keys " + task.data.keySet());
			try {
				subsrl.push(t);
				task.meta = t.meta;
				p.setDone();
			} catch (TaskAbortException e) {
				p.setAbort(e);
			}
		}

	}



}
