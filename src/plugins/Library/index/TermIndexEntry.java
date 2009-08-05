/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import freenet.keys.FreenetURI;

/**
** A {@link TermEntry} that associates a subject term with another index.
**
** @author infinity0
*/
public class TermIndexEntry extends TermEntry {

	/**
	** Index target of this entry.
	*/
	protected FreenetURI index;

	/**
	** Empty constructor for the JavaBean convention.
	*/
	public TermIndexEntry() { }

	public TermIndexEntry(String s, FreenetURI i) {
		super(s);
		setIndex(i);
	}

	public FreenetURI getIndex() {
		return index;
	}

	// OPTIMISE have some intern pool of FreenetURIs like we have for Token.
	// Or just use a string instead?
	public void setIndex(FreenetURI i) {
		index = i;
	}

	/*========================================================================
	  abstract public class TermEntry
	 ========================================================================*/

	@Override public int entryType() {
		assert(getClass() == TermIndexEntry.class);
		return TermEntry.TYPE_INDEX;
	}

	@Override public int compareTo(TermEntry o) {
		int a = super.compareTo(o);
		if (a != 0) { return a; }
		return index.toString().compareTo(((TermIndexEntry)o).index.toString());
	}

	@Override public boolean equals(Object o) {
		return super.equals(o) && index.equals(((TermIndexEntry)o).index);
	}

	public boolean equalsIgnoreSubject(TermEntry entry) {
		return (entry instanceof TermIndexEntry) && index.equals(((TermIndexEntry)entry).index);
	}

	@Override public int hashCode() {
		return super.hashCode() ^ index.hashCode();
	}
}
