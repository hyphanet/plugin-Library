/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import plugins.Interdex.util.Skeleton;
import plugins.Interdex.util.SkeletonMap;
import plugins.Interdex.util.SkeletonTreeMap;
import plugins.Interdex.util.SkeletonBTreeMap;
import plugins.Interdex.util.DataNotLoadedException;
import plugins.Interdex.serl.Serialiser;
import plugins.Interdex.serl.TaskAbortException;
import plugins.Interdex.serl.Progress;

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
					result = tmtab.get(term);
					break;
				} catch (DataNotLoadedException d) {
					Skeleton p = d.getParent();
					objects.push(d.getValue());
					try {
						p.inflate((String)d.getKey());
						// e.getValue();
						// put this onto the "stageNames"
					} catch (TaskAbortException e) {
						// something
						break;
					}
				}
			}
		}

		public String getSubject() {
			return term;
		}

		public String getCurrentProgress() {
			return null;
		}

		public String getCurrentStatus() {
			return null;
		}

		public String getCurrentStage() {
			if (objects.size() == 0) { return "nothing yet"; }
			Object o = objects.peek();
			Progress p;

			if (o instanceof SkeletonBTreeMap.GhostNode) {
				p = ((Serialiser.Trackable)tmtab.nsrl).getTracker().getPullProgress(o);

			} else {
				p = ((Serialiser.Trackable)tmtab.vsrl).getTracker().getPullProgress(o);

			}

			return (p == null)? "waiting for next stage to start": p.getName();
		}


/*
				final String s = sterm;
				new Thread() {
					public void run() {
						test.inflate(Token.intern(s));
					}
				}.start();

				for (;;) {
					try {
						test.get(Token.intern(sterm));
						System.out.println("deflated term \"" + sterm + "\" in " + timeDiff() + "ms.");
						break;
					} catch (DataNotLoadedException e) {
						Object meta = e.getValue();
						Progress p;
						if ((p = srl.getTracker().getPullProgress(meta)) != null) {
							pollProgress(meta, p);
						} else if ((p = vsrl.getTracker().getPullProgress(meta)) != null) {
							pollProgress(meta, p);
						} else {
							System.out.println("lol wut no progress (" + meta + ")? trying again");
							try { Thread.sleep(1000); } catch (InterruptedException x) { }
						}
						continue;
					}
				}

			public void pollProgress(Object key, Progress p) {
				int d; int t; boolean f;
				do {
					d = p.partsDone();
					t = p.partsTotal();
					f = p.isTotalFinal();
					System.out.println(key + ": " + d + "/" + t + (f? "": "???"));
					try { Thread.sleep(1000); } catch (InterruptedException x) { }
				} while (!f || d != t);
			}
*/

	}








}
