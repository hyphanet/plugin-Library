/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import plugins.Interdex.util.PrefixTree.PrefixKey;
import plugins.Interdex.util.PrefixTree.AbstractPrefixKey;

import java.security.MessageDigest;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/**
** MD5 hash of a string, implementing the PrefixKey interface. Used to map
** keywords to entries in the index.
**
** @author infinity0
*/
public class Token extends AbstractPrefixKey implements PrefixKey {

	/**
	** Internal pool of tokens.
	*/
	public static Map<String, WeakReference<Token>> intern = new WeakHashMap<String, WeakReference<Token>>();

	/**
	** Returns the canonical {@link Token} for a given string.
	**
	** For even better performance, make sure the string parameter is from the
	** {@link String} internal pool.
	**
	** @see String#intern()
	*/
	public static synchronized Token intern(String s) {
		Token t;
		if (!intern.containsKey(s) || (t = intern.get(s).get()) == null) {
			t = new Token(s);
			intern.put(s, new WeakReference<Token>(t));
		}
		return t;
	}

	/**
	** Main data of the token.
	*/
	final byte[] hash;

	/**
	** Cache for the hexstring representation.
	*/
	transient String str_ = null;

	public Token(byte[] h) {
		if (h.length != 16) {
			throw new IllegalArgumentException("Byte array must be 16 bits long for a MD5 hash.");
		}
		hash = new byte[16];
		for (int i=0; i<16; ++i) { hash[i] = h[i]; }
	}
	public Token() {
		hash = new byte[16];
	}
	public Token(String w) {
		hash = MD5(w);
	}

	public String toString() {
		if (str_ == null) {
			str_ = hexString(hash);
		}
		return str_;
	}

	// URGENT have a static factory fromHexString()

	public String toByteString() { return new String(hash); }

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

	/*========================================================================
	  public interface PrefixTreeMap.PrefixKey
	 ========================================================================*/

	public Object clone() {
		return new Token(hash);
	}

	public int symbols() {
		return 256;
	}

	public int size() {
		return hash.length;
	}

	public int get(int i) {
		return hash[i] & 0xFF;
	}

	public void set(int i, int v) {
		hash[i] = (byte)v;
	}

	public void clear(int i) {
		hash[i] = 0;
	}

	/*========================================================================
	  public class Object
	 ========================================================================*/

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
