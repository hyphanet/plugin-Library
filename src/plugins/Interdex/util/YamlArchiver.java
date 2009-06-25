/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.Archiver.*;

import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
** Converts between a map of {@link String} to {@link Object}, and a YAML
** document. The Object must be serialisable as defined in {@link Archiver}.
**
** This class expects the meta object to be of type {@link String}, which is
** used as part of the filename.
**
** @author infinity0
*/
public class YamlArchiver<T extends Map<String, Object>> implements Archiver<T> {

	final static Yaml yaml = new Yaml();
	protected final String prefix;
	protected final String suffix;

	protected YamlArchiver() {
		suffix = prefix = "".intern();
	}

	protected YamlArchiver(String pre, String suf) {
		prefix = (pre == null)? "".intern(): pre;
		suffix = (pre == null)? "".intern(): suf;
	}

	public void pull(PullTask<T> t) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public void push(PushTask<T> t) {
		T task = t.data;
		convertPrimitiveArrays(task);

		String s = (String)(t.meta);
		try {
			File file = new File(prefix + s + suffix + ".yml");
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
