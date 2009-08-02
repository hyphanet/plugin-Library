/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import freenet.keys.FreenetURI;

import java.util.Collections;
import java.util.Map;

/**
** A {@link TokenEntry} that associates a subject term with a final target
** {@link FreenetURI} that satisfies the term.
**
** @author infinity0
*/
public class TokenURIEntry extends TokenEntry {

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
	** Empty constructor for the JavaBean convention.
	*/
	public TokenURIEntry() { }

	public TokenURIEntry(String s, FreenetURI u) {
		super(s);
		setURI(u);
	}

	public FreenetURI getURI() {
		return uri;
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
	  abstract public class TokenEntry
	 ========================================================================*/

	@Override public int entryType() {
		assert(getClass() == TokenURIEntry.class);
		return TokenEntry.TYPE_URI;
	}

	// we discount the "pos" field as there is no simple way to compare a map.
	// this case should never crop up anyway.
	@Override public int compareTo(TokenEntry o) {
		int a = super.compareTo(o);
		if (a != 0) { return a; }
		return uri.toString().compareTo(((TokenURIEntry)o).uri.toString());
	}

	@Override public boolean equals(Object o) {
		return super.equals(o) && uri.equals(((TokenURIEntry)o).uri);
	}

	@Override public int hashCode() {
		return super.hashCode() ^ uri.hashCode();
	}

}
