/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import plugins.Interdex.util.BTreeMap;
import plugins.Interdex.util.SkeletonBTreeMap;
import plugins.Interdex.util.SkeletonMap;
import plugins.Interdex.util.DataNotLoadedException;

import freenet.keys.FreenetURI;

import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.SortedSet;
import java.util.HashMap;
import java.util.Date;

/**
** Prototype B-tree based index. DOCUMENT
**
** @author infinity0
*/
public class ProtoIndex {

	// DEBUG make final again later
	/*final*/ public static int BTREE_NODE_MIN = 0x1000; // 0x10000

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





	final protected SkeletonBTreeMap<String, SortedSet<TokenEntry>> tmtab;
	//final protected SkeletonMap<URIKey, SortedMap<FreenetURI, URIEntry>> utab;


	public ProtoIndex(FreenetURI i, String n) {
		id = i;
		name = (n == null)? "": n;
		modified = new Date();
		extra = new HashMap<String, Object>();

		//utab = new SkeletonBTreeMap<URIKey, SortedMap<FreenetURI, URIEntry>>(new URIKey(), UTAB_MAX);
		tmtab = new SkeletonBTreeMap<String, SortedSet<TokenEntry>>(BTREE_NODE_MIN);
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
		tmtab = t;
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
		return null;
	}

	public Request<URIEntry> getURIEntry(FreenetURI uri) { return null; }

	public class getTermEntriesHandler extends Thread implements Request<Collection<TokenEntry>> {

		String term;

		protected getTermEntriesHandler(String t) {
			term = t;
		}

		public void run() {
			for (;;) {
				try {
					tmtab.get(term);
					break;
				} catch (DataNotLoadedException e) {
					e.getParent().deflate((String)e.getKey());
					// e.getValue();
					// put this onto the "stageNames"
				}
			}
		}

	}





}
