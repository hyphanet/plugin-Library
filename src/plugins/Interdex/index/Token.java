/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import java.security.MessageDigest;
import java.util.Arrays;

import plugins.Interdex.util.PrefixTreeMap.PrefixKey;
import plugins.Interdex.util.PrefixTreeMap.AbstractPrefixKey;

/**
** MD5 hash of a string, implementing the PrefixKey interface. Used to map
** keywords to entries in the index.
**
** @author infinity0
** @see PrefixTreeMap.PrefixKey
*/
public class Token extends AbstractPrefixKey implements PrefixKey {

	final byte[] hash;

	public Token(byte[] h) {
		hash = new byte[h.length];
		for (int i=0; i<h.length; ++i) { hash[i] = h[i]; }
	}
	public Token(String w) {
		hash = MD5(w);
	}

	public String toString() { return new String(hash); }

	public String toHexString() { return hexString(hash); }

	/**
	** Returns the hex representation of a byte array. From XMLLibrarian.
	*/
	public static String hexString(byte[] hash) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < hash.length; i++) {
			int halfbyte = (hash[i] >>> 4) & 0x0F;
			int two_halfs = 0;
			do {
				if ((0 <= halfbyte) && (halfbyte <= 9))
					buf.append((char) ('0' + halfbyte));
				else
					buf.append((char) ('a' + (halfbyte - 10)));
				halfbyte = hash[i] & 0x0F;
			} while (two_halfs++ < 1);
		}
		return buf.toString();
	}

	/**
	** Returns the MD5 byte array of a String. From XMLLibrarian.
	*/
	public static byte[] MD5(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] b = text.getBytes("UTF-8");
			md.update(b, 0, b.length);
			return md.digest();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/************************************************************************
	 * public interface PrefixTreeMap.PrefixKey
	 ************************************************************************/

	public Object clone() {
		return (Object)new Token(hash);
	}

	public int symbols() {
		return 256;
	}

	public int size() {
		return hash.length;
	}

	public int get(int i) {
		return hash[i];
	}

	public void set(int i, int v) {
		hash[i] = (byte)v;
	}

	public void clear(int i) {
		hash[i] = 0;
	}

	/************************************************************************
	 * public class Object
	 ************************************************************************/

	public boolean equals(Object o) {
		if (o instanceof Token) {
			return Arrays.equals(hash, ((Token)o).hash);
		}
		return false;
	}

	public int hashCode() {
		return Arrays.hashCode(hash);
	}

}
