/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.index.TermEntry.EntryType;

import freenet.keys.FreenetURI;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
** A {@link TermEntry} that associates a subject term with a final target
** {@link FreenetURI} that satisfies the term.
**
** @author infinity0
*/
public class TermPageEntry extends TermEntry {

	/**
	** URI of the target
	*/
	final public FreenetURI page;

	/**
	** Positions in the document where the term occurs, and an optional
	** fragment of text surrounding this.
	*/
	final public Map<Integer, String> pos;

	/**
	** Here for backwards-compatibility with the old URIWrapper class.
	*/
	final public String title;

	/**
	** Standard constructor.
	**
	** @param s Subject of the entry
	** @param r Relevance of the entry
	** @param u {@link FreenetURI} of the page
	** @param p Map of positions (where the term appears) to context (fragment
	**          surrounding it).
	*/
	public TermPageEntry(String s, float r, FreenetURI u, Map<Integer, String> p) {
		this(s, r, u, null, p);
	}

	/**
	** Extended constructor with additional {@code title} field for old-style
	** indexes.
	**
	** @param s Subject of the entry
	** @param r Relevance of the entry
	** @param u {@link FreenetURI} of the page
	** @param t Title or description of the page
	** @param p Map of positions (where the term appears) to context (fragment
	**          surrounding it).
	*/
	public TermPageEntry(String s, float r, FreenetURI u, String t, Map<Integer, String> p) {
		super(s, r);
		if (u == null) {
			throw new IllegalArgumentException("can't have a null page");
		}
		page = u; // OPTIMISE make the translator use the same URI object as from the URI table?
		title = t;
		pos = (p == null)? Collections.<Integer, String>emptyMap(): Collections.unmodifiableMap(p); // TODO could be more efficient...
	}

	/*========================================================================
	  abstract public class TermEntry
	 ========================================================================*/

	@Override public EntryType entryType() {
		assert(getClass() == TermPageEntry.class);
		return EntryType.PAGE;
	}

	// we discount the "pos" field as there is no simple way to compare a map.
	// this case should never crop up anyway.
	@Override public int compareTo(TermEntry o) {
		int a = super.compareTo(o);
		if (a != 0) { return a; }
		// OPTIMISE find a more efficient way than this
		return page.toString().compareTo(((TermPageEntry)o).page.toString());
	}

	@Override public boolean equals(Object o) {
		return super.equals(o) && page.equals(((TermPageEntry)o).page);
	}

	@Override public boolean equalsTarget(TermEntry entry) {
		return (entry instanceof TermPageEntry) && page.equals(((TermPageEntry)entry).page);
	}

	@Override public int hashCode() {
		return super.hashCode() ^ page.hashCode();
	}

}
