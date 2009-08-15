/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search.inter;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;

/**
** DOCUMENT
**
** @author infinity0
*/
public class TermRelation {

	/**
	** Parent query. Used to get the total trust mass in order to calculate
	** {@link #getAvPercvRelevance()}.
	*/
	final public InterdexQuery parent_query;

	/**
	** Subject term
	*/
	final public String subject;

	/**
	** Subject term for these results.
	*/
	final public String target;

	/**
	** Map of relevances from the {@link IndexIdentity}s that defined it.
	*/
	final public Map<IndexIdentity, Float> rel = new HashMap<IndexIdentity, Float>();

	/**
	** Incremental calculation of effective relevance. May not be exact if
	** if additional indexes were encountered referencing this term, after the
	** last update to this field.
	*/
	protected float rel_;

	public TermRelation(String s, String t, InterdexQuery q) {
		subject = s;
		target = t;
		parent_query = q;
	}

	/**
	** TODO implement.
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
	** We choose 1/3 as an arbitrary compromise between majority-ignorance
	** (most people might not know obscure term X is related to obscure
	** term Y) and minority-attack (one person might inject information to
	** distort a search query; we shouldn't expect WoT to deal with minor
	** corner cases). '''This value can be discussed further'''.
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
	public float getAvPercvRelevance() {
		throw new UnsupportedOperationException("not implemented");
	}


	/**
	** As required by the contract of {@link org.jgrapht.Graph}.
	*/
	@Override public boolean equals(Object o) {
		if (o == this) { return true; }
		if (!(o instanceof TermRelation)) { return false; }
		TermRelation rel = (TermRelation)o;
		return subject.equals(rel.subject) && target.equals(rel.target);
	}

	@Override public int hashCode() {
		return subject.hashCode() ^ target.hashCode();
	}


}
