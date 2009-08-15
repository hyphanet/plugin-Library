/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.Library;
import plugins.Library.util.SkeletonBTreeMap;
import plugins.Library.util.SkeletonBTreeSet;

import plugins.Library.serial.Serialiser.*;
import plugins.Library.serial.Serialiser;
import plugins.Library.serial.Translator;
import plugins.Library.serial.Archiver;
import plugins.Library.serial.FileArchiver;
import plugins.Library.serial.DataFormatException;
import plugins.Library.serial.TaskAbortException;
import plugins.Library.client.FreenetArchiver;
import plugins.Library.io.YamlReaderWriter;

import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.Date;
import java.io.File;

import freenet.keys.FreenetURI;

/**
** Serialiser for ProtoIndex
**
** DOCUMENT
**
** @author infinity0
*/
public class ProtoIndexSerialiser
implements Archiver<ProtoIndex>,
           Serialiser.Composite<Archiver<Map<String, Object>>>, // PRIORITY make this a LiveArchiver
           Serialiser.Translate<ProtoIndex, Map<String, Object>>/*,
           Serialiser.Trackable<Index>*/ {

	final public static String MIME_TYPE = YamlReaderWriter.MIME_TYPE;
	final public static String FILE_EXTENSION = YamlReaderWriter.FILE_EXTENSION;

	final protected static Translator<ProtoIndex, Map<String, Object>>
	trans = new IndexTranslator();

	final protected Archiver<Map<String, Object>>
	subsrl;

	public ProtoIndexSerialiser(Archiver<Map<String, Object>> s) {
		subsrl = s;
	}

	final protected static HashMap<Class<?>, ProtoIndexSerialiser>
	srl_cls = new HashMap<Class<?>, ProtoIndexSerialiser>();

	public static ProtoIndexSerialiser forIndex(Object o) {
		if (o instanceof FreenetURI) {
			return forIndex((FreenetURI)o);
		} else if (o instanceof File) {
			return forIndex((File)o);
		} else {
			throw new UnsupportedOperationException("Don't know how to retrieve index for object " + o);
		}
	}

	public static ProtoIndexSerialiser forIndex(FreenetURI uri) {
		ProtoIndexSerialiser srl = srl_cls.get(FreenetURI.class);
		if (srl == null) {
			// java's type-inference isn't that smart, see
			FreenetArchiver<Map<String, Object>> arx = Library.makeArchiver(ProtoIndexComponentSerialiser.yamlrw, MIME_TYPE, 0x80 * ProtoIndex.BTREE_NODE_MIN);
			srl_cls.put(FreenetURI.class, srl = new ProtoIndexSerialiser(arx));
		}
		return srl;
	}

	public static ProtoIndexSerialiser forIndex(File f) {
		ProtoIndexSerialiser srl = srl_cls.get(File.class);
		if (srl == null) {
			srl_cls.put(File.class, srl = new ProtoIndexSerialiser(new FileArchiver<Map<String, Object>>(ProtoIndexComponentSerialiser.yamlrw, true, FILE_EXTENSION)));
		}
		return srl;
	}

	public Archiver<Map<String, Object>> getChildSerialiser() {
		return subsrl;
	}

	public Translator<ProtoIndex, Map<String, Object>> getTranslator() {
		return trans;
	}

	public void pull(PullTask<ProtoIndex> task) throws TaskAbortException {
		PullTask<Map<String, Object>> serialisable = new PullTask<Map<String, Object>>(task.meta);
		subsrl.pull(serialisable);
		task.meta = serialisable.meta;
		if (task.meta instanceof FreenetURI) { // if not FreenetURI, skip this silently so we can test on local files
			serialisable.data.put("reqID", task.meta);
		}
		try {
			task.data = trans.rev(serialisable.data);
		} catch (RuntimeException e) {
			throw new TaskAbortException("Could not construct index from data", e);
		}
	}

	public void push(PushTask<ProtoIndex> task) throws TaskAbortException {
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
		Translator<SkeletonBTreeMap<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>, Map<String, Object>> utrans = new
		SkeletonBTreeMap.TreeTranslator<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>(null, new
		ProtoIndexComponentSerialiser.TreeMapTranslator<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>(null));


		public Map<String, Object> app(ProtoIndex idx) {
			if (!idx.ttab.isBare() || !idx.utab.isBare()) {
				throw new IllegalArgumentException("Data structure is not bare. Try calling deflate() first.");
			}
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			map.put("serialVersionUID", idx.serialVersionUID);
			map.put("serialFormatUID", idx.serialFormatUID);
			map.put("insID", idx.insID);
			map.put("name", idx.name);
			map.put("modified", idx.modified);
			map.put("extra", idx.extra);
			map.put("utab", utrans.app(idx.utab));
			map.put("ttab", ttrans.app(idx.ttab));
			return map;
		}

		public ProtoIndex rev(Map<String, Object> map) {
			long magic = (Long)map.get("serialVersionUID");

			if (magic == ProtoIndex.serialVersionUID) {
				try {
					ProtoIndexComponentSerialiser cmpsrl = ProtoIndexComponentSerialiser.get((Integer)map.get("serialFormatUID"));
					FreenetURI reqID = (FreenetURI)map.get("reqID");
					String name = (String)map.get("name");
					Date modified = (Date)map.get("modified");
					Map<String, Object> extra = (Map<String, Object>)map.get("extra");
					SkeletonBTreeMap<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>> utab = utrans.rev((Map<String, Object>)map.get("utab"));
					SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> ttab = ttrans.rev((Map<String, Object>)map.get("ttab"));

					return cmpsrl.setSerialiserFor(new ProtoIndex(reqID, name, modified, extra, utab, ttab));

				} catch (ClassCastException e) {
					// TODO maybe find a way to pass the actual bad data to the exception
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
