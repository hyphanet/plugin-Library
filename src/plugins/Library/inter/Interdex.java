/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.inter;

import freenet.keys.FreenetURI;

import java.util.Map;

/**
** DOCUMENT
**
** @author infinity0
*/
public class Interdex /* implements Index */ {




	/**
	** Class to handle a request for a given subject term.
	*/
	public class Query {

		/*final*/ public String subject;

		/*final*/ public Map<FreenetURI, IndexQuery> queries;

		/*final*/ public Map<String, TermResults> results;

		/**
		** TODO implement
		**
		** Returns the average perceived relevance of a given term relative to
		** a parent subject term. "Perceived" because it uses data from indexes
		** in the WoT's nearer rings; "average" because it generates an average
		** rating from the individual values encountered.
		**
		** The value is calculated from the mean of all the relevance values
		** encountered for this subject-term pair (with the weights /
		** probabilities being the normalised trust score of the index which
		** defined the pairing), multiplied by 3. (Values greater than 1 are
		** normalised back down to 1).
		**
		** For example, if we have encountered 9 indexes all with equal weight,
		** and 2 of them defined rel(S, T) = 0.5, then this method will return
		** 0.5 * min(1, 3*2/9) = 1/3.
		**
		** In other words, if less than 1/3 of the total index trust-mass gives
		** a relevance rating for a given subject-term pair, the value returned
		** by this method will be lower (than it would be if more indexes had
		** had supplied a rating).
		**
		** Formally, let
		**
		** : S := subject
		** : T := term
		** : A := { all indexes encountered }
		** : R := { i &#x220A; A: i.rel(S, T) is defined }
		** : W<sub>i</sub> := weight of index i (eg. WoT trust score)
		**
		** Then this method calculates:
		**
		** : (&Sigma;<sub>i&#x220A;R</sub>W<sub>i</sub>&middot;i.rel(S, T))
		**   &middot; min(1, 3&middot;&Sigma;<sub>i&#x220A;R</sub>W<sub>i</sub>
		**   / &Sigma;<sub>i&#x220A;A</sub>W<sub>i</sub>)
		**
		*/
		public float getAvPercvRelevance(String subj, String term) {
			throw new UnsupportedOperationException("not implemented");
		}

		/**
		** TODO implement. maybe use JGraphT.
		**
		** Returns the effective relevance of all terms relative to the subject
		** term for this query.
		**
		** The effective relevance of a term (relative to the subject term) is
		** defined as the highest-relevance path from the subject to that term,
		** where the relevance of a path is defined as the product of all the
		** edges on it, where each edge from S to T has weight equal to their
		** {@linkplain getAvPercvRelevance(String, String) average perceived
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

	}


}
