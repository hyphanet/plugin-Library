/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

import junit.framework.TestCase;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.Loader;
import org.yaml.snakeyaml.Dumper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.Construct;
import org.yaml.snakeyaml.constructor.ConstructorException;

import java.io.*;
import java.util.*;

/**
** @author infinity0
*/
public class YamlMapTest extends TestCase {

	public void testYamlMap() throws IOException {
		Map<String, Object> data = new TreeMap<String, Object>();
		data.put("key1", new Bean());
		data.put("key2", new Bean());
		data.put("key3", new Custom("test"));
		data.put("key4", new Wrapper("test", new Custom("test")));

		Yaml yaml = new Yaml(new Loader(new ExtendedConstructor()),
			                new Dumper(new ExtendedRepresenter(), new DumperOptions()));
		File file = new File("beantest.yml");

		FileOutputStream os = new FileOutputStream(file);
		yaml.dump(data, new OutputStreamWriter(os));
		os.close();

		FileInputStream is = new FileInputStream(file);
		Object o = yaml.load(new InputStreamReader(is));
		is.close();

		assertTrue(o instanceof Map);
		Map m = (Map)o;
		assertTrue(m.get("key1") instanceof Bean);
		assertTrue(m.get("key2") instanceof Bean); // NOTE these tests fail in snakeYAML 1.2 and below, fixed in 1.3
		assertTrue(m.get("key3") instanceof Custom);
		assertTrue(m.get("key4") instanceof Wrapper);
	}

	public static class Bean {
		private String a;
		public Bean() { a = ""; }
		public String getA() { return a; }
		public void setA(String s) { a = s; }

	}

	public static class Wrapper {
		private String a;
		private Custom b;
		public Wrapper(String s, Custom bb) { a = s; b = bb; }
		public Wrapper() { }
		public String getA() { return a; }
		public void setA(String s) { a = s; }
		public Custom getB() { return b; }
		public void setB(Custom bb) { b = bb; }
	}

	public static class Custom {
		final private String str;
		public Custom(String s) {
			str = s;
		}
		// the presence of this constructor causes 1.4-snapshot to fail
		// fixed in 1.4-rc1
		public Custom(Integer i) {
			str = "";
		}
		// the absence of this constructor causes 1.3 to fail
		// fixed in 1.4-rc1
		public Custom(Custom c) {
			str = c.str;
		}
		public String toString() { return str; }
	}


	public static class ExtendedRepresenter extends Representer {
		public ExtendedRepresenter() {
			this.representers.put(Custom.class, new RepresentCustom());
		}

		private class RepresentCustom implements Represent {
			@Override public Node representData(Object data) {
				return representScalar("!Custom", ((Custom) data).toString());
			}
		}
	}


	public static class ExtendedConstructor extends Constructor {
		public ExtendedConstructor() {
			this.yamlConstructors.put("!Custom", new ConstructCustom());
		}

		private class ConstructCustom implements Construct {
			@Override public Object construct(Node node) {
				String str = (String) constructScalar((ScalarNode)node);
				return new Custom(str);
			}
			@Override public void construct2ndStep(Node node, Object object) { }
		}
	}

}
