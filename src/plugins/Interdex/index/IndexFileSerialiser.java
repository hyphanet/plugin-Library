/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import plugins.Interdex.util.PrefixTree.PrefixKey;
import plugins.Interdex.util.SkeletonPrefixTreeMap;
import plugins.Interdex.util.SkeletonTreeMap;
import plugins.Interdex.util.AbstractSerialiser;
import plugins.Interdex.util.Serialiser;
import plugins.Interdex.util.Translator;
import plugins.Interdex.util.Archiver;
import plugins.Interdex.util.Archiver.*;
import plugins.Interdex.util.YamlArchiver;

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

	public Serialiser<SkeletonPrefixTreeMap<Token, TokenURIEntry>> s;
	public Serialiser<TokenURIEntry> sv;

	public IndexFileSerialiser() {
		s = new PrefixTreeMapSerialiser<Token, TokenURIEntry>();
		sv = new TokenURIEntrySerialiser();
		//
	}

	public static class PrefixTreeMapSerialiser<K extends PrefixKey, V> extends AbstractSerialiser<SkeletonPrefixTreeMap<K, V>, Map<String, Object>> {

		public PrefixTreeMapSerialiser() {
			arch = new YamlArchiver<Map<String, Object>>("tk_", "");
			trans = new PrefixTreeMapTranslator<K, V>();
		}

		public void push(PushTask<SkeletonPrefixTreeMap<K, V>> task) {
			if (task.meta == null) {
				task.meta = task.data.getMeta();
			}
			super.push(task);
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
	public static class TokenURIEntrySerialiser extends AbstractSerialiser<TokenURIEntry, Map<String, Object>> {

		protected static String[] keys = new String[]{"word", "_uri", "position", "relevance"};

		public TokenURIEntrySerialiser() {
			arch = new YamlArchiver<Map<String, Object>>("tk_", "_map");
			trans = new TokenURIEntryTranslator();
		}

		public void pull(PullTask<TokenURIEntry> tasks) {
			throw new UnsupportedOperationException("Not supported.");
		}

		public void push(PushTask<TokenURIEntry> tasks) {
			throw new UnsupportedOperationException("Not supported.");
		}

		public void pull(Iterable<PullTask<TokenURIEntry>> tasks) {
			throw new UnsupportedOperationException("Not supported.");
		}

		public void push(Iterable<PushTask<TokenURIEntry>> tasks) {
			throw new UnsupportedOperationException("Not supported.");
		}

		public <K> void pull(Map<K, PullTask<TokenURIEntry>> tasks) {
			throw new UnsupportedOperationException("Not implemented.");
		}

		public <K> void push(Map<K, PushTask<TokenURIEntry>> tasks) {

			// map of metadata to combined-tasks
			Map<String, Map<String, Object>> mmap = new HashMap<String, Map<String, Object>>(4);

			// this map is necessary to get the task object for a given intermediate
			Map<Object, PushTask<TokenURIEntry>> omap = new HashMap<Object, PushTask<TokenURIEntry>>(tasks.size()*2);

			for (Map.Entry<K, PushTask<TokenURIEntry>> en: tasks.entrySet()) {
				PushTask<TokenURIEntry> task = en.getValue();
				String file = (String)task.meta;

				// find the combined-tasks map for the metadata for this task
				if (!mmap.containsKey(file)) {
					// OPTIMISE HashMap constructor
					int sizeEst = tasks.size()*2/(mmap.size()+1);
					mmap.put(file, new java.util.HashMap<String, Object>(sizeEst));
				}
				Map<String, Object> map = mmap.get(file);

				Object o = trans.app(task.data);
				omap.put(o, task);
				// put the task into the combined-tasks map
				map.put(en.getKey().toString(), o);
			}

			for (Map.Entry<String, Map<String, Object>> en: mmap.entrySet()) {
				// execute the combined task
				PushTask<Map<String, Object>> task = new PushTask<Map<String, Object>>(en.getValue(), en.getKey());
				arch.push(task);

				// transfer meta to the individual tasks
				for (Object v: task.data.values()) {
					omap.get(v).meta = task.meta;
				}

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

	/**
	** This Archiver just copies the dummy to the data and vice-versa. Seems
	** pointless, but can be useful in conjunction with a {@link Translator}
	** inside an {@link AbstractSerialiser}.
	*/
	public static class DummyArchiver<T> implements Archiver<T> {

		public void pull(PullTask<T> t) {
			t.data = (T)(t.meta);
		}

		public void push(PushTask<T> t) {
			t.meta = t.data;
		}

	}


}
