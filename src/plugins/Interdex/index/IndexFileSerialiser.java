/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import plugins.Interdex.util.PrefixTree.PrefixKey;
import plugins.Interdex.util.SkeletonPrefixTreeMap;
import plugins.Interdex.util.SkeletonTreeMap;

import plugins.Interdex.serl.Serialiser.*;
import plugins.Interdex.serl.Serialiser;
import plugins.Interdex.serl.Translator;
import plugins.Interdex.serl.ProgressTracker;
import plugins.Interdex.serl.Archiver;
import plugins.Interdex.serl.IterableSerialiser;
import plugins.Interdex.serl.MapSerialiser;
import plugins.Interdex.serl.ParallelArchiver;
import plugins.Interdex.serl.CollectionPacker;
import plugins.Interdex.serl.MapPacker;
import plugins.Interdex.serl.Progress;
import plugins.Interdex.serl.AtomicProgress;
import plugins.Interdex.serl.CompoundProgress;
import plugins.Interdex.serl.YamlArchiver;
import plugins.Interdex.serl.DataFormatException;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.Date;

import freenet.keys.FreenetURI;

/**
** This class handles serialisation of an Index into a filetree.
**
** TODO: atm this is an utter mess, it was coded in a rush as a proof-of-concept
**
** TODO: could have this class load balance itself by having a queue of threads
** in the main class.
**
** DOCUMENT
**
** OPTIMISE: make Token and URIKey use halfbytes instead of bytes, and make this
** serialiser save 3 levels of PrefixTrees into the same file. Or something.
**
** @author infinity0
*/
public class IndexFileSerialiser
implements Archiver<Index>,
           Serialiser.Composite<Archiver<Map<String, Object>>>,
           Serialiser.Translate<Index, Map<String, Object>>/*,
           Serialiser.Trackable<Index>*/ {

	final protected static int UBIN_MAX = 512;
	final protected static int TKBIN_MAX = 512;

	// TODO
	// when doing this to/from freenet, we'll have to have two extra fields
	// for the request/insert URIs

	final protected Archiver<Map<String, Object>> subsrl;
	final protected Translator<Index, Map<String, Object>> trans;

	PrefixTreeMapSerialiser<Token, SortedSet<TokenEntry>> tksrl;
	PrefixTreeMapSerialiser<URIKey, SortedMap<FreenetURI, URIEntry>> usrl;

	public IndexFileSerialiser() {
		subsrl = new YamlArchiver<Map<String, Object>>("index", "");
		trans = new IndexTranslator();
		tksrl = new PrefixTreeMapSerialiser<Token, SortedSet<TokenEntry>>(new TokenTranslator());
		usrl = new PrefixTreeMapSerialiser<URIKey, SortedMap<FreenetURI, URIEntry>>(new URIKeyTranslator());
	}

	@Override public Archiver<Map<String, Object>> getChildSerialiser() {
		return subsrl;
	}

	@Override public Translator<Index, Map<String, Object>> getTranslator() {
		return trans;
	}

	@Override public void pull(PullTask<Index> task) {
		// I hate Java.
		PullTask<Map<String, Object>> subtask;
		PullTask<SkeletonPrefixTreeMap<URIKey, SortedMap<FreenetURI, URIEntry>>> utask;
		PullTask<SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>> tktask;
		subsrl.pull(subtask = new PullTask<Map<String, Object>>(null));
		usrl.pull(utask = new PullTask<SkeletonPrefixTreeMap<URIKey, SortedMap<FreenetURI, URIEntry>>>(null));
		tksrl.pull(tktask = new PullTask<SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>>(null));

		subtask.data.put("tktab", tktask.data);
		subtask.data.put("utab", utask.data);
		task.data = trans.rev(subtask.data);
	}

	@Override public void push(PushTask<Index> task) {
		// I hate Java.
		Map<String, Object> intermediate = trans.app(task.data);

		tksrl.push(new PushTask<SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>>((SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>)intermediate.remove("tktab")));
		usrl.push(new PushTask<SkeletonPrefixTreeMap<URIKey, SortedMap<FreenetURI, URIEntry>>>((SkeletonPrefixTreeMap<URIKey, SortedMap<FreenetURI, URIEntry>>)intermediate.remove("utab")));
		subsrl.push(new PushTask<Map<String, Object>>(intermediate));
	}



	public static class IndexTranslator
	implements Translator<Index, Map<String, Object>> {

		@Override public Map<String, Object> app(Index idx) {
			if (!idx.isBare()) {
				throw new IllegalArgumentException("Data structure is not bare. Try calling deflate() first.");
			}
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("MAGIC", idx.getMagic());
			map.put("id", idx.id);
			map.put("name", idx.name);
			map.put("modified", idx.modified);
			map.put("extra", idx.extra);
			// these are meant to be removed by the parent Serialiser and pushed
			map.put("utab", idx.utab);
			map.put("tktab", idx.tktab);
			return map;
		}

		@Override public Index rev(Map<String, Object> map) {
			long magic = (Long)map.get("MAGIC");

			if (magic == Index.MAGIC) {
				try {
					FreenetURI id = (FreenetURI)map.get("id");
					String name = (String)map.get("name");
					Date modified = (Date)map.get("modified");
					Map<String, Object> extra = (Map<String, Object>)map.get("extra");
					SkeletonPrefixTreeMap<URIKey, SortedMap<FreenetURI, URIEntry>> utab = (SkeletonPrefixTreeMap<URIKey, SortedMap<FreenetURI, URIEntry>>)map.get("utab");
					SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>> tktab = (SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>)map.get("tktab");

					return new Index(id, name, modified, extra, utab, tktab);

				} catch (ClassCastException e) {
					// TODO maybe find a way to pass the actual bad data to the exception
					throw new DataFormatException("Badly formatted data: " + e, e, null);
				}

			} else {
				throw new DataFormatException("Unrecognised magic number", magic, map, "magic");
			}
		}

	}



	public static class TokenTranslator implements Translator<Token, String> {
		@Override public String app(Token t) { return t.toString(); }
		@Override public Token rev(String s) { return Token.intern(new Token(Token.hexToBytes(s))); }
	}

	public static class URIKeyTranslator implements Translator<URIKey, String> {
		@Override public String app(URIKey k) { return k.toString(); }
		@Override public URIKey rev(String s) { return new URIKey(URIKey.hexToBytes(s)); }
	}




	public static class PrefixTreeMapSerialiser<K extends PrefixKey, V>
	extends ParallelArchiver<SkeletonPrefixTreeMap<K, V>>
	implements IterableSerialiser<SkeletonPrefixTreeMap<K, V>>,
	           Serialiser.Translate<SkeletonPrefixTreeMap<K, V>, Map<String, Object>>,
	           Serialiser.Composite<Archiver<Map<String, Object>>> {

		final protected Translator<SkeletonPrefixTreeMap<K, V>, Map<String, Object>> trans;
		final protected Archiver<Map<String, Object>> subsrl;
		final protected ProgressTracker<SkeletonPrefixTreeMap<K, V>, AtomicProgress> tracker;

		public PrefixTreeMapSerialiser(Translator<K, String> ktr) {
			super(new ProgressTracker<SkeletonPrefixTreeMap<K, V>, AtomicProgress>(AtomicProgress.class));
			subsrl = new YamlArchiver<Map<String, Object>>("tk", "");
			trans = new PrefixTreeMapTranslator<K, V>(ktr);
			tracker = (ProgressTracker<SkeletonPrefixTreeMap<K, V>, AtomicProgress>)super.tracker;
		}

		@Override public Translator<SkeletonPrefixTreeMap<K, V>, Map<String, Object>> getTranslator() {
			return trans;
		}

		@Override public Archiver<Map<String, Object>> getChildSerialiser() {
			return subsrl;
		}

		@Override public void pull(PullTask<SkeletonPrefixTreeMap<K, V>> task) {
			PullTask<Map<String, Object>> serialisable = new PullTask<Map<String, Object>>(task.meta);
			subsrl.pull(serialisable);
			SkeletonPrefixTreeMap<K, V> pulldata = trans.rev(serialisable.data);

			task.meta = serialisable.meta; task.data = pulldata;
			AtomicProgress p = tracker.getPullProgress(task.meta);
			if (p != null) { p.setDone(); }
		}

		@Override public void push(PushTask<SkeletonPrefixTreeMap<K, V>> task) {
			if (!task.data.isBare()) {
				throw new IllegalArgumentException("Data structure is not bare. Try calling deflate() first.");
			}
			if (task.meta == null) {
				task.meta = task.data.getMeta();
			}
			Map<String, Object> intermediate = trans.app(task.data);
			PushTask<Map<String, Object>> serialisable = new PushTask<Map<String, Object>>(intermediate, task.meta);
			subsrl.push(serialisable);

			task.meta = serialisable.meta;
			AtomicProgress p = tracker.getPushProgress(task.data);
			if (p != null) { p.setDone(); }
		}

		public static class PrefixTreeMapTranslator<K extends PrefixKey, V>
		extends SkeletonPrefixTreeMap.PrefixTreeMapTranslator<K, V> {

			final protected Translator<K, String> ktr;

			public PrefixTreeMapTranslator(Translator<K, String> k) {
				ktr = k;
			}

			@Override public Map<String, Object> app(SkeletonPrefixTreeMap<K, V> tree) {
				Map<String, Object> map = new HashMap<String, Object>(16);
				Map<String, Object> lmap = new HashMap<String, Object>(tree.sizeLocal()<<1);
				app(tree, map, lmap, null);
				// SnakeYAML can't handle arrays of primitives
				map.put("_child", refListOf((boolean[])map.get("_child")));
				map.put("sizePrefix", refListOf((int[])map.get("sizePrefix")));
				return map;
			}

			@Override public SkeletonPrefixTreeMap<K, V> rev(Map<String, Object> map) {
				// SnakeYAML can't handle arrays of primitives
				map.put("_child", boolArrayOf((List<Boolean>)map.get("_child")));
				map.put("sizePrefix", intArrayOf((List<Integer>)map.get("sizePrefix")));

				return rev(map, ktr);
			}

			// TODO maybe move this into a util class?
			public static List<Boolean> refListOf(boolean[] arr) {
				List<Boolean> list = new ArrayList<Boolean>(arr.length);
				for (boolean e: arr) { list.add((e)? Boolean.TRUE: Boolean.FALSE); }
				return list;
			}

			public static List<Integer> refListOf(int[] arr) {
				List<Integer> list = new ArrayList<Integer>(arr.length);
				for (int e: arr) { list.add(new Integer(e)); }
				return list;
			}

			public static boolean[] boolArrayOf(List<Boolean> list) {
				boolean[] arr = new boolean[list.size()];
				int i=0; for (Boolean e: list) { arr[i++] = e; }
				return arr;
			}

			public static int[] intArrayOf(List<Integer> list) {
				int[] arr = new int[list.size()];
				int i=0; for (Integer e: list) { arr[i++] = e; }
				return arr;
			}

		}

	}





	abstract public static class URIEntrySerialiser
	/*extends MapPacker<URIKey, SortedMap<FreenetURI, URIEntry>, URIEntryGroupSerialiser>
	implements MapSerialiser<URIKey, SortedMap<FreenetURI, URIEntry>>*/ {

		public URIEntrySerialiser() {
			// java compiler is retarded, see below
			//super(new URIEntryGroupSerialiser(), UBIN_MAX, (Class<TreeMap<FreenetURI, URIEntry>>)((new TreeMap<FreenetURI, URIEntry>()).getClass()));
		}
	}

	public static class URIEntryGroupSerialiser
	/*extends ParallelArchiver<Map<URIKey, SortedMap<FreenetURI, URIEntry>>, Map<String, Object>>
	implements IterableSerialiser<Map<URIKey, SortedMap<FreenetURI, URIEntry>>>*/ {

		public URIEntryGroupSerialiser() {
			//super(new YamlArchiver<Map<String, Object>>("u", ".tab"), new ProgressTracker(null)); // URGENT
		}

		public void pull(PullTask<Map<URIKey, SortedMap<FreenetURI, URIEntry>>> task) {
			// URGENT
			/*PullTask<Map<String, Object>> t = new PullTask<Map<String, Object>>(task.meta);
			subsrl.pull(t);

			Map<Token, SortedSet<TokenEntry>> map = new HashMap<Token, SortedSet<TokenEntry>>(t.data.size()<<1);
			try {
				for (Map.Entry<String, Object> en: t.data.entrySet()) {
					SortedSet<TokenEntry> entries = new TreeSet<TokenEntry>();
					for (Object o: (List)en.getValue()) {
						entries.add((TokenEntry)o);
					}
					map.put(Token.intern(en.getKey()), entries);
				}
			} catch (ClassCastException e) {
				// TODO more meaningful error message
				throw new DataFormatException("could not retrieve data from bin", null);
			}

			task.data = map;*/
		}

		public void push(PushTask<Map<URIKey, SortedMap<FreenetURI, URIEntry>>> task) {
			/*Map<String, Object> conv = new HashMap<String, Object>();
			for (Map.Entry<Token, SortedSet<TokenEntry>> mp: task.data.entrySet()) {
				List entries = new ArrayList();
				for (TokenEntry en: mp.getValue()) {
					entries.add(en);
				}
				conv.put(mp.getKey().toString(), entries);
			}

			PushTask<Map<String, Object>> t = new PushTask<Map<String, Object>>(conv, task.meta);
			subsrl.push(t);
			task.meta = t.meta;*/
		}

		public void pull(Iterable<PullTask<Map<URIKey, SortedMap<FreenetURI, URIEntry>>>> tasks) {
			// TODO threads
			for (PullTask<Map<URIKey, SortedMap<FreenetURI, URIEntry>>> task: tasks) {
				pull(task);
			}
		}

		public void push(Iterable<PushTask<Map<URIKey, SortedMap<FreenetURI, URIEntry>>>> tasks) {
			for (PushTask<Map<URIKey, SortedMap<FreenetURI, URIEntry>>> task: tasks) {
				push(task);
			}
		}

	}


	public static class TokenEntrySerialiser
	extends CollectionPacker<Token, SortedSet<TokenEntry>>
	implements MapSerialiser<Token, SortedSet<TokenEntry>>,
	           Serialiser.Trackable<SortedSet<TokenEntry>> {

		final protected ProgressTracker<SortedSet<TokenEntry>, CompoundProgress> tracker;
		final protected TokenEntryGroupSerialiser subsrl;

		public TokenEntrySerialiser() {
			// java compiler is retarded; the following doesn't work:
			// - TreeSet.class
			// - TreeSet<TokenEntry>.class (syntax error)
			// - (new TreeSet<TokenEntry>()).getClass()
			// - (Class<TreeSet<TokenEntry>>)(TreeSet.class)
			super(new TokenEntryGroupSerialiser(), TKBIN_MAX, (Class<TreeSet<TokenEntry>>)((new TreeSet<TokenEntry>()).getClass()));
			subsrl = (TokenEntryGroupSerialiser)super.subsrl;
			tracker = new ProgressTracker<SortedSet<TokenEntry>, CompoundProgress>(CompoundProgress.class);
		}

		@Override public ProgressTracker<SortedSet<TokenEntry>, CompoundProgress> getTracker() {
			return tracker;
		}

		/**
		** {@inheritDoc}
		**
		** This implementation associates each task metadata with all the
		** metadata of every bintask. This allows {@link ProgressTracker#getPullProgress(Object)}
		** to report the accumulated progress of the whole set of tasks, by
		** querying the progress of all the bintasks.
		*/
		@Override protected void preprocessPullBins(Map<Token, PullTask<SortedSet<TokenEntry>>> tasks, Collection<PullTask<Map<Token, SortedSet<TokenEntry>>>> bintasks) {
			List<Object> mib = new ArrayList<Object>(bintasks.size());
			for (PullTask<Map<Token, SortedSet<TokenEntry>>> t: bintasks) {
				mib.add(t.meta);
			}

			for (PullTask<SortedSet<TokenEntry>> t: tasks.values()) {
				CompoundProgress p = tracker.addPullProgress(t.meta);
				if (p != null) { p.setSubprogress(subsrl.getTracker().iterableOfPull(mib)); }
			}
		}

		/**
		** {@inheritDoc}
		**
		** This implementation associates each task metadata with all the
		** metadata of every bintask. This allows {@link ProgressTracker#getPushProgress(Object)}
		** to report the accumulated progress of the whole set of tasks, by
		** querying the progress of all the bintasks.
		*/
		@Override protected void preprocessPushBins(Map<Token, PushTask<SortedSet<TokenEntry>>> tasks, Collection<PushTask<Map<Token, SortedSet<TokenEntry>>>> bintasks) {
			List<Map<Token, SortedSet<TokenEntry>>> dib = new ArrayList<Map<Token, SortedSet<TokenEntry>>>(bintasks.size());
			for (PushTask<Map<Token, SortedSet<TokenEntry>>> t: bintasks) {
				dib.add(t.data);
			}

			for (PushTask<SortedSet<TokenEntry>> t: tasks.values()) {
				CompoundProgress p = (CompoundProgress)tracker.addPushProgress(t.data);
				if (p != null) { p.setSubprogress(subsrl.getTracker().iterableOfPush(dib)); }
			}
		}

	}

	public static class TokenEntryGroupSerialiser
	extends ParallelArchiver<Map<Token, SortedSet<TokenEntry>>>
	implements IterableSerialiser<Map<Token, SortedSet<TokenEntry>>>,
	           Serialiser.Composite<Archiver<Map<String, Object>>>,
	           Serialiser.Trackable<Map<Token, SortedSet<TokenEntry>>> {

		final protected ProgressTracker<Map<Token, SortedSet<TokenEntry>>, AtomicProgress> tracker;
		final protected Archiver<Map<String, Object>> subsrl;

		public TokenEntryGroupSerialiser() {
			super(new ProgressTracker<Map<Token, SortedSet<TokenEntry>>, AtomicProgress>(AtomicProgress.class));
			subsrl = new YamlArchiver<Map<String, Object>>("tk", ".tab");
			tracker = (ProgressTracker<Map<Token, SortedSet<TokenEntry>>, AtomicProgress>)super.tracker;
		}

		@Override public Archiver<Map<String, Object>> getChildSerialiser() {
			return subsrl;
		}

		@Override public ProgressTracker<Map<Token, SortedSet<TokenEntry>>, AtomicProgress> getTracker() {
			return tracker;
		}

		@Override public void pull(PullTask<Map<Token, SortedSet<TokenEntry>>> task) {
			PullTask<Map<String, Object>> t = new PullTask<Map<String, Object>>(task.meta);
			subsrl.pull(t);

			Map<Token, SortedSet<TokenEntry>> map = new HashMap<Token, SortedSet<TokenEntry>>(t.data.size()<<1);
			try {
				for (Map.Entry<String, Object> en: t.data.entrySet()) {
					SortedSet<TokenEntry> entries = new TreeSet<TokenEntry>();
					for (Object o: (List)en.getValue()) {
						entries.add((TokenEntry)o);
					}
					map.put(Token.intern(new Token(Token.hexToBytes(en.getKey()))), entries);
				}
			} catch (ClassCastException e) {
				// TODO more meaningful error message
				throw new DataFormatException("could not retrieve data from bin " + t.data.keySet(), e, null, null, null);
			}

			task.data = map;
			AtomicProgress p = tracker.getPullProgress(task.meta);
			if (p != null) { p.setDone(); }
		}

		@Override public void push(PushTask<Map<Token, SortedSet<TokenEntry>>> task) {
			Map<String, Object> conv = new HashMap<String, Object>();
			for (Map.Entry<Token, SortedSet<TokenEntry>> mp: task.data.entrySet()) {
				List entries = new ArrayList();
				for (TokenEntry en: mp.getValue()) {
					entries.add(en);
				}
				conv.put(mp.getKey().toString(), entries);
			}

			PushTask<Map<String, Object>> t = new PushTask<Map<String, Object>>(conv, task.meta);
			subsrl.push(t);
			task.meta = t.meta;
			AtomicProgress p = tracker.getPushProgress(task.data);
			if (p != null) { p.setDone(); }
		}

	}


	/* *
	** This Archiver just copies the dummy to the data and vice-versa. Seems
	** pointless, but can be useful in conjunction with a {@link Translator}
	** inside an {@link AbstractSerialiser}.
	*//*
	public static class DummyArchiver<T> implements Archiver<T> {

		public void pull(PullTask<T> t) {
			t.data = (T)(t.meta);
		}

		public void push(PushTask<T> t) {
			t.meta = t.data;
		}

	}*/


}
