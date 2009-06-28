/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.serl.Serialiser.*;

import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

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

	final static Yaml yaml = new Yaml();
	protected final String prefix;
	protected final String suffix;

	public YamlArchiver() {
		suffix = prefix = "".intern();
	}

	public YamlArchiver(String pre, String suf) {
		prefix = (pre == null)? "".intern(): pre;
		suffix = (suf == null)? "".intern(): suf;
	}

	/*========================================================================
	  public interface Archiver
	 ========================================================================*/

	@Override public void pull(PullTask<T> t) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override public void push(PushTask<T> t) {
		T task = t.data;
		convertPrimitiveArrays(task);

		String s = "", x = "";
		if (t.meta instanceof String) {
			s = (String)(t.meta);
		} else if (t.meta instanceof Object[]) {
			Object[] arr = (Object[])t.meta;
			if (arr.length > 0 && arr[0] instanceof String) {
				s = (String)arr[0];
				if (arr.length > 1) {
					StringBuilder str = new StringBuilder(arr[1].toString());
					for (int i=2; i<arr.length; ++i) {
						str.append('.').append(arr[i].toString());
					}
					x = str.toString();
				}
			} else {
				throw new IllegalArgumentException("YamlArchiver does not support such metadata: " + t.meta);
			}
		} else {
			throw new IllegalArgumentException("YamlArchiver does not support such metadata: " + t.meta);
		}

		try {
			File file = new File(prefix + s + suffix + x + ".yml");
			FileOutputStream os = new FileOutputStream(file);
			yaml.dump(task, new OutputStreamWriter(os));
			os.close();
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	// snakeYAML says "Arrays of primitives are not fully supported."
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

}
