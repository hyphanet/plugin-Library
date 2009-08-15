/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search.inter;

import java.util.Set;
import java.util.HashSet;

/**
** DOCUMENT
**
** @author infinity0
*/
public class TermDefinition {

	/**
	** Subject term
	*/
	final public String subject;

	/**
	** Set of all indexes which have a definition for the subject term.
	*/
	final protected Set<IndexIdentity> def = new HashSet<IndexIdentity>();

	/**
	** Total trust mass of the relevance map. This is the term
	**
	** : W<sub>D</sub> == &Sigma;<sub>i&#x220A;D</sub>W<sub>i</sub>
	**
	** in the definition for {@link TermRelation#getWeightedRelevance()}.
	*/
	protected float local_trust_mass;

	public TermDefinition(String subj) {
		subject = subj;
	}

	public void addDefiningIdentity(IndexIdentity id) {
		if (!def.add(id)) {
			throw new AssertionError("Cannot add the same identity to a TermDefinition twice. This indicates a bug in the code.");
		}
		local_trust_mass += id.trust;
	}

	public float getTrustMass() {
		return local_trust_mass;
	}

	public float getTrustMass(boolean recalculate) {
		if (recalculate) {
			// TODO
		}
		return local_trust_mass;
	}

	/**
	** As required by the contract of {@link org.jgrapht.Graph}.
	*/
	@Override public boolean equals(Object o) {
		if (o == this) { return true; }
		if (!(o instanceof TermDefinition)) { return false; }
		TermDefinition rel = (TermDefinition)o;
		return subject.equals(rel.subject);
	}

	@Override public int hashCode() {
		return subject.hashCode();
	}

}
