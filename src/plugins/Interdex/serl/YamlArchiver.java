/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.serl.Serialiser.*;

import freenet.keys.FreenetURI;

import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
** Converts between a map of {@link String} to {@link Object}, and a YAML
** document. The object must be serialisable as defined in {@link Serialiser}.
**
** This class expects {@link Task#meta} to be of type {@link String}, or an
** array whose first element is of type {@link String}.
**
** @author infinity0
*/
public class YamlArchiver<T extends Map<String, Object>> implements Archiver<T> {

	final static Yaml yaml = new Yaml(new org.yaml.snakeyaml.Loader(new FreenetURIConstructor()),
	                                  new org.yaml.snakeyaml.Dumper(new FreenetURIRepresenter(), new org.yaml.snakeyaml.DumperOptions()));
	protected final String prefix;
	protected final String suffix;

	public YamlArchiver() {
		suffix = prefix = "".intern();
	}

	public YamlArchiver(String pre, String suf) {
		prefix = (pre == null)? "".intern(): pre;
		suffix = (suf == null)? "".intern(): suf;
	}

	protected String[] getFileParts(Object meta) {
		String[] m = new String[]{"", ""};
		if (meta instanceof String) {
			m[0] = (String)(meta);
		} else if (meta instanceof Object[]) {
			Object[] arr = (Object[])meta;
			if (arr.length > 0 && arr[0] instanceof String) {
				m[0] = (String)arr[0];
				if (arr.length > 1) {
					StringBuilder str = new StringBuilder(arr[1].toString());
					for (int i=2; i<arr.length; ++i) {
						str.append('.').append(arr[i].toString());
					}
					m[1] = str.toString();
				}
			} else {
				throw new IllegalArgumentException("YamlArchiver does not support such metadata: " + meta);
			}
		} else if (meta != null) {
			throw new IllegalArgumentException("YamlArchiver does not support such metadata: " + meta);
		}

		// TODO maybe have to deconvert primitive arrays
		return m;
	}

	/*========================================================================
	  public interface Archiver
	 ========================================================================*/

	@Override public void pull(PullTask<T> t) {
		String[] s = getFileParts(t.meta);
		try {
			File file = new File(prefix + s[0] + suffix + s[1] + ".yml");
			FileInputStream is = new FileInputStream(file);
			t.data = (T)yaml.load(new InputStreamReader(is));
			is.close();
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public void push(PushTask<T> t) {
		convertPrimitiveArrays(t.data);
		String[] s = getFileParts(t.meta);
		try {
			File file = new File(prefix + s[0] + suffix + s[1] + ".yml");
			FileOutputStream os = new FileOutputStream(file);
			yaml.dump(t.data, new OutputStreamWriter(os));
			os.close();
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	// URGENT snakeYAML says "Arrays of primitives are not fully supported."
	public void convertPrimitiveArrays(Map<String, Object> map) {

		for (Map.Entry<String, Object> en: map.entrySet()) {
			Object o = en.getValue();

			if (o instanceof int[]) {
				int[] ii = (int[]) o;
				Integer[] arr = new Integer[ii.length];
				for (int i=0; i<ii.length; ++i) { arr[i] = ii[i]; }
				en.setValue(arr);

			} else if (o instanceof boolean[]) {
				boolean[] ii = (boolean[]) o;
				Boolean[] arr = new Boolean[ii.length];
				for (int i=0; i<ii.length; ++i) { arr[i] = ii[i]; }
				en.setValue(arr);

			// fuck it, skip coding Byte[] etc until we actually need it

			} else if (o instanceof Map) {
				try {
					convertPrimitiveArrays((Map<String, Object>)o);
				} catch (ClassCastException e) {
					// ignore
				}

			}

		}

	}

	public static class FreenetURIRepresenter extends org.yaml.snakeyaml.representer.Representer {
		public FreenetURIRepresenter() {
			this.representers.put(FreenetURI.class, new RepresentFreenetURI());
		}

		private class RepresentFreenetURI implements org.yaml.snakeyaml.representer.Represent {
			public org.yaml.snakeyaml.nodes.Node representData(Object data) {
				return representScalar("!FreenetURI", ((FreenetURI) data).toString());
			}
		}
	}

	public static class FreenetURIConstructor extends org.yaml.snakeyaml.constructor.Constructor {
		public FreenetURIConstructor() {
			this.yamlConstructors.put("!FreenetURI", new ConstructFreenetURI());
		}

		private class ConstructFreenetURI implements org.yaml.snakeyaml.constructor.Construct {
			public Object construct(org.yaml.snakeyaml.nodes.Node node) {
				String uri = (String) constructScalar((org.yaml.snakeyaml.nodes.ScalarNode)node);
				try {
					return new FreenetURI(uri);
				} catch (java.net.MalformedURLException e) {
					throw new org.yaml.snakeyaml.constructor.ConstructorException("while constructing a FreenetURI", node.getStartMark(), "found malformed URI " + uri, null) {};
				}
			}
		}
	}

}
