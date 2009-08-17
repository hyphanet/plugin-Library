/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search.inter;

import plugins.Library.util.FloatMethods;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;

/**
** Represents a weighted average of relevance scores.
**
** @author infinity0
*/
public class WeightedRelevance {

	/**
	** Map of relevances from the {@link IndexIdentity}s that defined it.
	*/
	final protected Map<IndexIdentity, Float> rel = new HashMap<IndexIdentity, Float>();

	/**
	** Weighted sum of the relevance map. This is the term
	**
	** : &Sigma;<sub>i&#x220A;dom(R)</sub>W<sub>i</sub>&middot;R(i)
	**
	** in the definition for {@link #getWeightedRelevance()}.
	*/
	protected float local_relevance_mass;

	/**
	** Total trust mass of the relevance map. This is the term
	**
	** : W<sub>R</sub> == &Sigma;<sub>i&#x220A;dom(R)</sub>W<sub>i</sub>
	**
	** in the definition for {@link #getWeightedRelevance()}.
	*/
	protected float local_trust_mass;

	public WeightedRelevance() { }

	public void addRelevanceRating(IndexIdentity id, float relevance) {
		Float old = rel.put(id, relevance);
		if (old != null) {
			// ignore duplicate ratings in an index
			// maybe log this?
			rel.put(id, old);
			return;
		}
		local_relevance_mass += id.trust * relevance;
		local_trust_mass += id.trust;
	}

	/**
	** Returns the mean weighted relevance, with the weights taken from the
	** trust score of the {@link IndexIdentity} for each corresponding
	** relevance rating.
	**
	** Formally, let
	**
	** : R := the {@link #rel relevance map}
	** : W<sub>i</sub> := weight of index i (ie. WoT trust score)
	** : W<sub>R</sub> := total weight of R ==
	**   &Sigma;<sub>i&#x220A;dom(R)</sub>W<sub>i</sub>
	**
	** Then this method calculates:
	**
	** : &Sigma;<sub>i&#x220A;dom(R)</sub>W<sub>i</sub>&middot;R(i) /
	**   W<sub>R</sub>
	*/
	public float getWeightedRelevance() {
		return local_relevance_mass / local_trust_mass;
	}

	/**
	** Returns the mean weighted relevance.
	**
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
			local_relevance_mass = FloatMethods.sumPositive(weighted_relevances);
			local_trust_mass = FloatMethods.sumPositive(trust_weights);
		}
		return getWeightedRelevance();
	}

}
