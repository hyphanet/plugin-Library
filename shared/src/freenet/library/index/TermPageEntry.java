/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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
	final public String page;

	/** 
	 * Positions where the term occurs. May be null if we don't have that data.
	 * 
	 * Retrieved from the YamlReaderWriter so must be public and 
	 * have the Set<Integer> type to be able to read the old indexes.
	 */
	final public Set<Integer> positions;
	
	/**
	** Map from positions in the text to a fragment of text around where it occurs.
	** Only non-null if we have the fragments of text (we may have positions but not details), 
	** to save memory.
	*/
	final public Map<Integer, String> posFragments;

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
	public TermPageEntry(String s, float r, String u, Map<Integer, String> p) {
		this(s, r, u, (String)null, p);
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
	public TermPageEntry(String s, float r, String u, String t, Map<Integer, String> p) {
		super(s, r);
		if (u == null) {
			throw new IllegalArgumentException("can't have a null page");
		}
		page = u;
		title = t == null ? null : t.intern();
		if(p == null) {
			posFragments = null;
			positions = null;
		} else {
			posFragments = Collections.unmodifiableMap(p);
			positions = new TreeSet<Integer>(p.keySet());
		}
	}

	/**
	** For serialisation.
	*/
	public TermPageEntry(String s, float r, String u, String t, Set<Integer> pos, Map<Integer, String> frags) {
		super(s, r);
		if (u == null) {
			throw new IllegalArgumentException("can't have a null page");
		}
		page = u;
		title = t;
		if(pos != null) {
			positions = new TreeSet<Integer>(pos);
		} else
			positions = null;
		if(frags != null) {
			boolean allNulls = true;
			for(String i : frags.values()) {
				if(i != null && !i.equals("")) {
					allNulls = false;
					break;
				}
			}
			if(allNulls)
				posFragments = null;
			else
				posFragments = frags;
		} else
			posFragments = null;
	}
	
	public TermPageEntry(TermPageEntry t, float newRel) {
		super(t, newRel);
		this.page = t.page;
		this.posFragments = t.posFragments;
		this.positions = t.positions;
		this.title = t.title;
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
		// OPT NORM make a more efficient way of comparing these
		return page.toString().compareTo(((TermPageEntry)o).page.toString());
	}

	@Override public boolean equals(Object o) {
		return o == this || super.equals(o) && page.equals(((TermPageEntry)o).page);
	}

	@Override public boolean equalsTarget(TermEntry entry) {
		return entry == this || (entry instanceof TermPageEntry) && page.equals(((TermPageEntry)entry).page);
	}

	@Override public int hashCode() {
		return super.hashCode() ^ page.hashCode();
	}

	/** Do we have term positions? Just because we do doesn't necessarily mean we have fragments. */
	public boolean hasPositions() {
		return positions != null;
	}

	/** Get the positions to fragments map. If we don't have fragments, create this from the positions list. */
	public Map<Integer, String> positionsMap() {
		if(positions == null) return null;
		if(posFragments != null) return posFragments;
		HashMap<Integer, String> ret = new HashMap<Integer, String>(positions.size());
		for(int x : positions)
			ret.put(x, null);
		return ret;
	}

	public boolean hasPosition(int i) {
		return positions.contains(i);
	}

	public ArrayList<Integer> positions() {
		Integer[] array = positions.toArray(new Integer[positions.size()]);
		Arrays.sort(array);
		ArrayList<Integer> ret = new ArrayList<Integer>(positions.size());
		for(int i=0;i<array.length;i++) {
			ret.add(array[i]);
		}
		return ret;
	}

	public int[] positionsRaw() {
		Integer[] r = new Integer[positions.size()];
		r = positions.toArray(r);
		int[] ret = new int[positions.size()];
		for(int i = 0; i < ret.length; i++)
			ret[i] = r[i];
		Arrays.sort(ret);
		return ret;
	}

	public int positionsSize() {
		if(positions == null) return 0;
		return positions.size();
	}

	public boolean hasFragments() {
		return posFragments != null;
	}

}
