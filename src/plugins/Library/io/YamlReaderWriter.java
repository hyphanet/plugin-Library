/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.io;

import plugins.Library.io.DataFormatException;

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
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.ConstructorException;

import java.util.Collections;
import java.util.Arrays;
import java.util.Map;
import java.util.LinkedHashMap;
import java.io.File;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

/* class definitions added to the extended Yaml processor */
import plugins.Library.io.serial.Packer;
import plugins.Library.index.TermEntry;
import plugins.Library.index.TermPageEntry;
import plugins.Library.index.TermIndexEntry;
import plugins.Library.index.TermTermEntry;
import freenet.keys.FreenetURI;


/**
** Converts between an object and a stream containing a YAML document. By
** default, this uses a {@link Yaml} processor with additional object and tag
** definitions relevant to the Library plugin.
**
** (Ideally this would implement {@link java.io.ObjectInput} and {@link
** java.io.ObjectOutput} but they have too many methods to bother with...)
**
** @see Yaml
** @see Loader
** @see Dumper
** @author infinity0
*/
public class YamlReaderWriter
implements ObjectStreamReader, ObjectStreamWriter {

	final public static String MIME_TYPE = "text/yaml";
	final public static String FILE_EXTENSION = ".yml";

	/**
	** The default {@link Yaml} processor. This one does not wrap long lines
	** and always uses block-level elements.
	*/
	final protected static ThreadLocal<Yaml> DEFAULT_YAML = new ThreadLocal<Yaml>() {
		@Override protected synchronized Yaml initialValue() {
			DumperOptions opt = new DumperOptions();
			opt.setWidth(Integer.MAX_VALUE);
			opt.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
			return new Yaml(new Loader(new ExtendedConstructor()),
							new Dumper(new ExtendedRepresenter(), opt));
		}
	};

	/**
	** Thread local {@link Yaml} processor. ({@code Yaml} is not thread-safe.)
	**
	** @see ThreadLocal
	** @see Yaml
	*/
	final private ThreadLocal<Yaml> yaml;

	public YamlReaderWriter() {
		yaml = DEFAULT_YAML;
	}

	public YamlReaderWriter(ThreadLocal<Yaml> y) {
		yaml = y;
	}

	/*@Override**/ public Object readObject(InputStream is) throws IOException {
		try {
			return yaml.get().load(new InputStreamReader(is));
		} catch (YAMLException e) {
			throw new DataFormatException("Yaml could not process the stream: " + is, e, is, null, null);
		}
	}

	/*@Override**/ public void writeObject(Object o, OutputStream os) throws IOException {
		try {
			yaml.get().dump(o, new OutputStreamWriter(os));
		} catch (YAMLException e) {
			throw new DataFormatException("Yaml could not process the object", e, o, null, null);
		}
	}

	static ObjectBlueprint<TermTermEntry> t_1;
	static ObjectBlueprint<TermIndexEntry> t_2;
	static ObjectBlueprint<TermPageEntry> t_3;
	static {
		try {
			t_1 = new ObjectBlueprint<TermTermEntry>(TermTermEntry.class, Arrays.asList("subj", "rel", "term"));
			t_2 = new ObjectBlueprint<TermIndexEntry>(TermIndexEntry.class, Arrays.asList("subj", "rel", "index"));
			t_3 = new ObjectBlueprint<TermPageEntry>(TermPageEntry.class, Arrays.asList("subj", "rel", "page", "pos"));
		} catch (NoSuchFieldException e) {
			throw new AssertionError(e);
		} catch (NoSuchMethodException e) {
			throw new AssertionError(e);
		}
	}

	/************************************************************************
	** DOCUMENT
	*/
	public static class ExtendedRepresenter extends Representer {

		public ExtendedRepresenter() {
			this.representers.put(FreenetURI.class, new RepresentFreenetURI());
			this.representers.put(Packer.BinInfo.class, new RepresentPackerBinInfo());
			this.representers.put(TermPageEntry.class, new RepresentTermEntry());
			this.representers.put(TermIndexEntry.class, new RepresentTermEntry());
			this.representers.put(TermTermEntry.class, new RepresentTermEntry());
		}

		private class RepresentFreenetURI implements Represent {
			/*@Override**/ public Node representData(Object data) {
				return representScalar("!FreenetURI", ((FreenetURI) data).toString());
			}
		}

		private class RepresentPackerBinInfo implements Represent {
			/*@Override**/ public Node representData(Object data) {
				Packer.BinInfo inf = (Packer.BinInfo)data;
				Map<Object, Object> map = Collections.<Object, Object>singletonMap(inf.getID(), inf.getWeight());
				return representMapping("!BinInfo", map, true);
			}
		}

		private class RepresentTermEntry implements Represent {
			/*@Override**/ public Node representData(Object data) {
				TermEntry en = (TermEntry)data;
				Map<String, Object> map = new LinkedHashMap<String, Object>();
				// PRIORITY WORK OUT A BETTER FORMAT THAN THIS
				// PRIORITY WORK OUT A BETTER FORMAT THAN THIS
				// PRIORITY WORK OUT A BETTER FORMAT THAN THIS
				map.put("type", en.entryType().ordinal());
				switch (en.entryType()) {
				case TERM:
					map.putAll(t_1.objectAsMap((TermTermEntry)en));
					break;
				case INDEX:
					map.putAll(t_2.objectAsMap((TermIndexEntry)en));
					break;
				case PAGE:
					map.putAll(t_3.objectAsMap((TermPageEntry)en));
					break;
				}
				return representMapping("!TermEntry", map, true);
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
			this.yamlConstructors.put("!TermEntry", new ConstructTermEntry());
		}

		private class ConstructFreenetURI extends AbstractConstruct {
			/*@Override**/ public Object construct(Node node) {
				String uri = (String) constructScalar((ScalarNode)node);
				try {
					return new FreenetURI(uri);
				} catch (java.net.MalformedURLException e) {
					throw new ConstructorException("while constructing a FreenetURI", node.getStartMark(), "found malformed URI " + uri, null) {};
				}
			}
		}

		private class ConstructPackerBinInfo extends AbstractConstruct {
			/*@Override**/ public Object construct(Node node) {
				Map<?, ?> map = (Map) constructMapping((MappingNode)node);
				if (map.size() != 1) {
					throw new ConstructorException("while constructing a Packer.BinInfo", node.getStartMark(), "found incorrectly sized map data " + map, null) {};
				}
				for (Map.Entry en: map.entrySet()) {
					return new Packer.BinInfo(en.getKey(), (Integer)en.getValue());
				}
				throw new AssertionError();
			}
		}

		private class ConstructTermEntry extends AbstractConstruct {
			/*@Override**/ public Object construct(Node node) {
				TermEntry en = null;
				Map map = (Map)constructMapping((MappingNode)node);
				map.put("rel", new Float(((Double)map.get("rel")).floatValue()));
				try {
					switch(TermEntry.EntryType.values()[(Integer)map.remove("type")]) {
					case TERM:
						en = t_1.objectFromMap(map);
						break;
					case INDEX:
						en = t_2.objectFromMap(map);
						break;
					case PAGE:
						en = t_3.objectFromMap(map);
						break;
					}
				} catch (Exception e) {
					//java.lang.InstantiationException
					//java.lang.IllegalArgumentException
					//java.lang.reflect.InvocationTargetException
					throw new ConstructorException("while constructing a TermEntry", node.getStartMark(), "could not instantiate map " + map, null, e) {};
				}
				return en;
			}
		}
	}

}
