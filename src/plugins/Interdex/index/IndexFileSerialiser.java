/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import plugins.Interdex.util.PrefixTree.PrefixKey;
import plugins.Interdex.util.SkeletonPrefixTreeMap;
import plugins.Interdex.util.SkeletonTreeMap;
import plugins.Interdex.util.Serialiser;
import plugins.Interdex.util.Serialiser.InflateTask;
import plugins.Interdex.util.Serialiser.DeflateTask;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.HashMap;

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

	final Yaml yaml = new Yaml();

	public Serialiser<SkeletonPrefixTreeMap<Token, TokenURIEntry>> s;
	public Serialiser<SkeletonTreeMap<Token, TokenURIEntry>> sl;
	public Serialiser<TokenURIEntry> sv;


	public IndexFileSerialiser() {
		s = new PrefixTreeMapSerialiser<Token, TokenURIEntry>();
		sl = new TreeMapSerialiser<Token, TokenURIEntry>();
		sv = new TokenURIEntrySerialiser();
		//
	}

	public class PrefixTreeMapSerialiser<K extends PrefixKey, V> implements Serialiser<SkeletonPrefixTreeMap<K, V>> {


		public InflateTask<SkeletonPrefixTreeMap<K, V>> newInflateTask(Object o) {
			throw new UnsupportedOperationException("Not implemented.");
		}

		public DeflateTask<SkeletonPrefixTreeMap<K, V>> newDeflateTask(SkeletonPrefixTreeMap<K, V> tr) {
			return new DeflatePrefixTreeMapTask(tr);
		}

		public SkeletonPrefixTreeMap<K, V> inflate(Object dummy) {
			throw new UnsupportedOperationException("Not implemented.");
		}

		public Object deflate(SkeletonPrefixTreeMap<K, V> skel) {
			skel.deflate();
			DeflateTask de = new DeflatePrefixTreeMapTask(skel);
			de.put("prefix", skel.prefix.toString());
			de.put("preflen", skel.preflen);
			de.put("sizeMax", skel.sizeMax);
			de.put("size", skel.size);
			de.put("subtreesMax", skel.subtreesMax);
			//de.put("subtrees", subtrees);
			// TODO snakeYAML says "Arrays of primitives are not fully supported."
			//de.put("sizePrefix", sizePrefix);

			//Boolean chd[] = new Boolean[subtreesMax];
			//for (int i=0; i<subtreesMax; ++i) { chd[i] = (child[i] != null); }
			//de.put("_child", chd);
			//de.put("_tmap", itmap.keySet());
			//de.put("tmap", tmapdummy);

			de.start(); de.join();
			return de.get();
		}

		public class DeflatePrefixTreeMapTask extends YamlDeflateTask {

			final SkeletonPrefixTreeMap<K, V> tree;

			public DeflatePrefixTreeMapTask(SkeletonPrefixTreeMap<K, V> tr) {
				super(tr.prefixString());
				tree = tr;
			}

		}

	}



	public class TreeMapSerialiser<K extends PrefixKey, V> implements Serialiser<SkeletonTreeMap<K, V>> {


		public InflateTask<SkeletonTreeMap<K, V>> newInflateTask(Object o) {
			throw new UnsupportedOperationException("Not implemented.");
		}

		public DeflateTask<SkeletonTreeMap<K, V>> newDeflateTask(SkeletonTreeMap<K, V> tr) {
			return new DeflateTreeMapTask(tr);
		}

		public SkeletonTreeMap<K, V> inflate(Object dummy) {
			throw new UnsupportedOperationException("Not implemented.");
		}

		public Object deflate(SkeletonTreeMap<K, V> map) {
			DeflateTask de = new DeflateTreeMapTask(map);
			de.start(); de.join();
			return de.get();
		}

		public class DeflateTreeMapTask extends YamlDeflateTask {

			public DeflateTreeMapTask(SkeletonTreeMap<K, V> m) {
				java.util.Map<K, V> mm = m.dummyValues();
				for (K k: mm.keySet()) {
					put(k.toString(), mm.get(k));
				}
			}

			public void setOption(Object o) {
				super.setOption(o + "_map");
			}

		}

	}



	public class TokenURIEntrySerialiser implements Serialiser<TokenURIEntry> {


		public InflateTask<TokenURIEntry> newInflateTask(Object o) {
			throw new UnsupportedOperationException("Not implemented.");
		}

		public DeflateTask<TokenURIEntry> newDeflateTask(TokenURIEntry en) {
			return new DeflateTokenURIEntryTask(en);
		}

		public TokenURIEntry inflate(Object dummy) {
			throw new UnsupportedOperationException("Not implemented.");
		}

		public Object deflate(TokenURIEntry t) {
			DeflateTask de = new DeflateTokenURIEntryTask(t);
			de.start(); de.join();
			return de.get();
		}

		public class DeflateTokenURIEntryTask extends MapDeflateTask {

			public DeflateTokenURIEntryTask(TokenURIEntry t) {
				super.put("_uri", t.uri.toString());
				super.put("word", t.word);
				super.put("position", t.position);
				super.put("relevance", t.relevance);
			}

		}

	}

	abstract public class YamlDeflateTask extends MapDeflateTask implements DeflateTask {

		protected File file = null;

		public YamlDeflateTask() {
			// TODO exceptions
			file = null;
		}

		public YamlDeflateTask(String s) {
			setOption(s);
		}

		public void setOption(Object o) {
			file = new File(o.toString() + ".yml");
		}

		public void start() {
			try {
				FileOutputStream os = new FileOutputStream(file);
				yaml.dump(hm, new OutputStreamWriter(os));
				os.close();
				done = true;
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
		}

		public void join() {
			return;
		}

		public Object get() {
			return file.getPath();
		}

	}

	abstract public class MapDeflateTask implements DeflateTask {

		final protected HashMap<String, Object> hm = new HashMap<String, Object>();
		protected boolean done = false;

		public void setOption(Object o) {
			// TODO
		}

		public void start() {
			done = true;
		}

		public void join() {
			return;
		}

		public void put(String key, Object o) {
			hm.put(key, o);
		}

		public Object get() {
			return hm;
		}

	}

}
