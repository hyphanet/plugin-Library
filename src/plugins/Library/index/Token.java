/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.util.BytePrefixKey;

import java.security.MessageDigest;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
** A {@link BytePrefixKey} backed by the MD5 hash of a {@link String} term.
**
** @author infinity0
*/
public class Token extends BytePrefixKey<Token> {

	/**
	** Internal map of string -> token. Note that there is no need to hold the
	** token by a WeakReference here as the presence of the mapping implies
	** that we want to keep access to it.
	*/
	final private static Map<String, Token> internMap = new WeakHashMap<String, Token>();

	/**
	** Internal pool of tokens. Ideally we would have a WeakHashSet but the
	** {@link Set} interface does not provide a method to retrieve the actual
	** object contained in the Set, so we need to use a {@link Map} of keys
	** back to the keys. Also, this means that we need to wrap the value in a
	** {@link WeakReference}.
	*/
	final private static Map<Token, WeakReference<Token>> internPool = new WeakHashMap<Token, WeakReference<Token>>();

	/**
	** Returns the canonical {@link Token} for a given string.
	**
	** For even better performance, make sure the string parameter is from the
	** {@link String} internal pool.
	**
	** @see String#intern()
	*/
	public static synchronized Token intern(String s) {
		if (s == null) { return null; }
		Token t = internMap.get(s);
		if (t == null) {
			t = Token.intern(new Token(s));
			internMap.put(s, t);
		}
		return t;
	}

	/**
	** Returns the canonical representation of a {@link Token}.
	**
	** @see String#intern()
	*/
	public static synchronized Token intern(Token t) {
		if (t == null) { return null; }
		WeakReference<Token> ref = internPool.get(t);
		Token tk;
		if (ref == null || (tk = ref.get()) == null) {
			// the referent could still be null, because the GC could have cleared the
			// weak refs (including the key) between lines 1 and 2 of this method.
			internPool.put(t, new WeakReference<Token>(t));
			return t;
		}
		return tk;
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

	/**
	** Returns the canonical representation of a {@link Token}.
	**
	** This implementation just calls {@link #intern(Token)}.
	**
	** @see String#intern()
	*/
	public Token intern() {
		return intern(this);
	}

	/*========================================================================
	  public interface BytePrefixKey
	 ========================================================================*/

	@Override public Token clone() {
		return new Token(hash);
	}

}
