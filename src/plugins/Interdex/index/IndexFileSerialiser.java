/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import plugins.Interdex.util.IncompletePrefixTreeMap;
import plugins.Interdex.util.IncompleteTreeMap;
import plugins.Interdex.util.Serialiser;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.HashMap;

/**
** This class handles serialisation of an Index into a filetree.
**
** TODO: could have this class load balance itself by having a queue of threads
** in the main class.
**
** TODO: make this combine small .yml URIEntries into the *same* file.
**
** @author infinity0
*/
public class IndexFileSerialiser implements Serialiser {

	final Yaml yaml = new Yaml();

	public IndexFileSerialiser() {
		//
	}

	public SerialiseTask poll() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public InflateTask newInflateTask(Object o) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public DeflateTask newDeflateTask(Object o) {
		if (o instanceof IncompletePrefixTreeMap) {
			return new PrefixTreeMapDeflateTask((IncompletePrefixTreeMap)o);
		} else if (o instanceof IncompleteTreeMap) {
			return new TreeMapDeflateTask((IncompleteTreeMap)o);
		} else if (o instanceof TokenURIEntry) {
			return new TokenURIEntryDeflateTask((TokenURIEntry)o);
		} else {
			throw new UnsupportedOperationException("This type of object is not supported.");
		}
	}

	/*public static class InflateTask implements Serialiser.InflateTask {
		public Object get(String key);
		//public void start();
		//public Object poll();
		//public Object join();
	}*/

	public class PrefixTreeMapDeflateTask extends YamlDeflateTask implements Serialiser.DeflateTask {

		public PrefixTreeMapDeflateTask(IncompletePrefixTreeMap t) {
			super(t.prefixString());
		}

	}

	public class TreeMapDeflateTask extends MapDeflateTask implements Serialiser.DeflateTask {

		public TreeMapDeflateTask(IncompleteTreeMap t) { }

	}

	public class TokenURIEntryDeflateTask extends YamlDeflateTask implements Serialiser.DeflateTask {

		public TokenURIEntryDeflateTask(TokenURIEntry t) {
			super((new Token(t.word)).toString());
			super.put("_uri", t.uri.toString());
			super.put("word", t.word);
			super.put("position", t.position);
			super.put("relevance", t.relevance);
		}

		public void put(String key, Object o) { }

	}

	public abstract class YamlDeflateTask extends MapDeflateTask implements Serialiser.DeflateTask {

		final protected File file;

		public YamlDeflateTask(String s) {
			file = new File(s + ".yml");
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

		public Object poll() {
			return (done)? file.getPath(): null;
		}

		public Object join() {
			return (done)? file.getPath(): null;
		}

	}

	public abstract class MapDeflateTask implements Serialiser.DeflateTask {

		final protected HashMap<String, Object> hm = new HashMap<String, Object>();
		protected boolean done = false;

		public void put(String key, Object o) {
			hm.put(key, o);
		}

		public void start() {
			done = true;
		}

		public Object poll() {
			return (done)? hm: null;
		}

		public Object join() {
			return (done)? hm: null;
		}

	}

}
