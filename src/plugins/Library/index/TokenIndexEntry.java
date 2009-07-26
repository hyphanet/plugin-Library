/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import freenet.keys.FreenetURI;

/**
** A {@link TokenEntry} that associates a subject term with another index.
**
** @author infinity0
*/
public class TokenIndexEntry extends TokenEntry {

	/**
	** Index target of this entry.
	*/
	protected FreenetURI index;

	/**
	** Empty constructor for the JavaBean convention.
	*/
	public TokenIndexEntry() { }

	public TokenIndexEntry(String s, FreenetURI i) {
		super(s);
		setIndex(i);
	}

	public FreenetURI getIndex() {
		return index;
	}

	// OPTIMISE maybe have some intern pool of FreenetURIs like we have for Token
	public void setIndex(FreenetURI i) {
		index = i;
	}

}
