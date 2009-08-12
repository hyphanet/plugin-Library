/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.library.Index;
import plugins.Library.serial.Serialiser;
import plugins.Library.serial.TaskAbortException;
import plugins.Library.serial.Progress;
import plugins.Library.serial.ProgressParts;
import plugins.Library.serial.ProgressTracker;
import plugins.Library.util.Skeleton;
import plugins.Library.util.SkeletonTreeMap;
import plugins.Library.util.SkeletonBTreeMap;
import plugins.Library.util.SkeletonBTreeSet;
import plugins.Library.util.DataNotLoadedException;

import freenet.keys.FreenetURI;

import java.util.Collections;
import java.util.Iterator;
import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.SortedSet;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.Stack;
import java.util.Date;
import plugins.Library.serial.ChainedProgress;

/**
** Prototype B-tree based index. DOCUMENT
**
** @author infinity0
*/
final public class ProtoIndex implements Index {

	final static long serialVersionUID = 0xf82a40726c1e5ba6L;

	final public static String MIME_TYPE = ProtoIndexSerialiser.MIME_TYPE;
	final public static String DEFAULT_FILE = "index" + ProtoIndexSerialiser.FILE_EXTENSION;

	// DEBUG make final again later
	/*final*/ public static int BTREE_NODE_MIN = 0x1000;
	final public static int BTREE_ENT_MAX = (BTREE_NODE_MIN<<1) - 1;

	/**
	** Request ID for this index
	*/
	protected FreenetURI reqID;

	/**
	** Insert ID for this index
	*/
	protected FreenetURI insID; // TODO maybe move this to WriteableProtoIndex?

	/**
	** Name for this index.
	*/
	protected String name;

	/**
	** Last time this index was modified.
	*/
	protected Date modified;

	/**
	** Extra configuration options for the index.
	*/
	final protected Map<String, Object> extra;



	final public /* DEBUG URGENT protected*/ SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> ttab;
	final protected SkeletonBTreeMap<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>> utab;


	public ProtoIndex(FreenetURI id, String n) {
		this(id, n, new Date(), new HashMap<String, Object>(),
			new SkeletonBTreeMap<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>(BTREE_NODE_MIN),
			new SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>>(BTREE_NODE_MIN)/*,
			//filtab = new SkeletonPrefixTreeMap<Token, TokenFilter>(new Token(), TKTAB_MAX)*/
		);
	}

	protected ProtoIndex(FreenetURI id, String n, Date m, Map<String, Object> x,
		SkeletonBTreeMap<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>> u,
		SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> t/*,
		SkeletonMap<Token, TokenFilter> f*/
		) {
		reqID = id;
		name = (n == null)? "": n;
		modified = m;
		extra = x;

		//filtab = f;
		ttab = t;
		utab = u;
	}

	// TODO maybe have more general classs than ProtoIndexSerialiser

	protected int serialFormatUID;
	protected ProtoIndexComponentSerialiser serialiser;
	public ProtoIndexComponentSerialiser getSerialiser() {
		return serialiser;
	}

	public void setSerialiser(ProtoIndexComponentSerialiser srl) {
		// TODO test for isLive() here... or something
		serialFormatUID = srl.serialFormatUID;
		serialiser = srl;
	}




	private Map<String, Request<Collection<TermEntry>>> getTermEntriesProgress = new
	HashMap<String, Request<Collection<TermEntry>>>();

	public Request<Collection<TermEntry>> getTermEntries(String term) {
		Request<Collection<TermEntry>> request = getTermEntriesProgress.get(term);
		if (request == null) {
			request = new getTermEntriesHandler(term);
			getTermEntriesProgress.put(term, request);
			// TODO use ThreadPoolExecutor
			new Thread((Runnable)request).start();
		}
		return request;
	}




	public Request<URIEntry> getURIEntry(FreenetURI uri) {
		throw new UnsupportedOperationException("not implemented");
	}


	public class getTermEntriesHandler extends AbstractRequest<Collection<TermEntry>> implements Runnable, ChainedProgress {

		final Stack<Object> metas = new Stack<Object>();
		final Stack<ProgressTracker> trackers = new Stack<ProgressTracker>();

		protected getTermEntriesHandler(String t) {
			super(t);
		}

		@Override public ProgressParts getParts() {
			// PRIORITY
			throw new UnsupportedOperationException("not implemented 2457");
		}

		/**
		** {@inheritDoc}
		**
		** This implementation returns an immutable collection backed by the
		** data stored in the Library.
		*/
		@Override public Collection<TermEntry> getResult() throws TaskAbortException {
			if (error != null) { throw error; }
			return result;
		}

		@Override public String getStatus() {
			Progress cur = getCurrentProgress();
			return (cur == null)? "Starting next stage...": cur.getSubject() + ": " + cur.getStatus();
		}

		@Override public Progress getCurrentProgress() {
			return last != null? last: trackers.isEmpty()? null: trackers.peek().getPullProgressFor(metas.peek());
		}

		// URGENT tidy this - see SkeletonBTreeMap.inflate() for details
		Progress last = null;
		@Override public void run() {
			try {
				// get the root container
				SkeletonBTreeSet<TermEntry> root;
				for (;;) {
					try {
						root = ttab.get(subject);
						break;
					} catch (DataNotLoadedException d) {
						Skeleton p = d.getParent();
						metas.push(d.getValue());
						trackers.push(((Serialiser.Trackable)p.getSerialiser()).getTracker());
						p.inflate(d.getKey());
					}
				}

				if (root == null) {
					// PRIORITY better way to handle this
					throw new TaskAbortException("Index does not contain term " + subject, null);
				}
				last = root.getPPP(); // REMOVE ME
				root.inflate();
				setResult(Collections.unmodifiableSet(root));

			} catch (TaskAbortException e) {
				setError(e);
				return;
			}
		}

	}



}
