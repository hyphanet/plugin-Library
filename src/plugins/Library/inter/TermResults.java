/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.inter;

import java.util.Collection;
import java.util.Map;

/**
** DOCUMENT
**
** @author infinity0
*/
public class TermResults {

	/**
	** Subject term for these results.
	*/
	/*final*/ public String subject;

	/**
	** Subject term for the original query.
	*/
	/*final*/ public String query_subject;

	/**
	** Map of parent terms to the relevances that were seen for this term in
	** the results for the parent.
	*/
	/*final*/ public Map<String, Collection<Float>> parents;

	/**
	** Incremental calculation of effective relevance. May not be exact if
	** if additional indexes were encountered referencing this term, after the
	** last update to this field.
	*/
	protected float av_pcv_rel_;

	/**
	** Get the cached effective relevance. Use this in preference to {@link
	** Interdex#getEffectiveRelevance()} when the search is still ongoing.
	**
	** TODO this should be a good estimate that is always greater than the
	** actual value...
	*/
	public float getCachedAveragePerceivedRelevance() {
		return av_pcv_rel_;
	}


}
