/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import plugins.Interdex.util.PrefixTree.PrefixKey;
import plugins.Interdex.util.SkeletonPrefixTreeMap;
import plugins.Interdex.util.SkeletonTreeMap;

import plugins.Interdex.serl.Serialiser.*;
import plugins.Interdex.serl.Translator;
import plugins.Interdex.serl.Archiver;
import plugins.Interdex.serl.IterableSerialiser;
import plugins.Interdex.serl.MapSerialiser;
import plugins.Interdex.serl.CompositeSerialiser;
import plugins.Interdex.serl.CompositeArchiver;
import plugins.Interdex.serl.YamlArchiver;

import java.util.HashMap;
import java.util.Map;

/**
** This class handles serialisation of an Index into a filetree.
**
** TODO: atm this is an utter mess, it was coded in a rush as a proof-of-concept
**
** TODO: could have this class load balance itself by having a queue of threads
** in the main class.
**
**
** @author infinity0
*/
public class IndexFileSerialiser /*implements Serialiser<Index>*/ {

	public IterableSerialiser<SkeletonPrefixTreeMap<Token, TokenURIEntry>> s;
	public MapSerialiser<TokenURIEntry> sv;

	public IndexFileSerialiser() {
		s = new PrefixTreeMapSerialiser<Token, TokenURIEntry>();
		sv = new TokenURIEntrySerialiser();
		//
	}

	public static class PrefixTreeMapSerialiser<K extends PrefixKey, V>
	extends CompositeArchiver<SkeletonPrefixTreeMap<K, V>, Map<String, Object>>
	implements IterableSerialiser<SkeletonPrefixTreeMap<K, V>> {

		public PrefixTreeMapSerialiser() {
			super(new YamlArchiver<Map<String, Object>>("tk_", ""), new PrefixTreeMapTranslator<K, V>());
		}

		public void push(PushTask<SkeletonPrefixTreeMap<K, V>> task) {
			if (task.meta == null) {
				task.meta = task.data.getMeta();
			}
			super.push(task);
		}

		public void pull(Iterable<PullTask<SkeletonPrefixTreeMap<K, V>>> tasks) {
			for (PullTask<SkeletonPrefixTreeMap<K, V>> task: tasks) {
				pull(task);
			}
		}

		public void push(Iterable<PushTask<SkeletonPrefixTreeMap<K, V>>> tasks) {
			for (PushTask<SkeletonPrefixTreeMap<K, V>> task: tasks) {
				push(task);
			}
		}

		public static class PrefixTreeMapTranslator<K extends PrefixKey, V> extends SkeletonPrefixTreeMap.PrefixTreeMapTranslator<K, V> {

			public SkeletonPrefixTreeMap<K, V> rev(Map<String, Object> map) {
				throw new UnsupportedOperationException("Not implemented.");
			}

			public Map<String, Object> app(SkeletonPrefixTreeMap<K, V> tree) {
				Map<String, Object> map = new HashMap<String, Object>(16);
				Map<String, Object> lmap = new HashMap<String, Object>(tree.sizeLocal()*2);
				app(tree, lmap, map);
				return map;
			}

		}

	}

	/**
	** This serialiser creates a Map that is a serialised version of
	** TokenURIEntry.
	**
	** DOCUMENT
	*/
	public static class TokenURIEntrySerialiser
	extends CompositeSerialiser<TokenURIEntry, Map<String, Object>, Archiver<Map<String, Object>>>
	implements MapSerialiser<TokenURIEntry> {

		protected static String[] keys = new String[]{"word", "_uri", "position", "relevance"};

		public TokenURIEntrySerialiser() {
			super(new YamlArchiver<Map<String, Object>>("tk_", "_map"), new TokenURIEntryTranslator());
		}

		public <K> void pull(Map<K, PullTask<TokenURIEntry>> tasks, Object meta) {
			throw new UnsupportedOperationException("Not implemented.");
		}

		public <K> void push(Map<K, PushTask<TokenURIEntry>> tasks, Object meta) {
			// generate a single task for the archiver
			Map<String, Object> taskmap = new HashMap<String, Object>(tasks.size()*2);
			for (Map.Entry<K, PushTask<TokenURIEntry>> en: tasks.entrySet()) {
				taskmap.put(en.getKey().toString(), trans.app(en.getValue().data));
			}
			PushTask<Map<String, Object>> maptask = new PushTask<Map<String, Object>>(taskmap, meta);
			subsrl.push(maptask);

			// transfer metadata to the individual tasks
			for (PushTask<TokenURIEntry> task: tasks.values()) {
				task.meta = maptask.meta;
			}
		}

		public static class TokenURIEntryTranslator implements Translator<TokenURIEntry, Map<String, Object>> {

			public TokenURIEntry rev(Map<String, Object> t) {
				try {
					TokenURIEntry en = new TokenURIEntry((String)(t.get(keys[0])), (String)(t.get(keys[1])));
					en.position = (Integer)(t.get(keys[2]));
					en.relevance = (Integer)(t.get(keys[3]));
					return en;

				} catch (java.net.MalformedURLException e) {
					return null;
					// TODO have a CorruptData exception or something...
				} catch (ClassCastException e) {
					return null;
					// TODO have a CorruptData exception or something...
				}

			}

			public Map<String, Object> app(TokenURIEntry t) {
				Map<String, Object> map = new java.util.HashMap<String, Object>();
				map.put(keys[0], t.word);
				map.put(keys[1], t.uri.toString());
				map.put(keys[2], t.position);
				map.put(keys[3], t.relevance);

				return map;
			}

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
