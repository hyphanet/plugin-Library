/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import freenet.keys.FreenetURI;

import plugins.Interdex.util.SkeletonPrefixTreeMap;
import plugins.Interdex.util.SkeletonMap;
import plugins.Interdex.util.DataNotLoadedException;

/**
** Represents the data for an index.
**
** PRIORITY make this into an interface and put the actual implementation in
** ProtoIndex.java
**
** PRIORITY work out locking
**
** TODO parallelise the fetches
**
** @deprecated
** @author infinity0
*/
public class PrefixIndex  {

	final public static int TKTAB_MAX = 4096;
	final public static int UTAB_MAX = 4096;

	/**
	** Magic number to guide serialisation.
	*/
	final public static long MAGIC = 0xf82a9084681e5ba6L;

	/**
	** Freenet ID for this index
	*/
	protected FreenetURI id;

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
	final protected Map<String, Object> extra;

	/**
	** Whether this index is writeable.
	*/
	final public transient boolean writeable;

	/* *
	** Filter table. Used to make checks quicker.
	** TODO implement
	*/
	//protected SkeletonMap<Token, TokenFilter> filtab;

	/**
	** Token table. Provides information related to a Token-URI pairing.
	*/
	protected SkeletonMap<Token, SortedSet<TokenEntry>> tktab;

	/**
	** URI table. Provides information related to a URI.
	*/
	protected SkeletonMap<URIKey, SortedMap<FreenetURI, URIEntry>> utab;

	/**
	** Read-write lock. PRIORITY: actually use this.
	*/
	final private transient ReadWriteLock lock = new ReentrantReadWriteLock();

	public PrefixIndex(FreenetURI i, String n) {
		id = i;
		name = (n == null)? "": n;
		writeable = true;
		modified = new Date();
		extra = new HashMap<String, Object>();

		utab = new SkeletonPrefixTreeMap<URIKey, SortedMap<FreenetURI, URIEntry>>(new URIKey(), UTAB_MAX);
		tktab = new SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>(new Token(), TKTAB_MAX);
		//filtab = new SkeletonPrefixTreeMap<Token, TokenFilter>(new Token(), TKTAB_MAX);
	}

	/**
	** This constructor is used by the {@link IndexFileSerialiser.IndexTranslator
	** translator} to create a skeleton index.
	*/
	protected PrefixIndex(FreenetURI i, String n, Date m, Map<String, Object> x,
		SkeletonMap<URIKey, SortedMap<FreenetURI, URIEntry>> u,
		SkeletonMap<Token, SortedSet<TokenEntry>> t/*,
		SkeletonMap<Token, TokenFilter> f*/
		) {
		id = i;
		name = (n == null)? "": n;
		writeable = false;
		modified = m;
		extra = x;

		//filtab = f;
		tktab = t;
		utab = u;
	}

	public long getMagic() {
		return MAGIC;
	}

	/**
	** DOCUMENT
	**
	** TODO make Index extend a Skeleton class or something.
	*/
	public boolean isLive() {
		return utab.isLive() && tktab.isLive()/* && filtab.isLive()*/;
	}

	public boolean isBare() {
		return utab.isBare() && tktab.isBare()/* && filtab.isBare()*/;
	}

	public void inflate() {
		//filtab.inflate();
		tktab.inflate();
		utab.inflate();
	}

	public void deflate() {
		utab.deflate();
		tktab.deflate();
		//filtab.deflate();
	}

	/*
	** Search the index for a given keyword.
	**
	** PRIORITY make this support getting a subset of the entries.
	* /
	public synchronized SortedSet<TokenEntry> searchIndex(String keyword, int numberOfResults);
	*/

	/**
	** Fetch the TokenEntries associated with a given term.
	**
	** @param term The term to fetch the entries for
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The fetched entries
	** @throws DataNotLoadedException
	**         if the TokenEntries have not been loaded
	*/
	public synchronized SortedSet<TokenEntry> fetchTokenEntries(String term, boolean auto) {
		// TODO make this use the bloom filter
		if (auto) {
			// PRIORITY implement this using GhostTreeSet instead of TreeSet so we retrieve
			// a SkeletonSet rather than the whole map, and then run the autoload
			// algorithm on that again; see todo.txt for more details
			SortedSet<TokenEntry> entries;
			for (;;) {
				try {
					entries = tktab.get(new Token(term));
					break;
				} catch (DataNotLoadedException e) {
					e.getParent().deflate((Token)e.getKey());
				}
			}
			return entries;
		} else {
			return tktab.get(new Token(term));
		}
	}

	/**
	** Clear all TokenEntries associated with a given term, and remove traces
	** of this term from any associated URIEntries.
	**
	** @param term The term to clear the entries for
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The cleared entries
	** @throws DataNotLoadedException
	**         if the TokenEntries or URIEntries have not been loaded
	*/
	public synchronized SortedSet<TokenEntry> clearTokenEntries(String term, boolean auto) {
		if (!writeable) { throw new IllegalStateException("Index is not writeable: " + id); }
		// make sure the entry is loaded
		SortedSet<TokenEntry> entries = fetchTokenEntries(term, auto);

		// for all TokenURIEntries in the set, remove the term from the
		// corresponding URIEntry's "terms" field. we want this to be atomic,
		// so grab all the entries first to make sure they are loaded.
		Set<URIEntry> uris = new HashSet<URIEntry>();
		for (TokenEntry en: entries) {
			if (!(en instanceof TokenURIEntry)) { continue; }
			URIEntry u = fetchURIEntry(((TokenURIEntry)en).getURI(), auto);
			if (u == null) { continue; /* TODO or throw index corrupt exception? */ }
			uris.add(u);
		}

		for (URIEntry u: uris) {
			u.getTerms().remove(term);
			// we do NOT automatically remove URIEntries that have no associated terms
			// because they could be added again. also, in terms of the structure of
			// the index, such a state is not inconsistent, and happens elsewhere, eg.
			// directly after a call to insertURIEntry
		}

		// remove the entries from the tktab
		tktab.remove(new Token(term));
		return entries;
	}

	/**
	** Insert a TokenEntry into the index, and (TODO) update the filter. If
	** appropriate, insert this term into the associated URIEntry.
	**
	** @param entry The entry to insert
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The previous entry TODO just returns null for now
	** @throws DataNotLoadedException
	**         if the term's TokenEntries have not been loaded,
	**         or if the filter or URIEntry has not been loaded
	*/
	public synchronized TokenEntry insertTokenEntry(TokenEntry entry, boolean auto) {
		if (!writeable) { throw new IllegalStateException("Index is not writeable: " + id); }
		// make sure the entry is loaded
		// TODO: when we implement GhostTreeSet, make sure that enough of the
		// set is loaded to complete the add operation below
		String term = entry.getSubject();
		SortedSet<TokenEntry> entries = fetchTokenEntries(term, auto);

		if (entry instanceof TokenURIEntry) {
			// if it's a TokenURIEntry, its URIEntry must already be in the table
			URIEntry u = fetchURIEntry(((TokenURIEntry)entry).getURI(), auto);
			if (u == null) { throw new IllegalArgumentException("Can only add a TokenURIEntry for a FreenetURI already present in the URI table."); }
			// in which case insert its subject to the URIEntry's "terms" field
			u.getTerms().add(entry.getSubject());
		}

		// if the term is not indexed yet, prepare it for indexing
		if (entries == null) {
			entries = new TreeSet<TokenEntry>();
			tktab.put(Token.intern(term), entries);
		}

		// add entry into the tktab
		entries.add(entry);

		// URGENT: at present, since the set of entries is sorted by relevance
		// to make fetches of relevant stuff quicker, there is no way of
		// preventing entries being added for the same URI, other than by
		// scanning through the entire set. this is unacceptable for a simple
		// "add" command, so i've decided to leave out this functionality for
		// now and just allow entries with the same URI to be added.

		// TODO insert entry into the filtab
		return null;
	}

	/**
	** Remove a TokenEntry from the index, and (TODO) updated the filter. If
	** appropriate, remove this term from the associated URIEntry.
	**
	** @param entry The entry to remove
	** @param auto Whether to catch and handle {@link DataNotLoadedException}.
	** @return The removed entry, or null if no entry was removed
	** @throws DataNotLoadedException
	**         if the term's TokenEntries have not been loaded,
	**         or if the filter or URIEntry has not been loaded
	*/
	public synchronized TokenEntry removeTokenEntry(TokenEntry entry, boolean auto) {
		if (!writeable) { throw new IllegalStateException("Index is not writeable: " + id); }
		// make sure the entry is loaded
		// TODO: when we implement GhostTreeSet, make sure that enough of the
		// set is loaded to complete the rem operation below
		String term = entry.getSubject();
		SortedSet<TokenEntry> entries = fetchTokenEntries(term, auto);

		if (entries.contains(entry)) { return null; }

		if (entry instanceof TokenURIEntry) {
			// if it's a TokenURIEntry, its URIEntry must already be in the table
			URIEntry u = fetchURIEntry(((TokenURIEntry)entry).getURI(), auto);
			if (u == null) { throw new IllegalArgumentException("Can only add a TokenURIEntry for a FreenetURI already present in the URI table."); }
			// in which case remove its subject from the URIEntry's "terms" field
			u.getTerms().remove(entry.getSubject());
		}

		// rem entry from the tktab
		entries.remove(entry);

		// if there are no entries left, remove the whole set from the map
		if (entries.isEmpty()) {
			tktab.remove(term);
		}

		// TODO remove entry from the filtab

		// TODO ideally this should return the same object the set holds, but
		// java's Set interface doesn't let you do this
		return entry;
	}

	/**
	** Fetch a URIEntry from the index by its FreenetURI.
	**
	** @param uri The URI to fetch the entry for
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The fetched entry
	** @throws DataNotLoadedException
	**         if the URIEntry has not been loaded
	*/
	public synchronized URIEntry fetchURIEntry(FreenetURI uri, boolean auto) {
		if (auto) {
			// PRIORITY implement this using GhostTreeMap instead of TreeMap so we retrieve
			// a SkeletonMap rather than the whole map, and then run the autoload
			// algorithm on that again; see todo.txt for more details
			SortedMap<FreenetURI, URIEntry> entries;
			for (;;) {
				try {
					entries = utab.get(new URIKey(uri));
					break;
				} catch (DataNotLoadedException e) {
					e.getParent().deflate((URIKey)e.getKey());
				}
			}
			return entries.get(uri);
		} else {
			return utab.get(new URIKey(uri)).get(uri);
		}
	}

	/**
	** Clear all the terms associated with the URIEntry for a given FreenetURI,
	** and for each term, remove all TokenURIEntries pointing to the URIEntry.
	** Note: does NOT remove the URIEntry from the index.
	**
	** @param uri The URI to clear the entry for
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The cleared entry
	** @throws DataNotLoadedException
	**         if the URIEntry has not been loaded,
	**         or if the TokenEntries for any term has not been loaded
	*/
	public synchronized URIEntry clearURIEntry(FreenetURI uri, boolean auto) {
		if (!writeable) { throw new IllegalStateException("Index is not writeable: " + id); }
		URIEntry entry = fetchURIEntry(uri, auto);

		// go through the "terms" and remove the TokenEntries associated with
		// them from the index. this is expensive but it is hoped that this
		// operation won't be called so often.
		Set<String> terms = entry.getTerms();
		Set<SortedSet<TokenEntry>> tks = new HashSet<SortedSet<TokenEntry>>();

		for (String term: terms) {
			// TODO when we use GhostTreeSet, we shall have to call inflate() here
			// to make sure that all the entries are loaded
			SortedSet<TokenEntry> entries = fetchTokenEntries(term, auto);
			if (entries == null) { continue; /* or throw index corrupt? */ }
			tks.add(entries);
		}

		for (SortedSet<TokenEntry> entries: tks) {
			Iterator<TokenEntry> it = entries.iterator();
			while (it.hasNext()) {
				TokenEntry en = it.next();
				if (!(en instanceof TokenURIEntry)) { continue; }
				TokenURIEntry uen = (TokenURIEntry)en;
				if (!uen.getURI().equals(uri)) { continue; }
				it.remove();
			}
		}

		return entry;
	}

	/**
	** Insert a URIEntry into the index. If there is already a URIEntry for its
	** FreenetURI in the index, the associated terms is replaced with the set
	** from the old entry; otherwise, the set is cleared.
	**
	** @param entry The entry to insert
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The previous entry, or null if no entry was overwritten
	** @throws DataNotLoadedException
	**         if the URIEntry has not been loaded
	*/
	public synchronized URIEntry insertURIEntry(URIEntry entry, boolean auto) {
		if (!writeable) { throw new IllegalStateException("Index is not writeable: " + id); }
		SortedMap<FreenetURI, URIEntry> entries;
		FreenetURI uri = entry.getSubject();

		if (auto) {
			for (;;) {
				try {
					entries = utab.get(new URIKey(uri));
					break;
				} catch (DataNotLoadedException e) {
					e.getParent().deflate((URIKey)e.getKey());
				}
			}
		} else {
			entries = utab.get(new URIKey(uri));
		}

		// if the map doesn't exist, create it
		if (entries == null) {
			entries = new TreeMap<FreenetURI, URIEntry>();
			utab.put(new URIKey(uri), entries);
		}

		// PRIORITY implement this using GhostTreeMap
		// copy the "terms" from the old entry to maintain index consistency
		URIEntry oldentry = entries.put(uri, entry);
		if (oldentry == null) {
			entry.getTerms().clear();
		} else {
			entry.setTerms(oldentry.getTerms());
		}
		return oldentry;
	}

	/**
	** Remove a URIEntry from the index. The associated terms must be empty,
	** and it must match the URIEntry already in the index. (To clear the
	** associated terms and return the entry, use {@link
	** #clearURIEntry(FreenetURI, boolean)}.)
	**
	** @param entry The entry to remove
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The removed entry, or null if no entry was removed
	** @throws DataNotLoadedException
	**         if the URIEntry has not been loaded,
	**         or if the TokenEntries for any term has not been loaded
	*/
	public synchronized URIEntry removeURIEntry(URIEntry entry, boolean auto) {
		if (!writeable) { throw new IllegalStateException("Index is not writeable: " + id); }

		if (!entry.getTerms().isEmpty()) { return null; }

		SortedMap<FreenetURI, URIEntry> entries;
		FreenetURI uri = entry.getSubject();

		if (auto) {
			for (;;) {
				try {
					entries = utab.get(new URIKey(uri));
					break;
				} catch (DataNotLoadedException e) {
					e.getParent().deflate((URIKey)e.getKey());
				}
			}
		} else {
			entries = utab.get(new URIKey(uri));
		}

		// PRIORITY implement this using GhostTreeMap
		if (entries == null || !entries.get(uri).equals(entry)) { return null; }
		return entries.remove(uri);
	}

}
