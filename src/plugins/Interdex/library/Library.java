/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import java.util.Map;
import java.util.HashMap;

/**
** Holds the collection of indexes
**
** @author infinity0
*/
public class Library {

	private Library() { }

	/**
	** Holds all the read-indexes.
	*/
	private static Map<String, Index> rtab = new HashMap<String, Index>();

	/**
	** Holds all the writeable indexes.
	*/
	private static Map<String, WriteableIndex> wtab = new HashMap<String, WriteableIndex>();

	/**
	** Holds all the virtual indexes.
	*/
	private static Map<String, VirtualIndex> vtab = new HashMap<String, VirtualIndex>();

	/**
	** Holds all the bookmarks (aliases into the rtab).
	*/
	private static Map<String, String> bookmarks = new HashMap<String, String>();

}
