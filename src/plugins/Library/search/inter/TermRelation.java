/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search.inter;

import java.util.Arrays;
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
	** {@link #getWeightedRelevance()}.
	*/
	final public InterdexQuery parent_query;

	/**
	** Subject {@link TermDefinition} of the relation.
	*/
	final public TermDefinition subjdef;

	/**
	** Target {@link TermDefinition} of the relation.
	*/
	final public TermDefinition targdef;

	/**
	** Map of relevances from the {@link IndexIdentity}s that defined it.
	*/
	final protected Map<IndexIdentity, Float> rel = new HashMap<IndexIdentity, Float>();

	/**
	** Weighted sum of the relevance map. This is the term
	**
	** : (&Sigma;<sub>i&#x220A;R</sub>W<sub>i</sub>&middot;i.rel(S, T))
	**
	** in the definition for {@link #getWeightedRelevance()}.
	*/
	protected float local_relevance_mass;

	/**
	** Total trust mass of the relevance map. This is the term
	**
	** : W<sub>R</sub> == &Sigma;<sub>i&#x220A;R</sub>W<sub>i</sub>
	**
	** in the definition for {@link #getWeightedRelevance()}.
	*/
	protected float local_trust_mass;

	public TermRelation(TermDefinition s, TermDefinition t, InterdexQuery q) {
		subjdef = s;
		targdef = t;
		parent_query = q;
	}

	public void addRelevanceRating(IndexIdentity id, float relevance) {
		if (rel.put(id, relevance) != null) {
			throw new AssertionError("Cannot add two relations from the same IndexIdentity to a TermRelation. This indicates a bug in the code.");
		}
		local_relevance_mass += id.trust * relevance;
		local_trust_mass += id.trust;
	}

	/**
	** Returns the average weighted relevance of a given term relative to
	** a parent subject term. "Weighted" because it uses data from indexes
	** in the WoT's nearer rings; "average" because it generates an average
	** rating from the individual values encountered.
	**
	** The value is calculated from the weighted mean of all relevance values
	** encountered for this {@link TermRelation}, multiplied by triple the
	** weighted ratio of (indexes defining this {@code TermRelation}) to
	** (indexes defining the {@link TermDefinition} of the subject).
	** Triple-ratios greater than 1 are normalised back down to 1).
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
	** : S := subject term
	** : T := target term
	** : A := { all indexes encountered }
	** : D := { i &#x220A; A: i[S] is defined }
	** : R := { i &#x220A; A: i.rel(S, T) is defined }
	** : W<sub>i</sub> := weight of index i (eg. WoT trust score)
	** : W<sub>R</sub> := total weight of R ==
	**   &Sigma;<sub>i&#x220A;R</sub>W<sub>i</sub>
	** : W<sub>D</sub> := total weight of D ==
	**   &Sigma;<sub>i&#x220A;D</sub>W<sub>i</sub>
	**
	** Then this method calculates:
	**
	** : (&Sigma;<sub>i&#x220A;R</sub>W<sub>i</sub>&middot;i.rel(S, T) /
	**   W<sub>R</sub>) &middot; min(1, 3&middot;W<sub>R</sub> / W<sub>D</sub>)
	*/
	public float getWeightedRelevance() {
		return local_relevance_mass / local_trust_mass * Math.min(1,
		       3 * local_trust_mass / targdef.getTrustMass());
	}

	/**
	** @param recalculate Whether to recalculate this value, or return the
	**        cached version (which may be slightly different due to rounding.)
	*/
	public float getWeightedRelevance(boolean recalculate) {
		if (recalculate) {
			float[] weighted_relevances = new float[rel.size()];
			float[] trust_weights = new float[rel.size()];
			int i=0;
			for (Map.Entry<IndexIdentity, Float> en: rel.entrySet()) {
				IndexIdentity id = en.getKey();
				weighted_relevances[i] = id.trust * en.getValue();
				trust_weights[i] = id.trust;
				++i;
			}
			// add smallest terms first to reduce floating point error
			Arrays.sort(weighted_relevances);
			Arrays.sort(trust_weights);
			int relevance_mass = 0, trust_mass = 0;
			for (i=0; i<trust_weights.length; ++i) {
				relevance_mass += weighted_relevances[i];
				trust_mass += trust_weights[i];
			}
			local_relevance_mass = relevance_mass;
			local_trust_mass = trust_mass;
		}
		return getWeightedRelevance();
	}

	/**
	** As required by the contract of {@link org.jgrapht.Graph}.
	*/
	@Override public boolean equals(Object o) {
		if (o == this) { return true; }
		if (!(o instanceof TermRelation)) { return false; }
		TermRelation rel = (TermRelation)o;
		return subjdef.equals(rel.subjdef) && targdef.equals(rel.targdef);
	}

	@Override public int hashCode() {
		return subjdef.hashCode() ^ targdef.hashCode();
	}

}
