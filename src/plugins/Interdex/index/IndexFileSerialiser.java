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

import java.io.*;
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
/*
	public Serialiser<SkeletonPrefixTreeMap<Token, TokenURIEntry>> s;
	public Serialiser<SkeletonTreeMap<Token, TokenURIEntry>> sl;
	public Serialiser<TokenURIEntry> sv;


	public IndexFileSerialiser() {
		s = new PrefixTreeMapSerialiser<Token, TokenURIEntry>();
		sl = new TreeMapSerialiser<Token, TokenURIEntry>();
		sv = new TokenURIEntrySerialiser();
		//
	}

	public class PrefixTreeMapSerialiser<K extends PrefixKey, V> extends YamlSerialiser<SkeletonPrefixTreeMap<K, V>> {

		public PrefixTreeMapSerialiser() {
		}

		public PullTask<SkeletonPrefixTreeMap<K, V>> makePullTask(Dummy<SkeletonPrefixTreeMap<K, V>> o) {
			throw new UnsupportedOperationException("Not implemented.");
		}

		public PushTask<SkeletonPrefixTreeMap<K, V>> makePushTask(SkeletonPrefixTreeMap<K, V> tr) {
			return new PushPrefixTreeMapTask(tr);
		}

		public class PushPrefixTreeMapTask extends YamlPushTask {

			public PushPrefixTreeMapTask(SkeletonPrefixTreeMap<K, V> skel) {
				SkeletonPrefixTreeMap.pushMap(this, skel);
				super.putDummy(new FileDummy(skel.prefixString()));
			}

		}

	}


	public class TreeMapSerialiser<K extends PrefixKey, V> extends AbstractSerialiser<SkeletonTreeMap<K, V>> {

		public PullTask<SkeletonTreeMap<K, V>> makePullTask(Dummy<SkeletonTreeMap<K, V>> o) {
			throw new UnsupportedOperationException("Not implemented.");
		}

		public PushTask<SkeletonTreeMap<K, V>> makePushTask(SkeletonTreeMap<K, V> tr) {
			return new PushTreeMapTask(tr);
		}

		public class PushTreeMapTask extends YamlPushTask {

			public PushTreeMapTask(SkeletonTreeMap<K, V> map) {
				SkeletonTreeMap.pushMap(this, map);
			}

		}

	}
*/

	/**
	** This serialiser creates a Map that is a serialised version of
	** TokenURIEntry.
	*/
	public static class TokenURIEntrySerialiser extends AbstractSerialiser<TokenURIEntry, Map<String, Object>> {

		protected static String[] keys = new String[]{"word", "_uri", "position", "relevance"};

		public TokenURIEntrySerialiser() {
			arch = new DummyArchiver<Map<String, Object>>();
			trans = new TokenURIEntryTranslator();
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
