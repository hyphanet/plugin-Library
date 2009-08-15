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

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.ThreadPoolExecutor;

/**
** Class to handle a request for a given subject term.
**
** @author infinity0
*/
public class InterdexQuery implements /* CompositeProgress, */ Runnable {

	// PRIORITY find a better place to put this.. maybe in the Library singleton
	final static protected ThreadPoolExecutor exec = new ThreadPoolExecutor(
		0x40, 0x40, 1, TimeUnit.SECONDS,
		new LinkedBlockingQueue<Runnable>(),
		new ThreadPoolExecutor.CallerRunsPolicy() // easier than catching RejectedExecutionException, if it ever occurs
	);

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
	final protected ConcurrentMap<FreenetURI, Map<String, Collection<TermEntry>>>
	results = new ConcurrentHashMap<FreenetURI, Map<String, Collection<TermEntry>>>(0x10, (float)0.75, CONCURRENCY_LEVEL);

	/**
	** Graph of terms encountered. Not synchronized.
	*/
	final protected Graph<TermDefinition, TermRelation>
	terms = new DefaultDirectedWeightedGraph<TermDefinition, TermRelation>(new TermRelationFactory(this));

	/**
	** Queue for completed (index, term) queries.
	*/
	final protected BlockingQueue<IndexQuery>
	query_complete = new LinkedBlockingQueue<IndexQuery>();

	// TODO error maps..

	/**
	** Queue for completed {@link IndexIdentity}s retrievals.
	*/
	final protected BlockingQueue<IndexIdentity>
	index_complete = new LinkedBlockingQueue<IndexIdentity>();

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
		addTermDefinition(new TermDefinition(subj));
	}

	public float getTotalTrustMass() {
		// TODO maybe provide a way to re-calculate this from all the identities...
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
	** {@linkplain TermRelation#getWeightedRelevance() average weighted
	** relevance}.
	**
	** This is itself equivalent to the shortest-path problem from the
	** subject term to the target term, with each edge from S to T having
	** weight {@code -log(R)}, with R being the average weighted relevance
	** of S, T.
	**
	** This definition was chosen because there may be an infinite number
	** of paths between two terms in the semantic web; we want the "most
	** direct" one that exists (by consensus; so we use average weighted
	** relevance).
	*/
	public Map<String, Float> getEffectiveRelevances() {
		throw new UnsupportedOperationException("not implemented");
	}

	/**
	** Add an {@link IndexIdentity} to the active list, and queue {@link
	** IndexQuery}s to search all terms in this index. This method assumes that
	** the identity does not already exist in the index map; it is up to the
	** calling code to ensure that this holds.
	**
	** This method is not thread-safe.
	*/
	protected void addIndex(IndexIdentity id) {
		// update the trust mass
		trust_mass += id.trust;
		// pair up this index with all terms in terms.vertexSet(), and schedule
		// them for execution
		IndexIdentity old = indexes.put(id.reqID, id);
		assert(old == null);
		for (TermDefinition termdef: terms.vertexSet()) {
			exec.execute(new IndexQuery(id, termdef, query_complete));
		}
	}

	/**
	** Add a new term relation to the terms graph. This method assumes that
	** the {@link TermDefinition} for the subject term has already been added
	** to the terms graph; it is up to the calling code to ensure that this
	** holds.
	**
	** This method is not thread-safe.
	**
	** @return New average weighted relevance of the relation.
	*/
	protected float addTermRelation(TermDefinition subjdef, String term, IndexIdentity index, float relevance) {
		assert(terms.containsVertex(subjdef));
		TermDefinition termdef = new TermDefinition(term);
		addTermDefinition(termdef);
		TermRelation rel = terms.getEdge(subjdef, termdef);
		if (rel == null) {
			rel = terms.addEdge(subjdef, termdef);
		}
		return rel.addRelevanceRating(index, relevance);
	}

	/**
	** Add a new term to the terms graph, and queue {@link IndexQuery}s to
	** search this term in all {@link IndexIdentity}s. If the term is already
	** in the graph, returns {@code false}.
	**
	** This method is not thread-safe.
	**
	** @return Whether the graph changed (ie. the term was not already in it).
	*/
	protected boolean addTermDefinition(TermDefinition termdef) {
		if (!terms.addVertex(termdef)) { return false; }
		// pair up this term with all indexes, and add them to the task queeu
		for (IndexIdentity id: indexes.values()) {
			exec.execute(new IndexQuery(id, termdef, query_complete));
		}
		return true;
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

				while (!query_complete.isEmpty()) {
					// get completed task
					IndexQuery res_query = query_complete.poll(1, TimeUnit.SECONDS);

					TermDefinition res_termdef = res_query.termdef;
					Collection<TermEntry> entries = null;// TODO res_query.getResult();
					IndexIdentity res_id = res_query.id;

					// update the termdefs
					res_termdef.addDefiningIdentity(res_id);

					for (TermEntry en: entries) {

						if (en instanceof TermIndexEntry) {
							TermIndexEntry idxen = ((TermIndexEntry)en);
							FreenetURI rel_id = idxen.getIndex();

							// PRIORITY limit this! stop the recursion at some point
							if (!indexes.containsKey(rel_id)) {
								// TODO start a new request to retrieve the IndexIdentity for this FreenetURI
							}

						} else if (en instanceof TermTermEntry) {
							TermTermEntry termen = ((TermTermEntry)en);
							String rel_term = termen.getTerm();
							float rel_rel = termen.getRelevance();

							// PRIORITY limit this! stop the recursion at some point
							addTermRelation(res_termdef, rel_term, res_id, rel_rel);

						} else {
							// TODO add it to results set for the term
						}
					}
				}

				while (!index_complete.isEmpty()) {
					IndexIdentity index_id = index_complete.poll(1, TimeUnit.SECONDS);
					addIndex(index_id);
				}

			} while (Math.log(1)!=0 /* tasks active or tasks complete not handled */);

		} catch (InterruptedException e) {
			// TODO.. setError(new TaskAbortException("Query was interrupted", e);
		}

	}

}
