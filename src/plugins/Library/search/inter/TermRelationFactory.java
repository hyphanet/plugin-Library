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
public class TermRelationFactory implements EdgeFactory<String, TermRelation> {

	final protected InterdexQuery parent_query;

	public TermRelationFactory(InterdexQuery q) {
		parent_query = q;
	}

	@Override public TermRelation createEdge(String subject, String target) {
		return new TermRelation(subject, target, parent_query);
	}

}

