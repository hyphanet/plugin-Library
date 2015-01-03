/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.index;

import freenet.library.util.BytePrefixKey;

/**
** A {@link BytePrefixKey} backed by the 32-byte routing key for the {@link
** freenet.keys.Key NodeKey} constructed from a FreenetURI.
**
** @author infinity0
*/
public class URIKey extends BytePrefixKey<URIKey> {

	public URIKey() {
		super(0x20);
	}

	/**
	 * Could be the routingkey.
	 * @param h
	 */
	public URIKey(byte[] h) {
		super(0x20, h);
	}

	/*========================================================================
	  public interface BytePrefixKey
	 ========================================================================*/

	@Override public URIKey clone() {
		return new URIKey(hash);
	}

}
