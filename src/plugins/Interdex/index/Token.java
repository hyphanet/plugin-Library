/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import plugins.Interdex.util.BytePrefixKey;

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
public class Token extends BytePrefixKey<Token> {

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

	public Token() {
		super(16);
	}

	public Token(byte[] h) {
		super(16, h);
	}

	public Token(String w) {
		super(16, MD5(w));
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
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch (java.io.UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	/*========================================================================
	  public interface BytePrefixKey
	 ========================================================================*/

	@Override public Token clone() {
		return new Token(hash);
	}

}
