/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import plugins.Interdex.util.IdentityComparator;

/**
** Represents data indexed by a {@link Token} and associated with a given
** subject {@link String} term.
**
** TODO maybe have a "writeable" field to enforce immutability when inserted
** into a collection.
**
** URGENT code equals() for this and subclasses
**
** @author infinity0
*/
public abstract class TokenEntry implements Comparable<TokenEntry> {

	/**
	** Actual (not hashed) subject term of this entry.
	*/
	protected String subject;

	/**
	** Relevance rating. Must be in the closed interval [0,1].
	*/
	protected float rel;

	/**
	** Empty constructor for the JavaBean convention.
	*/
	public TokenEntry() { }

	public TokenEntry(String s) {
		setSubject(s);
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String s) {
		subject = (s == null)? null: s.intern();
	}

	public float getRelevance() {
		return rel;
	}

	public void setRelevance(float r) {
		if (r < 0 || r > 1) {
			throw new IllegalArgumentException("Relevance must be in the closed interval [0,1].");
		}
		rel = r;
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

	/**
	** Compares two entries by their relevance. If these are the same, returns
	** a random (but consistent, ie. obeying the contract of {@link
	** Comparable#compareTo(Object)}) value, being 0 if and only(ish) if the
	** entries are equal.
	*/
	@Override public int compareTo(TokenEntry o) {
		if (this == o) { return 0; }
		float f = o.rel - rel;
		if (f != 0) { return (f > 0)? 1: -1; }
		// PRIORITY hmmm, no, this won't do, we need a equals() based comparator
		return IdentityComparator.comparator.compare(this, o);
	}

}
