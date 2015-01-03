/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.index;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import freenet.library.io.serial.ProgressTracker;
import freenet.library.io.serial.Serialiser;
import freenet.library.util.DataNotLoadedException;
import freenet.library.util.Skeleton;
import freenet.library.util.SkeletonBTreeMap;
import freenet.library.util.SkeletonBTreeSet;
import freenet.library.util.concurrent.Executors;
import freenet.library.util.exec.AbstractExecution;
import freenet.library.util.exec.ChainedProgress;
import freenet.library.util.exec.Execution;
import freenet.library.util.exec.Progress;
import freenet.library.util.exec.ProgressParts;
import freenet.library.util.exec.TaskAbortException;

/**
** Prototype B-tree based index. DOCUMENT
**
** @author infinity0
*/
final public class ProtoIndex implements Index {

	final static long serialVersionUID = 0xf8ea40b26c1e5b37L;

	final public static String MIME_TYPE = ProtoIndexSerialiser.MIME_TYPE;
	final public static String DEFAULT_FILE = "index" + ProtoIndexSerialiser.FILE_EXTENSION;

	/** FIXME DEFAULT FAN-OUT:
	 * Yaml has quite high overheads which are dependant on the size of the document being processed.
	 * This is potentially a problem on the client side as well as here. In the long term our fan-out 
	 * will be on the order of hundreds anyway because we will include the metadata in the layer above.
	 * For now, a relatively low fan-out (1024 not 4096) will give reasonable performance without 
	 * causing such problems. Note that this also determines the size of bins for Packer. */
	// DEBUG make final again later
	/*final*/ public static int BTREE_NODE_MIN = 0x400;
	final public static int BTREE_ENT_MAX = (BTREE_NODE_MIN<<1) - 1;

	protected static Executor exec = Executors.DEFAULT_EXECUTOR;
	public static void setExecutor(Executor e) { exec = e; }

	/**
	** Request ID for this index
	*/
	protected String reqID;

	/**
	** Insert ID for this index
	*/
	protected String insID; // TODO HIGH maybe move this to WriteableProtoIndex?

	/**
	** Name for this index.
	*/
	protected String name;
	
	/** Name for index owner */
	protected String indexOwnerName;
	
	/** Email (or more likely Freemail) address of index owner */
	protected String indexOwnerEmail;
	
	/** Total number of indexed pages. Currently this is just passed in or set(), and used for rank calculations. 
	 * In future we might get it from utab. */
	protected long totalPages;

	/**
	** Last time this index was modified.
	*/
	protected Date modified;

	/**
	** Extra configuration options for the index.
	*/
	final protected Map<String, Object> extra;



	final public /* DEBUG protected*/ SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> ttab;
	final protected SkeletonBTreeMap<URIKey, SkeletonBTreeMap<String, URIEntry>> utab;


	public ProtoIndex(String id, String n, String owner, String ownerEmail, long pages) {
		this(id, n, owner, ownerEmail, pages, new Date(), new HashMap<String, Object>(),
			new SkeletonBTreeMap<URIKey, SkeletonBTreeMap<String, URIEntry>>(BTREE_NODE_MIN),
			new SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>>(BTREE_NODE_MIN)/*,
			//filtab = new SkeletonPrefixTreeMap<Token, TokenFilter>(new Token(), TKTAB_MAX)*/
		);
	}

	protected ProtoIndex(String id, String n, String owner, String ownerEmail, long pages, Date m, Map<String, Object> x,
		SkeletonBTreeMap<URIKey, SkeletonBTreeMap<String, URIEntry>> u,
		SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> t/*,
		SkeletonMap<Token, TokenFilter> f*/
		) {
		reqID = id;
		name = (n == null)? "": n;
		modified = m;
		extra = x;
		indexOwnerName = owner;
		indexOwnerEmail = ownerEmail;
		totalPages = pages;

		//filtab = f;
		ttab = t;
		utab = u;
	}

	// TODO NORM maybe have more general class than ProtoIndexSerialiser

	protected int serialFormatUID;
	protected ProtoIndexComponentSerialiser serialiser;
	public ProtoIndexComponentSerialiser getSerialiser() {
		return serialiser;
	}

	public void setSerialiser(ProtoIndexComponentSerialiser srl) {
		// FIXME LOW test for isLive() here... or something
		serialFormatUID = srl.serialFormatUID;
		serialiser = srl;
	}




	private Map<String, Execution<Set<TermEntry>>> getTermEntriesProgress = new
	HashMap<String, Execution<Set<TermEntry>>>();

	public Execution<Set<TermEntry>> getTermEntries(String term) {
		Execution<Set<TermEntry>> request = getTermEntriesProgress.get(term);
		if (request == null) {
			request = new getTermEntriesHandler(term);
			getTermEntriesProgress.put(term, request);
			exec.execute((getTermEntriesHandler)request);
		}
		return request;
	}

	public class getTermEntriesHandler extends AbstractExecution<Set<TermEntry>> implements Runnable, ChainedProgress {
		// TODO NORM have a Runnable field instead of extending Runnable
		// basically, redesign this entire class and series of classes

		final Map<Object, ProgressTracker> trackers = new LinkedHashMap<Object, ProgressTracker>();
		Object current_meta;
		ProgressTracker current_tracker;

		protected getTermEntriesHandler(String t) {
			super(t);
		}

		@Override public ProgressParts getParts() throws TaskAbortException {
			// TODO NORM tidy this up
			Set<TermEntry> result = getResult();
			int started = trackers.size();
			int known = started;
			int done = started - 1;
			// FIXME is ++known valid here? I get an exception with ProgressParts(0/1/0/2) without it,
			// which presumably means last != null while trackers.size() == 0.
			if (last != null) { ++started; ++done; ++known; }
			if (done < 0) { done = 0; }
			int estimate = (result != null)? ProgressParts.TOTAL_FINALIZED: Math.max(ttab.heightEstimate()+1, known);
			return new ProgressParts(done, started, known, estimate);
		}

		@Override public String getStatus() {
			Progress cur = getCurrentProgress();
			return (cur == null)? "Starting next stage...": cur.getSubject() + ": " + cur.getStatus();
		}

		/*@Override**/ public Progress getCurrentProgress() {
			return last != null? last: current_tracker == null? null: current_tracker.getPullProgressFor(current_meta);
		}

		// TODO HIGH tidy this - see SkeletonBTreeMap.inflate() for details
		Progress last = null;
		/*@Override**/ public void run() {
			try {
				// get the root container
				SkeletonBTreeSet<TermEntry> root;
				for (;;) {
					try {
						root = ttab.get(subject);
						break;
					} catch (DataNotLoadedException d) {
						Skeleton p = d.getParent();
						trackers.put(current_meta = d.getValue(), current_tracker = ((Serialiser.Trackable)p.getSerialiser()).getTracker());
						p.inflate(d.getKey());
					}
				}

				if (root == null) {
					// TODO HIGH better way to handle this
					throw new TaskAbortException("Index does not contain term " + subject, new Exception("Index does not contain term " + subject));
				}
				last = root.getProgressInflate(); // REMOVE ME
				root.inflate();

				// Post-process relevance.
				double multiplier = 1.0;
				if(totalPages > 0) {
					long total = totalPages; // Number of pages total
					long specific = root.size(); // Number of pages in this entry
					multiplier = Math.log(((double)total) / ((double)specific));
					if(multiplier < 0) {
						// Logger.error(this, "Negative multiplier!: "+multiplier+" total = "+total+" specific = "+root.size());
						System.out.println("Negative multiplier!: "+multiplier+" total = "+total+" specific = "+root.size());
						multiplier = 1.0;
					} else {
						// Logger.normal(this, "Correcting results: "+multiplier);
					}
				}
				Set<TermEntry> entries = wrapper(root, multiplier);
				
				setResult(entries);

			} catch (TaskAbortException e) {
				setError(e);
				return;
			}
		}

		private Set<TermEntry> wrapper(final SkeletonBTreeSet<TermEntry> root, final double relAdjustment) {
			return new AbstractSet<TermEntry>() {

				public boolean add(TermEntry arg0) {
					throw new UnsupportedOperationException();
				}

				public boolean addAll(Collection<? extends TermEntry> arg0) {
					throw new UnsupportedOperationException();
				}

				public void clear() {
					throw new UnsupportedOperationException();
				}

				public boolean contains(Object arg0) {
					return root.contains(arg0);
				}

				public boolean containsAll(Collection<?> arg0) {
					return root.containsAll(arg0);
				}

				public boolean isEmpty() {
					return root.isEmpty();
				}

				public Iterator<TermEntry> iterator() {
					final Iterator<TermEntry> entries = root.iterator();
					return new Iterator<TermEntry>() {
						public boolean hasNext() {
							return entries.hasNext();
						}

						public TermEntry next() {
							TermEntry t = entries.next();
							if(t instanceof TermPageEntry && relAdjustment != 1.0) {
								// Adjust relevance
								return new TermPageEntry((TermPageEntry)t, (float)(relAdjustment*t.rel));
							} else
								return t;
						}

						public void remove() {
							throw new UnsupportedOperationException();
						}
							
					};
				}

				public boolean remove(Object arg0) {
					throw new UnsupportedOperationException();
				}

				public boolean removeAll(Collection<?> arg0) {
					throw new UnsupportedOperationException();
				}

				public boolean retainAll(Collection<?> arg0) {
					throw new UnsupportedOperationException();
				}

				public int size() {
					return root.size();
				}

			};
		}

	}


	public void setName(String indexName) {
		this.name = indexName;
	}
	
	public void setTotalPages(long total) {
		totalPages = total;
	}
	
	public void setOwner(String owner) {
		this.indexOwnerName = owner;
	}
	
	public void setOwnerEmail(String address) {
		this.indexOwnerEmail = address;
	}

	public String getName() {
		return name;
	}

	public String getOwnerEmail() {
		return indexOwnerEmail;
	}

	public String getOwner() {
		return indexOwnerName;
	}

	public long getTotalPages() {
		return totalPages;
	}



}
