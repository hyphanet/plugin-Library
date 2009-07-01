/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

/**
** @author infinity0
*/
public abstract class TokenEntry implements Comparable<TokenEntry> {

	/**
	** Relevance rating. Must be in the closed interval [0,1].
	*/
	protected float rel;

	/**
	** Quality rating. Must be in the closed interval [0,1].
	*/
	protected float qual;

	private transient Float score_;

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

	public float score() {
		if (score_ == null) {
			score_ = new Float(rel * rel* rel * qual);
		}
		return score_;
	}

	// TODO make reverse order?
	public int compareTo(TokenEntry o) {
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
