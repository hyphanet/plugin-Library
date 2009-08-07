/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

/**
** A {@link TermEntry} that associates a subject term with a related term.
**
** @author infinity0
*/
public class TermTermEntry extends TermEntry {

	/**
	** Related term target for this entry.
	*/
	protected String term;

	/**
	** Empty constructor for the JavaBean convention.
	*/
	public TermTermEntry() { }

	public TermTermEntry(String s, String t) {
		super(s);
		setTerm(t);
	}

	/**
	** Allow an entry with relevance=1 only in the constructor. This is used
	** by Interdex's recursive search algorithm.
	*/
	public TermTermEntry(boolean d) {
		if (d) { rel = 1; }
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String t) {
		term = (t == null)? null: t.intern();
	}

	@Override public void setRelevance(float r) {
		if (r < 0 || r >= 1) {
			throw new IllegalArgumentException("Relevance must be in the half-open interval [0,1).");
		}
		super.setRelevance(r);
	}

	/*========================================================================
	  abstract public class TermEntry
	 ========================================================================*/

	@Override public int entryType() {
		assert(getClass() == TermTermEntry.class);
		return TermEntry.TYPE_TERM;
	}

	@Override public int compareTo(TermEntry o) {
		int a = super.compareTo(o);
		if (a != 0) { return a; }
		return term.compareTo(((TermTermEntry)o).term);
	}

	@Override public boolean equals(Object o) {
		return super.equals(o) && term.equals(((TermTermEntry)o).term);
	}

	public boolean equalsIgnoreSubject(TermEntry entry) {
		return (entry instanceof TermPageEntry) && term.equals(((TermTermEntry)entry).term);
	}

	@Override public int hashCode() {
		return super.hashCode() ^ term.hashCode();
	}

	@Override
	public TermEntry combine(TermEntry entry) {
		if(!equalsIgnoreSubject(entry))
			throw new IllegalArgumentException("Combine can only be performed on equal TermEntrys");

		TermTermEntry castEntry = (TermTermEntry) entry;
		// Merge subj, term
		TermTermEntry newTermEntry = new TermTermEntry(subj, term);
		// Merge rel
		float newRel;
		if(rel == 0)	// combine relevances
			newRel = entry.rel;
		else
			newRel = rel * entry.rel;
		newTermEntry.setRelevance(newRel);

		return newTermEntry;
	}
}
