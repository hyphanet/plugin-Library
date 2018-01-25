/*
 * This code is part of Freenet. It is distributed under the GNU General Public License, version 2
 * (or at your option any later version). See http://www.gnu.org/ for further details of the GPL.
 */
package plugins.Library.search.inter;

import plugins.Library.Index;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

/**
 ** DOCUMENT
 **
 ** @author infinity0
 */
public class IndexQuery /* implements Index */ {

  /**
   ** Reference to the index.
   */
  final public Index index;

  /**
   ** Score for this index in the WoT.
   */
  final public float WoT_score;

  /**
   ** Minimum number of hops this index is from the inner-group of indexes.
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
  final protected Object terms_lock = new Object();

  final protected Set<String> terms_done = new HashSet<String>();
  final protected Set<String> terms_started = new HashSet<String>();
  final protected Set<String> terms_pending;

  public IndexQuery(Index i, float s, Set<String> terms, int hops) {
    index = i;
    WoT_score = s;
    WoT_hops = hops;
    terms_pending = new HashSet<String>(terms);
  }

  public IndexQuery(Index i, float s, String root_term) {
    this(i, s, Collections.singleton(root_term), 0);
  }

  public void updateMinimumHops(int h) {
    if (h < WoT_hops) {
      WoT_hops = h;
    }
  }

}
