/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search.inter;

/**
** Represents a relevance relation between a subject term and a target term,
** and keeps track of the {@link IndexIdentity}s that specified this relation.
** DOCUMENT
**
** @author infinity0
*/
public class TermRelation extends WeightedRelevance {

	/**
	** Parent query. TODO maybe remove
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

	public TermRelation(TermDefinition s, TermDefinition t, InterdexQuery q) {
		subjdef = s;
		targdef = t;
		parent_query = q;
	}

	/**
	** Returns the average weighted relevance of this entry, adjusted for
	** {@link IndexIdentity}s that did not define this relation. This is done
	** by multiplying {@link WeightedRelevance#getWeightedRelevance()} by
	** triple the weighted ratio of (indexes defining this relation) to
	** (indexes defining the subject term). (Triple-ratios greater than 1 are
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
	** corner cases). '''This value should be discussed further'''.
	**
	** Formally, let
	**
	** : V := {@link WeightedRelevance#getWeightedRelevance()}
	** : D := {@link #subjdef}.{@link TermDefinition#def def}
	** : R := the {@link #rel relevance map}
	** : W<sub>i</sub> := weight of index i (ie. WoT trust score)
	** : W<sub>R</sub> := total weight of R ==
	**   &Sigma;<sub>i&#x220A;dom(R)</sub>W<sub>i</sub>
	** : W<sub>D</sub> := total weight of D ==
	**   &Sigma;<sub>i&#x220A;D</sub>W<sub>i</sub>
	**
	** Then this method calculates:
	**
	** : V * min(1, 3&middot;W<sub>R</sub> / W<sub>D</sub>)
	*/
	public float getWeightedRelevance() {
		return super.getWeightedRelevance() * Math.min(1, 3 * local_trust_mass / subjdef.getTrustMass());
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
