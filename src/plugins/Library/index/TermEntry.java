/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

/**
** Represents data indexed by a {@link Token} and associated with a given
** subject {@link String} term.
**
** Subclasses '''must''' override the following methods to use information
** specific to that subclass:
**
** * {@link #entryType()}
** * {@link #compareTo(TermEntry)}
** * {@link #equals(Object)}
** * {@link #hashCode()}
**
** TODO make these immutable and make Yaml constructors/representers for them
**
** TODO better way to compare FreenetURIs than toString().compareTo() (this
** applies for TermIndexEntry, TokenPageEntry)
**
** @author infinity0
*/
abstract public class TermEntry implements Comparable<TermEntry> {

	// TODO maybe turn this into an enum at some point
	final public static int TYPE_URI = 0x00;
	final public static int TYPE_INDEX = 0xF1;
	final public static int TYPE_TERM = 0xF2;

	/**
	** Subject term of this entry.
	*/
	protected String subj;

	/**
	** Relevance rating. Must be in the half-closed interval (0,1].
	** a relevance of 0 indicates that the relevance is unset
	*/
	protected float rel;

	/**
	** Empty constructor for the JavaBean convention.
	*/
	public TermEntry() { }

	public TermEntry(String s) {
		setSubject(s);
	}

	public String getSubject() {
		return subj;
	}

	public void setSubject(String s) {
		if (s == null) {
			throw new IllegalArgumentException("can't have a null subject!");
		}
		subj = s.intern();
	}

	public float getRelevance() {
		return rel;
	}

	public void setRelevance(float r) {
		if (r <= 0 || r > 1) {
			throw new IllegalArgumentException("Relevance must be in the half-closed interval (0,1]. Supplied : "+r);
		}
		rel = r;
	}

	/**
	** Returns the type of TermEntry. This '''must''' be constant for
	** instances of the same class, and different between classes.
	*/
	abstract protected int entryType();

	/**
	** {@inheritDoc}
	**
	** Compares two entries, based on how useful they might be to the end user.
	**
	** This implementation sorts by order of descending relevance, then by
	** order of ascending {@link #entryType()}.
	**
	** Subclasses '''must overridde this method'' to also use information
	** specific to the subclass.
	**
	** @throws IllegalArgumentException if the entries have different subjects
	*/
	public int compareTo(TermEntry o) {
		if (!subj.equals(o.subj)) {
			throw new IllegalArgumentException("Entries for different subjects cannot be compared.");
		}
		if (this == o) { return 0; }
		if (rel != o.rel) { return (rel > o.rel)? -1: 1; }

		int a = entryType(), b = o.entryType();
		if (a != b) { return (a < b)? -1: 1; }
		return 0;
	}

	/**
	** {@inheritDoc}
	**
	** This implementation tests whether the run-time classes of the argument
	** is identical to the run-time class of this object. If they are, then
	** it tests the relevance and subject fields.
	**
	** Subclasses '''must overridde this method'' to also use information
	** specific to the subclass.
	*/
	@Override public boolean equals(Object o) {
		if (getClass() != o.getClass()) { return false; }
		TermEntry en = (TermEntry)o;
		return rel == en.rel && subj.equals(en.subj);
	}

	/**
	** Returns whether the parts of this TermEntry other than the subject are equal
	**
	** Subclasses '''must overridde this method'' to use information
	** specific to the subclass.
	*/
	public abstract boolean equalsTarget(TermEntry entry);

	/**
	** {@inheritDoc}
	**
	** This implementation XORs the hashcode of {@link #subj} and {@link #rel}.
	**
	** Subclasses '''must overridde this method'' to also use information
	** specific to the subclass.
	*/
	@Override public int hashCode() {
		return subj.hashCode() ^ Float.floatToIntBits(rel);
	}

	@Override public String toString() {
		return subj + ":" + rel;
	}


	/*
	** Calculates an accumulated score to sort the entry by, using the formula
	** relevance^3 * quality; in other words, relevance is (for sorting
	** results) 3 times as important as quality. For example, for (rel, qual)
	** we have (0.85, 0.95) < (1.0, 0.6) < (0.85, 1.00)
	* /
	public float score() {
		if (score_ == null) {
			score_ = new Float(rel * rel* rel * qual);
		}
		return score_;
	}*/

}
