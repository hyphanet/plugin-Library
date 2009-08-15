/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search.inter;

import plugins.Library.library.Index;

import freenet.keys.FreenetURI;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
** DOCUMENT
**
** @author infinity0
*/
public class IndexIdentity /* implements Index */ {

	/**
	** Request ID for the identity
	*/
	final public FreenetURI reqID;

	/**
	** Reference to the index.
	*/
	final public Index index;

	/**
	** Score for this index in the WoT.
	*/
	final public float trust;

	/**
	** Minimum number of hops this index is from the inner-group of indexes.
	*/
	protected int WoT_hops;

	public IndexIdentity(FreenetURI u, Index i, float t) {
		reqID = u;
		index = i;
		trust = t;
	}

	// TODO probably move this into InterdexQuery
	public void updateMinimumHops(int h) {
		if (h < WoT_hops) { WoT_hops = h; }
	}

}
