/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

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
	protected FreenetURI uri;

	/**
	** Positions in the document where the term occurs, and an optional
	** fragment of text surrounding this.
	*/
	protected Map<Integer, String> pos;

	/**
	** Here for backwards-compatibility with {@link URIWrapper}
	*/
	protected String title;

	/**
	** Empty constructor for the JavaBean convention.
	*/
	public TermPageEntry() { }

	/**
	** Constructor for all the fields
	**
	** @param s Subject of the entry
	** @param u {@link FreenetURI} of the page
	** @param t Title or description of the page
	** @param p Map of positions (where the term appears) to context (fragment
	**          surrounding it).
	*/
	public TermPageEntry(String s, FreenetURI u, String t, Map<Integer, String> p) {
		super(s);
		setURI(u);
		title = t;
		pos = p;
	}

	public TermPageEntry(String s, FreenetURI u) {
		super(s);
		setURI(u);
	}

	public FreenetURI getURI() {
		return uri;
	}

	// TODO rm this in SnakeYAML representation
	public String getTitle() {
		return title;
	}

	public void setTitle(String s) {
	}

	public void setURI(FreenetURI u) {
		// OPTIMISE make the translator use the same URI object as from the URI table
		// actually, nah, not that important
		uri = u;
	}

	transient protected Map<Integer, String> pos_immutable;
	public Map<Integer, String> getPositions() {
		if (pos_immutable == null && pos != null) {
			pos_immutable = Collections.unmodifiableMap(pos);
		}
		return pos_immutable;
	}

	public void setPositions(Map<Integer, String> p) {
		pos = p;
	}

	/*========================================================================
	  abstract public class TermEntry
	 ========================================================================*/

	@Override public int entryType() {
		assert(getClass() == TermPageEntry.class);
		return TermEntry.TYPE_URI;
	}

	// we discount the "pos" field as there is no simple way to compare a map.
	// this case should never crop up anyway.
	@Override public int compareTo(TermEntry o) {
		int a = super.compareTo(o);
		if (a != 0) { return a; }
		return uri.toString().compareTo(((TermPageEntry)o).uri.toString());
	}

	@Override public boolean equals(Object o) {
		return super.equals(o) && uri.equals(((TermPageEntry)o).uri);
	}

	public boolean equalsIgnoreSubject(TermEntry entry) {
		return (entry instanceof TermPageEntry) && uri.equals(((TermPageEntry)entry).uri);
	}

	@Override public int hashCode() {
		return super.hashCode() ^ uri.hashCode();
	}

	/**
	 * {@inheritDoc}
	 *
	 * Takes title from entry if this has none
	 * Takes term positions from entry if this has none, adds both together if both have term positions
	 */
	@Override public TermEntry combine(TermEntry entry) {
		if(!equalsIgnoreSubject(entry))
			throw new IllegalArgumentException("Combine can only be performed on equal TermEntrys");

		TermPageEntry castEntry = (TermPageEntry) entry;
		// Merge positions
		Map newPos = null;
		if(pos == null && castEntry.pos != null)
			newPos = new HashMap(castEntry.pos);
		else if(pos != null){
			newPos = new HashMap(pos);
			if(castEntry.pos != null)
				newPos.putAll(castEntry.pos);
		}
		// Merge subj, uri, title
		TermPageEntry newTermEntry = new TermPageEntry(subj, uri, (title!=null)?title:castEntry.title, newPos);
		// Merge rel
		float newRel;
		if(rel == 0)	// combine relevances
			newRel = entry.rel;
		else
			newRel = rel * entry.rel;
		newTermEntry.rel = newRel;

		return newTermEntry;
	}

}
