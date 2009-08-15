/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search.inter;

import plugins.Library.library.Index;
import plugins.Library.index.TermEntry;
import plugins.Library.index.TermIndexEntry;
import plugins.Library.index.TermTermEntry;
import plugins.Library.serial.CompositeProgress;

import freenet.keys.FreenetURI;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
** Class to handle a request for a given subject term.
**
** @author infinity0
*/
public class InterdexQuery implements /* CompositeProgress, */ Runnable {

	/**
	** Argument passed to the constructor of {@link ConcurrentHashMap}.
	*/
	final public static int CONCURRENCY_LEVEL = 0x40;
	// TODO maybe make this the same value as the parallel param of the
	// ThreadPoolExecutor we will use; however I read somewhere that a
	// bigger value is better no matter how many threads are running...

	final public String subject;

	/**
	** Table of index identities.
	*/
	final protected ConcurrentMap<FreenetURI, IndexIdentity>
	indexes = new ConcurrentHashMap<FreenetURI, IndexIdentity>(0x10, (float)0.75, CONCURRENCY_LEVEL);

	/**
	** Table of results.
	*/
	final protected ConcurrentMap<FreenetURI, Map<String, Set<TermEntry>>>
	results = new ConcurrentHashMap<FreenetURI, Map<String, Set<TermEntry>>>(0x10, (float)0.75, CONCURRENCY_LEVEL);

	/**
	** Graph of terms encountered relevant to the subject term of the
	** query. Not synchronized.
	*/
	final protected Graph<String, TermRelation>
	terms = new DefaultDirectedWeightedGraph<String, TermRelation>(new TermRelationFactory(this));

	/**
	** Queue for completed (index, term) queries.
	*/
	final protected BlockingQueue<IndexQuery>
	queries_complete = new LinkedBlockingQueue<IndexQuery>();

	// TODO error maps..

	/**
	** Queue for completed {@link IndexIdentity}s retrievals.
	*/
	final protected BlockingQueue<IndexIdentity>
	indexes_complete = new LinkedBlockingQueue<IndexIdentity>();

	/**
	** Sum of trust-scores of all indexes.
	*/
	protected float trust_mass;

	/**
	** Constructs a new InterdexQuery and TODO starts executing it.
	*/
	public InterdexQuery(String subj, Set<IndexIdentity> roots) {
		subject = subj;
		for (IndexIdentity id: roots) {
			addIndex(id);
			// TODO anything else?
		}
		addTerm(subj);
	}

	public float getTotalTrustMass() {
		return trust_mass;
	}

	/**
	** TODO implement. maybe use JGraphT. DefaultDirectedWeightedGraph
	** and DijkstraShortestPath.
	**
	** Returns the effective relevance of all terms relative to the subject
	** term for this query.
	**
	** The effective relevance of a term (relative to the subject term) is
	** defined as the highest-relevance path from the subject to that term,
	** where the relevance of a path is defined as the product of all the
	** edges on it, where each edge from S to T has weight equal to their
	** {@linkplain #getAvPercvRelevance(String, String) average perceived
	** relevance}.
	**
	** This is itself equivalent to the shortest-path problem from the
	** subject term to the target term, with each edge from S to T having
	** weight {@code -log(R)}, with R being the average perceived relevance
	** of S, T.
	**
	** This definition was chosen because there may be an infinite number
	** of paths between two terms in the semantic web; we want the "most
	** direct" one that exists (by consensus; so we use average perceived
	** relevance).
	*/
	public Map<String, Float> getEffectiveRelevances() {
		throw new UnsupportedOperationException("not implemented");
	}

	protected void addIndex(IndexIdentity id) {
		// pair up this index with all terms in terms.vertexSet(), and add them to the queue
		// update the trust mass
		indexes.put(id.reqID, id);
		throw new UnsupportedOperationException("not implemented");
	}

	/**
	** Add a new term relation to the semantic web.
	**
	** @return New average perceived relevance of the relation.
	*/
	protected float addTermRelation(String subj, String term, IndexIdentity index, float relevance) {
		throw new UnsupportedOperationException("not implemented");
	}

	/**
	** Add a new term to the semantic web, and queue the appropriate {@link
	** IndexQuery}s.
	**
	** @return Whether the graph changed (ie. the term was not already in it).
	*/
	protected boolean addTerm(String subj) {
		// pair up this term with all indexes, and add them to the task queeu
		throw new UnsupportedOperationException("not implemented");
	}




	/**
	** Returns true when we have enough data to display something
	** acceptable to the user. The query may still be going on in the
	** background.
	**
	** TODO decide when this is.
	*/
	/*@Override*/ public boolean isPartiallyDone() {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override public void run() {

		try {

			do {

				while (!queries_complete.isEmpty()) {
					// get completed task

					String res_term = null; // TODO init these
					FreenetURI res_index = null;
					Set<TermEntry> entries = null;
					IndexIdentity res_id = indexes.get(res_index);

					for (TermEntry en: entries) {

						if (en instanceof TermIndexEntry) {
							// PRIORITY limit this! stop the recursion at some point
							TermIndexEntry idxen = ((TermIndexEntry)en);
							FreenetURI rel_id = idxen.getIndex();

							// TODO start a new request to retrieve the IndexIdentity for this FreenetURI

						} else if (en instanceof TermTermEntry) {
							TermTermEntry termen = ((TermTermEntry)en);
							String rel_term = termen.getTerm();
							float rel_rel = termen.getRelevance();
							// PRIORITY limit this! stop the recursion at some point
							addTermRelation(res_term, rel_term, res_id, rel_rel);

						} else {
							// TODO add it to results set for the term
						}
					}
				}

				while (!indexes_complete.isEmpty()) {
					IndexIdentity index_id = indexes_complete.poll(1, TimeUnit.SECONDS);
					addIndex(index_id);
				}

			} while (Math.log(1)!=0 /* tasks active or tasks complete not handled */);

		} catch (InterruptedException e) {
			// TODO.. setError(new TaskAbortException("Query was interrupted", e);
		}

	}

}
