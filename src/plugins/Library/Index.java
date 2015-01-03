/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library;

import freenet.library.index.TermEntry;
import freenet.library.util.exec.Execution;

import java.util.Set;

/**
** Represents the data for an index.
**
** @author infinity0
*/
public interface Index {

	/**
	** Non-blocking fetch of the entries associated with a given term.
	**
	** DOCUMENT
	*/
	public Execution<Set<TermEntry>> getTermEntries(String term);
}
