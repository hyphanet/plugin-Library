/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search.inter;

import freenet.keys.FreenetURI;

import java.util.Map;
import java.util.HashMap;

/**
** Keeps track of the term-relevance assigned to a given {@link IndexIdentity}
** by other identities. DOCUMENT
**
** @author infinity0
*/
public class IndexReferent {

	/**
	** Request ID for the identity
	*/
	final public FreenetURI reqID;

	final protected Map<TermDefinition, WeightedRelevance> refs;

	public IndexReferent(FreenetURI uri) {
		reqID = uri;
		refs = new HashMap<TermDefinition, WeightedRelevance>();
	}

	/**
	** Constructs a new referent '''backed by''' the given referent.
	*/
	protected IndexReferent(IndexReferent rft) {
		reqID = rft.reqID;
		refs = rft.refs;
	}

	public void addTermReferent(TermDefinition termdef, IndexIdentity referrer, float relevance) {
		WeightedRelevance wrel = refs.get(termdef);
		if (wrel == null) {
			refs.put(termdef, wrel = new WeightedRelevance());
		}
		wrel.addRelevanceRating(referrer, relevance);
	}

	public float getWeightedRelevanceTo(TermDefinition termdef) {
		return refs.get(termdef).getWeightedRelevance();
	}

	/**
	** Helper method for {@link InterdexQuery#acceptIndexLoad(IndexReferent)}.
	** Tests whether this referent is has at least the given minimum relevance
	** to any term.
	**
	** TODO this is shit, make it better, or replace with something better.
	*/
	public boolean hasTermRelevance(float min) {
		for (WeightedRelevance r: refs.values()) {
			if (r.getWeightedRelevance() >= min) {
				return true;
			}
		}
		return false;
	}

}
