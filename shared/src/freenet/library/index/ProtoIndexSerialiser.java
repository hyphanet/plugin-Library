/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.index;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import freenet.library.FactoryRegister;
import freenet.library.io.DataFormatException;
import freenet.library.io.FreenetURI;
import freenet.library.io.YamlReaderWriter;
import freenet.library.io.serial.Archiver;
import freenet.library.io.serial.FileArchiver;
import freenet.library.io.serial.LiveArchiver;
import freenet.library.io.serial.Serialiser;
import freenet.library.io.serial.Translator;
import freenet.library.Priority;
import freenet.library.util.SkeletonBTreeMap;
import freenet.library.util.SkeletonBTreeSet;
import freenet.library.util.exec.SimpleProgress;
import freenet.library.util.exec.TaskAbortException;

/**
** Serialiser for ProtoIndex
**
** DOCUMENT
**
** @author infinity0
*/
public class ProtoIndexSerialiser
implements Archiver<ProtoIndex>,
           Serialiser.Composite<Archiver<Map<String, Object>>>, // TODO NORM make this a LiveArchiver
           Serialiser.Translate<ProtoIndex, Map<String, Object>>/*,
           Serialiser.Trackable<Index>*/ {

	final public static String MIME_TYPE = YamlReaderWriter.MIME_TYPE;
	final public static String FILE_EXTENSION = YamlReaderWriter.FILE_EXTENSION;

	final protected Translator<ProtoIndex, Map<String, Object>>
	trans;

	final protected LiveArchiver<Map<String, Object>, SimpleProgress> 
	subsrl;

	public ProtoIndexSerialiser(LiveArchiver<Map<String, Object>, SimpleProgress> s) {
		subsrl = s;
		trans = new IndexTranslator(subsrl);
	}

	/* FIXME HIGH: Parallelism in fetching multiple words for the same query.
	 * A single serialiser means when we fetch two words for the same query, and they both end up in the same
	 * bucket, we get an AssertionError when we fetch the bucket twice in ProgressTracker.addPullProgress.
	 * So the solution, for the time being, is simply to use two separate serialisers. */
	
//	final protected static HashMap<Class<?>, ProtoIndexSerialiser>
//	srl_cls = new HashMap<Class<?>, ProtoIndexSerialiser>();

	public static ProtoIndexSerialiser forIndex(Object o, Priority priorityLevel) {
		if (o instanceof String) {
			return forIndex((String)o, priorityLevel);
		} else if (o instanceof File) {
			return forIndex((File)o);
		} else {
			throw new UnsupportedOperationException("Don't know how to retrieve index for object " + o);
		}
	}

	public static ProtoIndexSerialiser forIndex(String uri, Priority priorityLevel) {
//		ProtoIndexSerialiser srl = srl_cls.get(FreenetURI.class);
//		if (srl == null) {
//			// java's type-inference isn't that smart, see
//			FreenetArchiver<Map<String, Object>> arx = Library.makeArchiver(ProtoIndexComponentSerialiser.yamlrw, MIME_TYPE, 0x80 * ProtoIndex.BTREE_NODE_MIN);
//			srl_cls.put(FreenetURI.class, srl = new ProtoIndexSerialiser(arx));
//		}
//		return srl;
		
		// One serialiser per application. See comments above re srl_cls.
		// java's type-inference isn't that smart, see
		LiveArchiver<Map<String, Object>, SimpleProgress> arx = FactoryRegister.getArchiverFactory().newArchiver(ProtoIndexComponentSerialiser.yamlrw, MIME_TYPE, 0x80 * ProtoIndex.BTREE_NODE_MIN, priorityLevel);
		return new ProtoIndexSerialiser(arx);
	}

	public static ProtoIndexSerialiser forIndex(File prefix) {
//		ProtoIndexSerialiser srl = srl_cls.get(File.class);
//		if (srl == null) {
//			srl_cls.put(File.class, srl = new ProtoIndexSerialiser(new FileArchiver<Map<String, Object>>(ProtoIndexComponentSerialiser.yamlrw, true, FILE_EXTENSION)));
//		}
//		return srl;
		
		// One serialiser per application. See comments above re srl_cls.
		return new ProtoIndexSerialiser(new FileArchiver<Map<String, Object>>(ProtoIndexComponentSerialiser.yamlrw, true, FILE_EXTENSION, "", "", prefix));
	}

	/*@Override**/ public LiveArchiver<Map<String, Object>, SimpleProgress> getChildSerialiser() {
		return subsrl;
	}

	/*@Override**/ public Translator<ProtoIndex, Map<String, Object>> getTranslator() {
		return trans;
	}

	/*@Override**/ public void pull(PullTask<ProtoIndex> task) throws TaskAbortException {
		PullTask<Map<String, Object>> serialisable = new PullTask<Map<String, Object>>(task.meta);
		subsrl.pull(serialisable);
		task.meta = serialisable.meta;
		if (task.meta instanceof String) { // if not FreenetURI, skip this silently so we can test on local files
			serialisable.data.put("reqID", task.meta);
		}
		try {
			task.data = trans.rev(serialisable.data);
		} catch (DataFormatException e) {
			throw new TaskAbortException("Could not construct index from data", e);
		}
	}

	/*@Override**/ public void push(PushTask<ProtoIndex> task) throws TaskAbortException {
		PushTask<Map<String, Object>> serialisable = new PushTask<Map<String, Object>>(trans.app(task.data));
		serialisable.meta = serialisable.data.remove("insID");
		subsrl.push(serialisable);
		task.meta = serialisable.meta;
	}

	public static class IndexTranslator
	implements Translator<ProtoIndex, Map<String, Object>> {

		/**
		** Term-table translator
		*/
		Translator<SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>>, Map<String, Object>> ttrans = new
		SkeletonBTreeMap.TreeTranslator<String, SkeletonBTreeSet<TermEntry>>(null, new
		ProtoIndexComponentSerialiser.TreeMapTranslator<String, SkeletonBTreeSet<TermEntry>>(null));

		/**
		** URI-table translator
		*/
		Translator<SkeletonBTreeMap<URIKey, SkeletonBTreeMap<String, URIEntry>>, Map<String, Object>> utrans = new
		SkeletonBTreeMap.TreeTranslator<URIKey, SkeletonBTreeMap<String, URIEntry>>(null, new
		ProtoIndexComponentSerialiser.TreeMapTranslator<URIKey, SkeletonBTreeMap<String, URIEntry>>(null));

		private LiveArchiver<Map<String, Object>, SimpleProgress> subsrl;
		
		public IndexTranslator(LiveArchiver<Map<String, Object>, SimpleProgress> subsrl) {
			this.subsrl = subsrl;
		}

		/**
		** {@inheritDoc}
		**
		** Note: the resulting map will contain the insert SSK URI under the
		** key {@code insID}. The intention is for push methods to remove this
		** and add it to the task metadata. '''Failure to do this could result
		** in the insert URI being published'''.
		**
		** FIXME NORM maybe make this more secure, eg. wrap it in a
		** UnserialisableWrapper or something that makes YAML throw an
		** exception if it is accidentally passed to it.
		*/
		/*@Override**/ public Map<String, Object> app(ProtoIndex idx) {
			if (!idx.ttab.isBare() || !idx.utab.isBare()) {
				throw new IllegalArgumentException("Data structure is not bare. Try calling deflate() first.");
			}
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			map.put("serialVersionUID", ProtoIndex.serialVersionUID);
			map.put("serialFormatUID", idx.serialFormatUID);
			map.put("insID", idx.insID);
			map.put("name", idx.name);
			map.put("ownerName", idx.indexOwnerName);
			map.put("ownerEmail", idx.indexOwnerEmail);
			map.put("totalPages", new Long(idx.totalPages));
			map.put("modified", idx.modified);
			map.put("extra", idx.extra);
			map.put("utab", utrans.app(idx.utab));
			map.put("ttab", ttrans.app(idx.ttab));
			return map;
		}

		/*@Override**/ public ProtoIndex rev(Map<String, Object> map) throws DataFormatException {
			long magic = (Long)map.get("serialVersionUID");

			if (magic == ProtoIndex.serialVersionUID) {
				try {
					// FIXME yet more hacks related to the lack of proper asynchronous FreenetArchiver...
					ProtoIndexComponentSerialiser cmpsrl = ProtoIndexComponentSerialiser.get((Integer)map.get("serialFormatUID"), subsrl);
					Object reqIDObject = map.get("reqID");
					FreenetURI reqID;
					if (reqIDObject instanceof FreenetURI) {
						reqID = (FreenetURI) reqIDObject;
					} else {
						try {
							reqID = new FreenetURI((String) reqIDObject);
						} catch (MalformedURLException e) {
							throw new DataFormatException("Badly formatted URI", e, null);
						}
					}
					String name = (String)map.get("name");
					String ownerName = (String)map.get("ownerName");
					String ownerEmail = (String)map.get("ownerEmail");
					// FIXME yaml idiocy??? It seems to give a Long if the number is big enough to need one, and an Integer otherwise.
					long totalPages;
					Object o = map.get("totalPages");
					if(o instanceof Long)
						totalPages = (Long)o;
					else // Integer
						totalPages = (Integer)o;
					Date modified = (Date)map.get("modified");
					Map<String, Object> extra = (Map<String, Object>)map.get("extra");
					SkeletonBTreeMap<URIKey, SkeletonBTreeMap<String, URIEntry>> utab = utrans.rev((Map<String, Object>)map.get("utab"));
					SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> ttab = ttrans.rev((Map<String, Object>)map.get("ttab"));

					return cmpsrl.setSerialiserFor(new ProtoIndex(reqID, name, ownerName, ownerEmail, totalPages, modified, extra, utab, ttab));

				} catch (ClassCastException e) {
					// TODO LOW maybe find a way to pass the actual bad data to the exception
					throw new DataFormatException("Badly formatted data", e, null);

				} catch (UnsupportedOperationException e) {
					throw new DataFormatException("Unrecognised format ID", e, map.get("serialFormatUID"), map, "serialFormatUID");

				}

			} else {
				throw new DataFormatException("Unrecognised serial ID", null, magic, map, "serialVersionUID");
			}
		}

	}

}
