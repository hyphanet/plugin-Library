/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

import plugins.Library.serial.Serialiser.*;
import plugins.Library.serial.TaskAbortException;
import plugins.Library.serial.Packer;

import freenet.keys.FreenetURI;

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

import java.util.Collections;
import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.channels.FileLock;

/**
** Converts between a map of {@link String} to {@link Object}, and a YAML
** document. The object must be serialisable as defined in {@link Serialiser}.
**
** This class expects {@link Task#meta} to be of type {@link String}, or an
** array whose first element is of type {@link String}.
**
** @author infinity0
*/
public class YamlArchiver<T extends Map<String, Object>>
implements Archiver<T>,
           LiveArchiver<T, SimpleProgress> {

	/**
	** Thread local yaml processor.
	**
	** @see ThreadLocal
	*/
	final private static ThreadLocal<Yaml> yaml = new ThreadLocal() {
		protected synchronized Yaml initialValue() {
			DumperOptions opt = new DumperOptions();
			opt.setWidth(Integer.MAX_VALUE);
			opt.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
			return new Yaml(new Loader(new ExtendedConstructor()),
			                new Dumper(new ExtendedRepresenter(), opt));
		}
	};

	// DEBUG
	private static boolean testmode = false;
	public static void setTestMode() { System.out.println("YamlArchiver will now randomly pause 5-10s for each task, to simulate network speeds"); testmode = true; }
	public static void randomWait(SimpleProgress p) {
		int t = (int)(Math.random()*5+5);
		p.addTotal(t, true);
		for (int i=0; i<t; ++i) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// nothing
			} finally {
				p.addPartDone();
			}
		}
	}

	/**
	** Prefix of filename
	*/
	protected final String prefix;

	/**
	** Suffix of filename
	*/
	protected final String suffix;

	/**
	** DEBUG: whether to generate random file names
	*/
	protected boolean random;

	public YamlArchiver() {
		suffix = prefix = "";
	}

	public YamlArchiver(boolean r) {
		this();
		random = r;
	}

	public YamlArchiver(String pre, String suf) {
		prefix = (pre == null)? "": pre;
		suffix = (suf == null)? "": suf;
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
				throw new IllegalArgumentException("YamlArchiver does not support such metadata: " + java.util.Arrays.deepToString(arr));
			}
		} else if (meta != null) {
			throw new IllegalArgumentException("YamlArchiver does not support such metadata: " + meta);
		}

		return m;
	}

	/*========================================================================
	  public interface LiveArchiver
	 ========================================================================*/

	@Override public void pull(PullTask<T> t) throws TaskAbortException {
		String[] s = getFileParts(t.meta);
		File file = new File(prefix + s[0] + suffix + s[1] + ".yml");
		try {
			FileInputStream is = new FileInputStream(file);
			try {
				FileLock lock = is.getChannel().lock(0L, Long.MAX_VALUE, true); // shared lock for reading
				try {
					t.data = (T)yaml.get().load(new InputStreamReader(is));
				} catch (YAMLException e) {
					throw new DataFormatException("Yaml could not process the document " + file, e, file, null, null);
				} finally {
					lock.release();
				}
			} finally {
				try { is.close(); } catch (IOException f) { }
			}
		} catch (IOException e) {
			throw new TaskAbortException("YamlArchiver could not complete the task", e, true);
		} catch (RuntimeException e) {
			throw new TaskAbortException("YamlArchiver could not complete the task", e);
		}
	}

	@Override public void push(PushTask<T> t) throws TaskAbortException {
		if (random) { t.meta = java.util.UUID.randomUUID().toString(); }
		String[] s = getFileParts(t.meta);
		File file = new File(prefix + s[0] + suffix + s[1] + ".yml");
		try {
			FileOutputStream os = new FileOutputStream(file);
			try {
				FileLock lock = os.getChannel().lock();
				try {
					yaml.get().dump(t.data, new OutputStreamWriter(os));
				} catch (YAMLException e) {
					throw new DataFormatException("Yaml could not process the object", e, t.data, null, null);
				} finally {
					lock.release();
				}
			} finally {
				try { os.close(); } catch (IOException f) { }
			}
		} catch (IOException e) {
			throw new TaskAbortException("YamlArchiver could not complete the task", e, true);
		} catch (RuntimeException e) {
			throw new TaskAbortException("YamlArchiver could not complete the task", e);
		}
	}

	@Override public void pullLive(PullTask<T> t, SimpleProgress p) {
		try {
			pull(t);
			if (testmode) { randomWait(p); }
			else { p.addTotal(0, true); }
		} catch (TaskAbortException e) {
			p.setAbort(e);
		}
	}

	@Override public void pushLive(PushTask<T> t, SimpleProgress p) {
		try {
			push(t);
			if (testmode) { randomWait(p); }
			else { p.addTotal(0, true); }
		} catch (TaskAbortException e) {
			p.setAbort(e);
		}
	}

	/************************************************************************
	** DOCUMENT
	*/
	public static class ExtendedRepresenter extends Representer {
		public ExtendedRepresenter() {
			this.representers.put(FreenetURI.class, new RepresentFreenetURI());
			this.representers.put(Packer.BinInfo.class, new RepresentPackerBinInfo());
		}

		private class RepresentFreenetURI implements Represent {
			@Override public Node representData(Object data) {
				return representScalar("!FreenetURI", ((FreenetURI) data).toString());
			}
		}

		private class RepresentPackerBinInfo implements Represent {
			@Override public Node representData(Object data) {
				Packer.BinInfo inf = (Packer.BinInfo)data;
				Map map = Collections.singletonMap(inf.id, inf.weight);
				return representMapping("!BinInfo", map, true);
			}
		}
	}

	/************************************************************************
	** DOCUMENT
	*/
	public static class ExtendedConstructor extends Constructor {
		public ExtendedConstructor() {
			this.yamlConstructors.put("!FreenetURI", new ConstructFreenetURI());
			this.yamlConstructors.put("!BinInfo", new ConstructPackerBinInfo());
		}

		private class ConstructFreenetURI implements Construct {
			@Override public Object construct(Node node) {
				String uri = (String) constructScalar((ScalarNode)node);
				try {
					return new FreenetURI(uri);
				} catch (java.net.MalformedURLException e) {
					throw new ConstructorException("while constructing a FreenetURI", node.getStartMark(), "found malformed URI " + uri, null) {};
				}
			}
			// TODO this might be removed in snakeYAML later
			@Override public void construct2ndStep(Node node, Object object) { }
		}

		private class ConstructPackerBinInfo implements Construct {
			@Override public Object construct(Node node) {
				Map<?, ?> map = (Map) constructMapping((MappingNode)node);
				if (map.size() != 1) {
					throw new ConstructorException("while constructing a Packer.BinInfo", node.getStartMark(), "found incorrectly sized map data " + map, null) {};
				}
				for (Map.Entry en: map.entrySet()) {
					return new Packer.BinInfo(en.getKey(), (Integer)en.getValue());
				}
				throw new AssertionError();
			}
			// TODO this might be removed in snakeYAML later
			@Override public void construct2ndStep(Node node, Object object) { }
		}
	}

}
