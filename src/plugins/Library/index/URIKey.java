/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.util.BytePrefixKey;

import freenet.keys.FreenetURI;

/**
** A {@link BytePrefixKey} backed by the 32-byte routing key (or for SSKs,
** pubkey hash) of a {@link FreenetURI}.
**
** @author infinity0
*/
public class URIKey extends BytePrefixKey<URIKey> {

	public URIKey() {
		super(32);
	}

	public URIKey(byte[] h) {
		super(32, h);
	}

	public URIKey(FreenetURI u) {
		super(32, u.getRoutingKey());
	}

	/*========================================================================
	  public interface BytePrefixKey
	 ========================================================================*/

	@Override public URIKey clone() {
		return new URIKey(hash);
	}

}
