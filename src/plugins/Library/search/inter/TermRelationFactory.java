/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search.inter;

import org.jgrapht.EdgeFactory;

/**
** DOCUMENT
**
** @author infinity0
*/
public class TermRelationFactory implements EdgeFactory<TermDefinition, TermRelation> {

	// TODO maybe get rid of this, this is no longer needed now that we have TermDefinition
	final protected InterdexQuery parent_query;

	public TermRelationFactory(InterdexQuery q) {
		parent_query = q;
	}

	@Override public TermRelation createEdge(TermDefinition subjdef, TermDefinition targdef) {
		return new TermRelation(subjdef, targdef, parent_query);
	}

}

