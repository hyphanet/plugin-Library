/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import java.util.HashSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import freenet.keys.FreenetURI;

import plugins.Interdex.util.PrefixTreeMap;

/**
** @author infinity0
*/
public class Index {

	final public static int TKTAB_MAX = 4096;
	final public static int UTAB_MAX = 65536;

	/**
	** Filter table. Used to make checks quicker.
	*/
	PrefixTreeMap<Token, TokenFilter> filtab = new
	PrefixTreeMap<Token, TokenFilter>(new Token(), TKTAB_MAX);

	/**
	** Token table. Provides information related to a Token-URI pairing.
	*/
	PrefixTreeMap<Token, HashSet<TokenEntry>> tktab = new
	PrefixTreeMap<Token, HashSet<TokenEntry>>(new Token(), TKTAB_MAX);

	/**
	** URI table. Provides information related to a URI.
	*/
	PrefixTreeMap<URIKey, URIEntry> utab = new
	PrefixTreeMap<URIKey, URIEntry>(new URIKey(), UTAB_MAX);

	final private ReadWriteLock lock = new ReentrantReadWriteLock();

	final String format;
	final String filterType;

	public Index(String fmt, String fil) {
		format = fmt;
		filterType = fil;
	}

	/**
	** Search the index for a given keyword.
	*/
	public synchronized HashSet<TokenEntry> searchIndex(String keyword) {
		return getAllEntries(new Token(keyword));
	}

	/**
	** Returns the HashSet associated with a given Token.
	*/
	public synchronized HashSet<TokenEntry> getAllEntries(Token t) {
		return tktab.get(t);
	}

	/**
	** Insert a TokenEntry into the index.
	**
	** @param en The entry to insert
	** @return The previous value for the entry
	*/
	public synchronized TokenEntry insertEntry(TokenEntry en) {
		// add n to the tktab
		// add n to the filtab
		// add n.uri to the utab
		throw new UnsupportedOperationException("Not implemented.");
	}

	/**
	** Remove a TokenEntry from the index.
	**
	** @param en The entry to remove
	** @return The entry that was removed
	*/
	public synchronized TokenEntry removeEntry(TokenEntry en) {
		// TODO
		throw new UnsupportedOperationException("Not implemented.");
	}

	/**
	** Get a URIEntry from the index.
	**
	** @param k The URIKey to retrieve the entry for
	** @return The value for the key
	*/
	public synchronized URIEntry getURI(URIKey k) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	/**
	** Put a URIEntry into the index.
	**
	** @param key The URIKey to store this entry under
	** @param value The entry to store
	** @return The previous value for the key
	*/
	public synchronized URIEntry putURI(URIKey key, URIEntry value) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	/**
	** Purge a URIKey from the index and all token mappings associated with it.
	*
	* @param key The URIKey to purge
	* @return A HashSet of all the purge token mappings
	*/
	public synchronized HashSet<TokenEntry> purgeURI(URIKey key) {
		// remove k from utab
		// remove everything linked to k from the tktab/filtab
		throw new UnsupportedOperationException("Not implemented.");
	}


}
