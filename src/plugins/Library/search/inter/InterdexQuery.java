/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search.inter;

import plugins.Library.Index;
import plugins.Library.index.TermEntry;
import plugins.Library.index.TermIndexEntry;
import plugins.Library.index.TermTermEntry;
import plugins.Library.event.CompositeProgress;

import freenet.keys.FreenetURI;

import org.jgrapht.WeightedGraph;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.alg.DijkstraShortestPath;

import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
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
	** Minimum relevance for a term/index to recurse down.
	*/
	final public static float RELEVANCE_MIN = 0.6f;

	/**
	** Argument passed to the constructor of {@link ConcurrentHashMap}.
	*/
	final public static int CONCURRENCY_LEVEL = 0x40;
	// TODO maybe make this the same value as the parallel param of the
	// ThreadPoolExecutor we will use; however I read somewhere that a
	// bigger value is better no matter how many threads are running...

	final public String subject;

	/**
	** {@link TermDefinition} for the subject term.
	*/
	final protected TermDefinition subjdef;

	/**
	** Graph of all terms encountered. The weight of an edge E is given by
	** {@code -Math.log(E.}{@link TermRelation#getWeightedRelevance()}{@code
	** )}.
	**
	** Not synchronized.
	*/
	final protected WeightedGraph<TermDefinition, TermRelation>
	terms = new SimpleDirectedWeightedGraph<TermDefinition, TermRelation>(new TermRelationFactory(this));

	/**
	** Set of terms accepted for querying.
	**
	** Not synchronized.
	*/
	final protected Set<TermDefinition>
	terms_Q = new HashSet<TermDefinition>();

	/**
	** Table of all index referents (including loaded identities) encountered.
	**
	** Not synchronized.
	*/
	final protected Map<FreenetURI, IndexReferent>
	idrft = new HashMap<FreenetURI, IndexReferent>();

	/**
	** Table of index identities accepted for querying.
	**
	** Not synchronized.
	*/
	final protected Map<FreenetURI, IndexIdentity>
	idrft_Q = new HashMap<FreenetURI, IndexIdentity>();

	/**
	** {@link BlockingQueue} for completed {@link IndexIdentity} retrievals.
	*/
	final protected BlockingQueue<IndexIdentity>
	id_done = new LinkedBlockingQueue<IndexIdentity>();

	/**
	** {@link BlockingQueue} for completed (index, term) queries.
	*/
	final protected BlockingQueue<IndexQuery>
	query_done = new LinkedBlockingQueue<IndexQuery>();

	// TODO error maps..

	/**
	** Table of results.
	*/
	final protected ConcurrentMap<IndexQuery, Collection<TermEntry>>
	query_results = new ConcurrentHashMap<IndexQuery, Collection<TermEntry>>(0x10, (float)0.75, CONCURRENCY_LEVEL);

	/**
	** Sum of trust-scores of all {@link IndexIdentity}s that have been queued
	** for querying.
	**
	** TODO unused. maybe remove.
	*/
	protected float trust_mass;

	/**
	** Constructs a new InterdexQuery and TODO starts executing it.
	*/
	public InterdexQuery(String subj, Set<IndexIdentity> roots) {
		subject = subj;
		for (IndexIdentity id: roots) {
			idrft.put(id.reqID, id);
			queryIndex(id);
			// TODO anything else?
		}
		subjdef = new TermDefinition(subj);
		terms.addVertex(subjdef);
		queryTerm(subjdef);
	}

	public float getTotalTrustMass() {
		// TODO maybe provide a way to re-calculate this from all the identities...
		return trust_mass;
	}

	/**
	** Returns the effective relevance of all terms relative to the subject
	** term for this query.
	**
	** The effective relevance of a term (relative to the subject term) is
	** defined as the highest-relevance path from the subject to that term,
	** where the relevance of a path is defined as the product of all the edges
	** on it, where each edge from S to T has weight equal to their {@linkplain
	** TermRelation#getWeightedRelevance() average weighted relevance}.
	**
	** This definition was chosen because there may be an infinite number of
	** paths between two terms in the semantic web; we want the "most direct"
	** one that exists (by consensus; so we use average weighted relevance).
	**
	** This is equivalent to the shortest-path problem from the subject term to
	** the target term, with each edge from S to T having weight {@code
	** -log(R)}, with R being the average weighted relevance of (S, T).
	**
	** This method is not thread safe.
	*/
	public float getEffectiveRelevance(TermDefinition targdef) {
		// OPTIMISE this could be made *much* more efficient by caching the
		// results for each vertex.
		float f = (float)Math.exp(-(new DijkstraShortestPath(terms, subjdef, targdef).getPathLength()));
		assert(0 <= f && f <= 1);
		return f;
	}

	/**
	** Add a new index to the index table and possibly load the identity for
	** it, if {@link #acceptIndexLoad()} agrees.
	**
	** Attempt to retrieve an {@link IndexIdentity}.
	*/
	protected boolean addIndexReferent(TermDefinition srcdef, FreenetURI uri, IndexIdentity id, float relevance) {
		IndexReferent ref = idrft.get(uri);
		if (ref == null) {
			idrft.put(uri, ref = new IndexReferent(uri));
		}
		ref.addTermReferent(srcdef, id, relevance);
		if (acceptIndexLoad(ref)) {
			// TODO start a new request to retrieve the IndexIdentity for this FreenetURI
			return true;
		} else {
			// TODO rejects queue?
			return false;
		}
	}

	/**
	** Whether to load the {@link IndexIdentity} for an {@link IndexReferent}.
	**
	** TODO currently the semantics of this is shit, make it better.
	*/
	protected boolean acceptIndexLoad(IndexReferent rft) {
		return !idrft.containsKey(rft.reqID) && rft.hasTermRelevance(RELEVANCE_MIN);
	}

	/**
	** Whether to accept a new {@link IndexIdentity} to search.
	**
	** TODO atm this just returns {@code true}. Future extensions might take
	** into account, eg. the trust score of the identity.
	*/
	protected boolean acceptIndexQuery(IndexIdentity id) {
		return true;
	}

	/**
	** Add an {@link IndexIdentity} to the active list, and queue {@link
	** IndexQuery}s to search all terms in this index. This method assumes that
	** the identity does not already exist in the index map; it is up to the
	** calling code to ensure that this holds.
	**
	** This method is not thread-safe.
	**
	** @return Whether the queries were actually queued.
	*/
	protected boolean queryIndex(IndexIdentity id) {
		assert(idrft.get(id.reqID) == id);
		if (idrft_Q.containsKey(id.reqID)) {
			throw new IllegalArgumentException("The identity has already been queued");
		}
		if (acceptIndexQuery(id)) {
			idrft_Q.put(id.reqID, id);
			// update the trust mass
			trust_mass += id.trust;
			// pair up this index with all terms in terms_Q, and schedule
			// them for execution
			for (TermDefinition termdef: terms_Q) {
				exec.execute(new IndexQuery(id, termdef, query_done));
			}
			return true;
		} else {
			// TODO have a rejects set? we could just use idrft.keySet() - idrft_Q.keySet()
			// but that would include non-loaded referents too.
			return false;
		}
	}

	/**
	** Add a new term relation to the terms graph, and maybe execute queries
	** for it. TODO DOCUMENT ETC ETC ETC ETC
	**
	** This method assumes that the {@link TermDefinition} for the subject term
	** has already been added to the terms graph; it is up to the calling code
	** to ensure that this holds.
	**
	** This method is not thread-safe.
	*/
	protected void addTermRelation(TermDefinition srcdef, String term, IndexIdentity id, float relevance) {
		assert(terms.containsVertex(srcdef));
		TermDefinition dstdef = new TermDefinition(term);
		boolean added = terms.addVertex(dstdef);

		// set relevance rating for the relation
		TermRelation rel = terms.getEdge(srcdef, dstdef);
		if (rel == null) {
			rel = terms.addEdge(srcdef, dstdef);
		}
		rel.addRelevanceRating(id, relevance);
		float av_relevance = rel.getWeightedRelevance();
		assert(0 <= av_relevance && av_relevance <= 1);
		terms.setEdgeWeight(rel, -Math.log(av_relevance));

		// recurse if this was a new one
		if (added) { queryTerm(dstdef); }
		// TODO maybe queryTerm() even if this was rejected earlier; but this
		// requires a way to get at the TermDefinition already in the graph.
		// a rejects queue (map of T->T) would solve this
	}

	/**
	** Whether to accept a new term to search.
	**
	** TODO currently the semantics of this is shit, make it better.
	*/
	protected boolean acceptTermQuery(TermDefinition dstdef) {
		return getEffectiveRelevance(dstdef) > RELEVANCE_MIN;
	}

	/**
	** Attempt to queue {@link IndexQuery}s to search the given term in all
	** {@link IndexIdentity}s.
	**
	** This method is not thread-safe.
	**
	** @return Whether the queries were actually queued.
	*/
	protected boolean queryTerm(TermDefinition dstdef) {
		assert(terms.vertexSet().contains(dstdef));
		if (terms_Q.contains(dstdef)) {
			throw new IllegalArgumentException("The term has already been queued");
		}
		if (acceptTermQuery(dstdef)) {
			terms_Q.add(dstdef);
			// pair up this term with all indexes, and add them to the task queeu
			for (IndexIdentity id: idrft_Q.values()) {
				exec.execute(new IndexQuery(id, dstdef, query_done));
			}
			return true;
		} else {
			// TODO add to rejects? maybe unnecassary; this is just
			// terms.vertexSet() - terms_Q
			return false;
		}
	}

	@Override public void run() {
		try {
			do {
				while (!query_done.isEmpty()) {
					// get completed task
					IndexQuery res_query = query_done.poll(1, TimeUnit.SECONDS);

					TermDefinition res_termdef = res_query.termdef;
					Collection<TermEntry> entries = null;// TODO res_query.getResult();
					IndexIdentity res_id = res_query.id;

					// update the termdefs
					res_termdef.addDefiningIdentity(res_id);

					for (TermEntry en: entries) {

						if (en instanceof TermIndexEntry) {
							float rel_rel = en.getRelevance();
							FreenetURI rel_uri = ((TermIndexEntry)en).getIndex();
							addIndexReferent(res_termdef, rel_uri, res_id, rel_rel);

						} else if (en instanceof TermTermEntry) {
							float rel_rel = en.getRelevance();
							String rel_term = ((TermTermEntry)en).getTerm();
							addTermRelation(res_termdef, rel_term, res_id, rel_rel);

						} else {
							// TODO add it to results set for the term
						}
					}
				}

				while (!id_done.isEmpty()) {
					IndexIdentity id = id_done.poll(1, TimeUnit.SECONDS);
					queryIndex(id);
				}

			} while (Math.log(1)!=0 /* tasks active or tasks complete not handled */);
		} catch (InterruptedException e) {
			// TODO.. setError(new TaskAbortException("Query was interrupted", e);
		}
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


}
