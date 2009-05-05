/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

/**
** @author infinity0
*/
public class Token { // TODO: maybe turn this into MD5Token extends AbstractToken?

	// TODO
	final String token; // MD5 hash

	Token(String md5hash) { token = md5hash; }
	Token(String keyword, boolean whatever) {
		// TODO: MD5 it
		// token = MD5(keyword)
		token = "";
	}

}
