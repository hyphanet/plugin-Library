/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

/**
** A {@link TokenEntry} that associates a subject term with a related term.
**
** @author infinity0
*/
public class TokenTermEntry extends TokenEntry {

	/**
	** Related term target for this entry.
	*/
	protected String term;

	/**
	** Empty constructor for the JavaBean convention.
	*/
	public TokenTermEntry() { }

	public TokenTermEntry(String s, String t) {
		super(s);
		setTerm(t);
	}

	/**
	** Returns the canonical {@link Token} form of the term.
	**
	** @see Token#intern(String)
	*/
	public Token token() {
		return Token.intern(term);
	}

	/**
	** Allow an entry with relevance=1 only in the constructor. This is used
	** by Interdex's recursive search algorithm.
	*/
	public TokenTermEntry(boolean d) {
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

}
