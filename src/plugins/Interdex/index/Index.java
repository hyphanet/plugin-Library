/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Date;
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
	** Magic number to guide serialisation.
	*/
	final public static long MAGIC = 0xf82a9084681e5ba6L;

	/**
	** Name for this index.
	*/
	protected String name;

	/**
	** Last time this index was modified.
	*/
	protected Date modified;

	/**
	** Extra configuration options for the index.
	*/
	protected Map<String, Object> extra;

	/**
	** Whether this index is writeable.
	*/
	final public transient boolean writeable;

	/**
	** Filter table. Used to make checks quicker.
	*/
	protected PrefixTreeMap<Token, TokenFilter> filtab;

	/**
	** Token table. Provides information related to a Token-URI pairing.
	*/
	protected PrefixTreeMap<Token, SortedSet<TokenEntry>> tktab;

	/**
	** URI table. Provides information related to a URI.
	*/
	protected PrefixTreeMap<URIKey, URIEntry> utab;

	final private transient ReadWriteLock lock = new ReentrantReadWriteLock();

	public Index(String n) {
		name = (n == null)? "".intern(): n;
		writeable = true;
		modified = new Date();
		extra = new HashMap<String, Object>();

		utab = new PrefixTreeMap<URIKey, URIEntry>(new URIKey(), UTAB_MAX);
		tktab = new PrefixTreeMap<Token, SortedSet<TokenEntry>>(new Token(), TKTAB_MAX);
		filtab = new PrefixTreeMap<Token, TokenFilter>(new Token(), TKTAB_MAX);
	}

	public Index() {
		this(null);
	}

	/**
	** This constructor is used by the {@link IndexTranslator translator} to
	** create a skeleton index.
	*/
	protected Index(String n, Date m, Map<String, Object> x,
		PrefixTreeMap<Token, TokenFilter> f,
		PrefixTreeMap<Token, SortedSet<TokenEntry>> t,
		PrefixTreeMap<URIKey, URIEntry> u
		) {
		name = (n == null)? "".intern(): n;
		writeable = false;
		modified = m;
		extra = x;

		filtab = f;
		tktab = t;
		utab = u;
	}

	/**
	** Search the index for a given keyword.
	**
	** PRIORITY make this support getting a subset of the entries.
	*/
	public synchronized SortedSet<TokenEntry> searchIndex(String keyword) {
		return getAllEntries(new Token(keyword));
	}

	/**
	** Returns the SortedSet associated with a given Token.
	*/
	public synchronized SortedSet<TokenEntry> getAllEntries(Token t) {
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
