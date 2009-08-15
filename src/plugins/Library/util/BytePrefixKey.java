/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import plugins.Library.util.PrefixTree.PrefixKey;
import plugins.Library.util.PrefixTree.AbstractPrefixKey;

import java.util.Arrays;

/**
** A PrefixKey backed by an array of bytes.
**
** @author infinity0
*/
abstract public class BytePrefixKey<K extends BytePrefixKey<K>> extends AbstractPrefixKey<K> implements PrefixKey<K> {

	/**
	** Returns the string representation of a byte array.
	*/
	public static String bytesToHex(byte[] hash) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < hash.length; i++) {
			int h = (hash[i] >> 4) & 0x0F;
			int l = hash[i] & 0x0F;
			buf.append((char)((0<=h && h<=9)? '0'+h: 'a'+h-10));
			buf.append((char)((0<=l && l<=9)? '0'+l: 'a'+l-10));
		}
		return buf.toString();
	}

	/**
	** Returns the byte array representation of a hex string.
	*/
	public static byte[] hexToBytes(String hex) {
		byte[] bs = new byte[hex.length()>>1];
		for (int i=0, j=0; i<bs.length; ++i) {
			char h = hex.charAt(j++);
			char l = hex.charAt(j++);
			int hi = ('0'<=h && h<='9')? h-'0': ('a'<=h && h<='f')? h-'a'+10:
			         ('A'<=h && h<='F')? h-'A'+10: err(h);
			int li = ('0'<=l && l<='9')? l-'0': ('a'<=l && l<='f')? l-'a'+10:
			         ('A'<=l && l<='F')? l-'A'+10: err(l);
			bs[i] = (byte)((hi << 4) | li);
		}
		return bs;
	}
	private static byte err(char c) {
		throw new IllegalArgumentException("Invalid character in string: " + c);
	}

	/**
	** Main data of the token.
	*/
	final protected byte[] hash;

	/**
	** Cache for the hexstring representation.
	*/
	private transient String str_ = null;

	/**
	** Creates an empty key.
	*/
	protected BytePrefixKey(int s) {
		hash = new byte[s];
	}

	/**
	** Creates a key backed by the given array.
	*/
	protected BytePrefixKey(int s, byte[] h) {
		if (h.length != s) {
			throw new IllegalArgumentException("Byte array must be " + s + " bits long.");
		}
		hash = new byte[s];
		for (int i=0; i<h.length; ++i) { hash[i] = h[i]; }
	}

	public String toString() {
		if (str_ == null) {
			str_ = bytesToHex(hash);
		}
		return str_;
	}

	public String toByteString() {
		return new String(hash);
	}

	/*
	 * Bean getter and setter for this PrefixKey
	public String getString() {
		return toString();
	}

	public void setString(String s) {
		if (s.length != hash.length<<1) {
			throw new IllegalArgumentException("Incorrect length of string for this BytePrefixKey.");
		}
		System.arraycopy(hexToBytes(s), 0, hash, 0, hash.length);
	}
	*/

	/*========================================================================
	  public class PrefixTree.PrefixKey
	 ========================================================================*/

	@Override abstract public BytePrefixKey<K> clone();

	@Override public int symbols() {
		return 256;
	}

	@Override public int size() {
		return hash.length;
	}

	@Override public int get(int i) {
		return hash[i] & 0xFF;
	}

	@Override public void set(int i, int v) {
		hash[i] = (byte)v;
	}

	@Override public void clear(int i) {
		hash[i] = 0;
	}

	/*========================================================================
	  public class Object
	 ========================================================================*/

	public boolean equals(Object o) {
		if (o == this) { return true; }
		if (o instanceof BytePrefixKey) {
			return Arrays.equals(hash, ((BytePrefixKey)o).hash);
		}
		return false;
	}

	public int hashCode() {
		return Arrays.hashCode(hash);
	}

}
