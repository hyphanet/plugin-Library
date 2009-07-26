/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.util.Skeleton;
import plugins.Library.util.SkeletonMap;
import plugins.Library.util.SkeletonTreeMap;
import plugins.Library.util.SkeletonBTreeMap;
import plugins.Library.util.DataNotLoadedException;
import plugins.Library.serial.Serialiser;
import plugins.Library.serial.TaskAbortException;
import plugins.Library.serial.Progress;

import freenet.keys.FreenetURI;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.SortedSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.Date;

/**
** Prototype B-tree based index. DOCUMENT
**
** @author infinity0
*/
public class ProtoIndex {

	// DEBUG make final again later
	/*final*/ public static int BTREE_NODE_MIN = 0x10000;

	/**
	** Magic number to guide serialisation.
	*/
	final public static long MAGIC = 0xf82a9084681e5ba6L;

	/**
	** Freenet ID for this index
	*/
	protected FreenetURI id;

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



	final public static int TTAB_KEYS = 0;
	final public static int TTAB_DATA = 1;
	final public static int UTAB_KEYS = 2;
	final public static int UTAB_DATA = 3;
	final protected Serialiser.Trackable[] trackables = new Serialiser.Trackable[4];


	final protected SkeletonBTreeMap<String, SortedSet<TokenEntry>> ttab;
	//final protected SkeletonMap<URIKey, SortedMap<FreenetURI, URIEntry>> utab;


	public ProtoIndex(FreenetURI i, String n) {
		id = i;
		name = (n == null)? "": n;
		modified = new Date();
		extra = new HashMap<String, Object>();

		//utab = new SkeletonBTreeMap<URIKey, SortedMap<FreenetURI, URIEntry>>(new URIKey(), UTAB_MAX);
		ttab = new SkeletonBTreeMap<String, SortedSet<TokenEntry>>(BTREE_NODE_MIN);
		//filtab = new SkeletonPrefixTreeMap<Token, TokenFilter>(new Token(), TKTAB_MAX);
	}

	protected ProtoIndex(FreenetURI i, String n, Date m, Map<String, Object> x/*,
		SkeletonMap<URIKey, SortedMap<FreenetURI, URIEntry>> u*/,
		SkeletonBTreeMap<String, SortedSet<TokenEntry>> t/*,
		SkeletonMap<Token, TokenFilter> f*/
		) {
		id = i;
		name = (n == null)? "": n;
		modified = m;
		extra = x;

		//filtab = f;
		ttab = t;
		//utab = u;
	}

	public long getMagic() {
		return MAGIC;
	}



/* might be needed in the future...?
	public void setSerialiser(Archiver<ProtoIndex> arx) {

	}
*/



	private Map<String, Request<Collection<TokenEntry>>> getTermEntriesProgress = new
	HashMap<String, Request<Collection<TokenEntry>>>();

	public Request<Collection<TokenEntry>> getTermEntries(String term) {
		Request<Collection<TokenEntry>> request = getTermEntriesProgress.get(term);
		if (request == null) {
			request = new getTermEntriesHandler(term);
			getTermEntriesProgress.put(term, request);
			request.start();
		}
		return request;
	}






	public Request<URIEntry> getURIEntry(FreenetURI uri) { return null; }




	public class getTermEntriesHandler extends Thread implements Request<Collection<TokenEntry>> {

		final String term;

		final Stack<Object> objects = new Stack<Object>();

		Collection<TokenEntry> result;

		protected getTermEntriesHandler(String t) {
			term = t;
		}

		public Collection<TokenEntry> getResult() {
			return result;
		}

		public void run() {
			for (;;) {
				try {
					result = ttab.get(term);
					break;
				} catch (DataNotLoadedException d) {
					Skeleton p = d.getParent();
					objects.push(d.getValue());
					try {
						p.inflate((String)d.getKey());
					} catch (TaskAbortException e) {
						// DEBUG
						throw new RuntimeException(e);
						// TODO setError() or something...
						//break;
					}
				}
			}
		}

		public String getSubject() {
			return term;
		}

		public String getCurrentStatus() {
			if (objects.size() == 0) { return "nothing yet"; }
			Progress p = selectProgress(objects.peek());
			return (p == null)? "waiting for next stage to start": p.getStatus();
		}

		public String getCurrentStage() {
			if (objects.size() == 0) { return "nothing yet"; }
			Progress p = selectProgress(objects.peek());
			return (p == null)? "waiting for next stage to start": p.getName();
		}

		protected Progress selectProgress(Object o) {
			if (o instanceof SkeletonBTreeMap.GhostNode) {
				return trackables[TTAB_KEYS].getTracker().getPullProgress(o);
			} else {
				return trackables[TTAB_DATA].getTracker().getPullProgress(o);
			}
		}

	}



}
