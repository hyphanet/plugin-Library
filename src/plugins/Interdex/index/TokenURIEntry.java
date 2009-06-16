/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import freenet.keys.FreenetURI;

/**
** @author infinity0
*/
public class TokenURIEntry extends TokenEntry {

	final FreenetURI uri;
	final String word;

	int position;
	int relevance;

	public TokenURIEntry(String w, FreenetURI u) {
		word = w;
		uri = u;
	}

}
