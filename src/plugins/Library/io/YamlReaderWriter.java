/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.io;

import org.yaml.snakeyaml.nodes.Tag;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.AbstractConstruct;

import java.util.Collections;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Semaphore;
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
** @author infinity0
*/
public class YamlReaderWriter
implements ObjectStreamReader, ObjectStreamWriter {

	final public static String MIME_TYPE = "text/yaml";
	final public static String FILE_EXTENSION = ".yml";
	
	final static int MAX_PARALLEL = 1; // Limited by memory mainly. If memory is no object it could be limited by threads.
	// Each Yaml instance uses a *significant* amount of memory...
	static final Semaphore parallelLimiter = new Semaphore(MAX_PARALLEL);

	public YamlReaderWriter() {
	}

	/*@Override**/ public Object readObject(InputStream is) throws IOException {
		parallelLimiter.acquireUninterruptibly();
		try {
			return makeYAML().load(new InputStreamReader(is, "UTF-8"));
		} catch (YAMLException e) {
			throw new DataFormatException("Yaml could not process the stream: " + is, e, is, null, null);
		} finally {
			parallelLimiter.release();
		}
	}

	/*@Override**/ public void writeObject(Object o, OutputStream os) throws IOException {
		parallelLimiter.acquireUninterruptibly();
		try {
			makeYAML().dump(o, new OutputStreamWriter(os, "UTF-8"));
		} catch (YAMLException e) {
			throw new DataFormatException("Yaml could not process the object", e, o, null, null);
		} finally {
			parallelLimiter.release();
		}
	}

	/** We do NOT keep this thread-local, because the Composer is only cleared after
	 * the next call to load(), so it can persist with a lot of useless data if we 
	 * then use a different thread. So lets just construct them as needed. */
	private Yaml makeYAML() {
		DumperOptions opt = new DumperOptions();
		opt.setWidth(Integer.MAX_VALUE);
		opt.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		return new Yaml(new ExtendedConstructor(), new ExtendedRepresenter(), opt);
	}

	final public static ObjectBlueprint<TermTermEntry> tebp_term;
	final public static ObjectBlueprint<TermIndexEntry> tebp_index;
	final public static ObjectBlueprint<TermPageEntry> tebp_page;
	static {
		try {
			tebp_term = new ObjectBlueprint<TermTermEntry>(TermTermEntry.class, Arrays.asList("subj", "rel", "term"));
			tebp_index = new ObjectBlueprint<TermIndexEntry>(TermIndexEntry.class, Arrays.asList("subj", "rel", "index"));
			tebp_page = new ObjectBlueprint<TermPageEntry>(TermPageEntry.class, Arrays.asList("subj", "rel", "page", "title", "positions", "posFragments"));
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
			this.representers.put(FreenetURI.class, new Represent() {
				/*@Override**/ public Node representData(Object data) {
					return representScalar(new Tag("!FreenetURI"), ((FreenetURI) data).toString());
				}
			});
			this.representers.put(Packer.BinInfo.class, new Represent() {
				/*@Override**/ public Node representData(Object data) {
					Packer.BinInfo inf = (Packer.BinInfo)data;
					Map<Object, Object> map = Collections.<Object, Object>singletonMap(inf.getID(), inf.getWeight());
					return representMapping(new Tag("!BinInfo"), map, DumperOptions.FlowStyle.FLOW);
				}
			});
			this.representers.put(TermTermEntry.class, new RepresentTermEntry(tebp_term));
			this.representers.put(TermIndexEntry.class, new RepresentTermEntry(tebp_index));
			this.representers.put(TermPageEntry.class, new RepresentTermEntry(tebp_page));
		}

		public class RepresentTermEntry<T extends TermEntry> implements Represent {

			final ObjectBlueprint<T> blueprint;
			final String tag;

			public RepresentTermEntry(ObjectBlueprint<T> bp) {
				blueprint = bp;
				tag = "!" + bp.getObjectClass().getSimpleName();
			}

			/*@Override**/ public Node representData(Object data) {
				return representMapping(new Tag(tag), blueprint.objectAsMap((T)data), DumperOptions.FlowStyle.FLOW);
			}

		}

	}


	/************************************************************************
	** DOCUMENT
	*/
	public static class ExtendedConstructor extends Constructor {
		public ExtendedConstructor() {
			this.yamlConstructors.put(new Tag("!FreenetURI"), new AbstractConstruct() {
				/*@Override**/ public Object construct(Node node) {
					String uri = (String) constructScalar((ScalarNode)node);
					try {
						return new FreenetURI(uri);
					} catch (java.net.MalformedURLException e) {
						throw new ConstructorException("while constructing a FreenetURI", node.getStartMark(), "found malformed URI " + uri, null);
					}
				}
			});
			this.yamlConstructors.put(new Tag("!BinInfo"), new AbstractConstruct() {
				/*@Override**/ public Object construct(Node node) {
					Map<?, ?> map = (Map) constructMapping((MappingNode)node);
					if (map.size() != 1) {
						throw new ConstructorException("while constructing a Packer.BinInfo", node.getStartMark(), "found incorrectly sized map data " + map, null);
					}
					for (Map.Entry en: map.entrySet()) {
						return new Packer.BinInfo(en.getKey(), (Integer)en.getValue());
					}
					throw new AssertionError();
				}
			});
			this.yamlConstructors.put(new Tag("!TermTermEntry"), new ConstructTermEntry(tebp_term));
			this.yamlConstructors.put(new Tag("!TermIndexEntry"), new ConstructTermEntry(tebp_index));
			this.yamlConstructors.put(new Tag("!TermPageEntry"), new ConstructTermEntry(tebp_page));
		}

		public class ConstructTermEntry<T extends TermEntry> extends AbstractConstruct {

			final ObjectBlueprint<T> blueprint;

			public ConstructTermEntry(ObjectBlueprint<T> bp) {
				blueprint = bp;
			}

			/*@Override**/ public Object construct(Node node) {
				Map map = (Map)constructMapping((MappingNode)node);
				map.put("rel", new Float(((Double)map.get("rel")).floatValue()));
				try {
					return blueprint.objectFromMap(map);
				} catch (Exception e) {
					//java.lang.InstantiationException
					//java.lang.IllegalAccessException
					//java.lang.reflect.InvocationTargetException
					throw new ConstructorException("while constructing a " + blueprint.getObjectClass().getSimpleName(), node.getStartMark(), "could not instantiate map " + map, null, e);
				}
			}

		}
	}

	// for some reason SnakeYAML doesn't make this class publicly constructible
	public static class ConstructorException extends org.yaml.snakeyaml.constructor.ConstructorException {
		public ConstructorException(String context, Mark contextMark, String problem, Mark problemMark) {
			super(context, contextMark, problem, problemMark);
		}
		public ConstructorException(String context, Mark contextMark, String problem, Mark problemMark, Throwable cause) {
			super(context, contextMark, problem, problemMark, cause);
		}
	}


}
