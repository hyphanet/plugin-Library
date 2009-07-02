/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

/**
** DOCUMENT
**
** TODO maybe have a "writeable" field to enforce immutability when inserted
** into a collection.
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
	** Quality rating. Must be in the closed interval [0,1].
	*/
	protected float qual;

	/**
	** Cache for the score.
	*/
	private transient Float score_;

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
		subject = s.intern();
	}

	public float getRelevance() {
		return rel;
	}

	public void setRelevance(float r) {
		if (r < 0 || r > 1) {
			throw new IllegalArgumentException("Relevance must be in the closed interval [0,1].");
		}
		if (rel != r) { score_ = null; }
		rel = r;
	}

	public float getQuality() {
		return qual;
	}

	public void setQuality(float q) {
		if (q < 0 || q > 1) {
			throw new IllegalArgumentException("Relevance must be in the closed interval [0,1].");
		}
		if (qual != q) { score_ = null; }
		qual = q;
	}

	/**
	** Calculates an accumulated score to sort the entry by, using the formula
	** relevance^3 * quality; in other words, relevance is (for sorting
	** results) 3 times as important as quality. For example, for (rel, qual)
	** we have (0.85, 0.95) < (1.0, 0.6) < (0.85, 1.00)
	*/
	public float score() {
		if (score_ == null) {
			score_ = new Float(rel * rel* rel * qual);
		}
		return score_;
	}

	/**
	** Compares two entries by their score. If the scores are the same, returns
	** a random (but consistent, ie. obeying the contract of {@link Comparable#compareTo(Object)}) value, being 0 if and only(ish) if the entries
	** are equal.
	*/
	@Override public int compareTo(TokenEntry o) {
		// TODO make reverse order?
		if (this == o) { return 0; }
		float d = score() - o.score();
		// this is a bit of a hack but is needed since Tree* treats two objects
		// as "equal" if their "compare" returns 0
		if (d != 0) { return (int)(d * Integer.MAX_VALUE); }
		d = rel - o.rel;
		if (d != 0) { return (int)(d * Integer.MAX_VALUE); }
		int h = hashCode() - o.hashCode();
		// on the off chance that the hashCodes are equal but the objects are not,
		// test the string representations of them...
		return (h != 0)? h: (equals(o))? 0: toString().compareTo(o.toString());
	}

}
