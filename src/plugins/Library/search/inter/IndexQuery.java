/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search.inter;

import plugins.Library.library.Index;

import java.util.Set;

/**
** DOCUMENT
**
** @author infinity0
*/
public class IndexQuery /* implements Index */ {

	/**
	** Reference to the index.
	*/
	/*final*/ public Index index;

	/**
	** Score for this index in the WoT.
	*/
	/*final*/ public int WoT_score;

	/**
	** Number of hops this index is from the inner-group of indexes.
	*/
	protected int WoT_hops;

	/**
	** References to this index that we've seen, from other indexes.
	**
	** TODO decide if this is needed.
	*/
	protected int WoT_refs;

	/**
	** Mutex for manipulating the below sets.
	*/
	protected Object terms_lock;

	protected Set<String> terms_done;
	protected Set<String> terms_started;
	protected Set<String> terms_pending;

}
