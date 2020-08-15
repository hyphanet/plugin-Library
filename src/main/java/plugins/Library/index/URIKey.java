/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.util.BytePrefixKey;

import freenet.keys.FreenetURI;
import freenet.keys.BaseClientKey;
import freenet.keys.ClientKey;

/**
** A {@link BytePrefixKey} backed by the 32-byte routing key for the {@link
** freenet.keys.Key NodeKey} constructed from a {@link FreenetURI}.
**
** @author infinity0
*/
public class URIKey extends BytePrefixKey<URIKey> {

	public URIKey() {
		super(0x20);
	}

	public URIKey(byte[] h) {
		super(0x20, h);
	}

	public URIKey(FreenetURI u) throws java.net.MalformedURLException {
		super(0x20, getNodeRoutingKey(u));
	}

	public static byte[] getNodeRoutingKey(FreenetURI u) throws java.net.MalformedURLException {
		try {
			return ((ClientKey)BaseClientKey.getBaseKey(u.isUSK()? u.sskForUSK(): u)).getNodeKey().getRoutingKey();
		} catch (ClassCastException e) {
			throw new UnsupportedOperationException("Could not get the node routing key for FreenetURI " + u + ". Only CHK/SSK/USK/KSKs are supported.");
		}
	}

	/*========================================================================
	  public interface BytePrefixKey
	 ========================================================================*/

	@Override public URIKey clone() {
		return new URIKey(hash);
	}

}
