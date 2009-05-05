/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import java.util.HashMap;

/**
** @author infinity0
*/
public class IndexNode {

	// TODO
	final static short MAX_SIZE = 4096; // max size of tokenmap

	final byte[] prefix_bytes;
	final IndexFilter filter;

	IndexNode[] child; // prefix_bytes + i == child[i].prefix_bytes
	HashMap<Token, IndexEntry> tmap;

	IndexNode(IndexFilter f) { this(f, new byte[]{}); }
	IndexNode(IndexFilter f, byte[] pre) {
		prefix_bytes = pre;
		filter = f;
	}

}
