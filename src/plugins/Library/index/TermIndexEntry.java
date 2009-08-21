/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.index.TermEntry.EntryType;

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
	final public FreenetURI index;

	public TermIndexEntry(String s, float r, FreenetURI i) {
		super(s, r);
		// OPTIMISE have some intern pool of FreenetURIs like we have for Token.
		// Or just use a string instead?
		index = i;
	}

	/*========================================================================
	  abstract public class TermEntry
	 ========================================================================*/

	@Override public EntryType entryType() {
		assert(getClass() == TermIndexEntry.class);
		return EntryType.INDEX;
	}

	@Override public int compareTo(TermEntry o) {
		int a = super.compareTo(o);
		if (a != 0) { return a; }
		// OPTIMISE find a more efficient way than this
		return index.toString().compareTo(((TermIndexEntry)o).index.toString());
	}

	@Override public boolean equals(Object o) {
		return super.equals(o) && index.equals(((TermIndexEntry)o).index);
	}

	@Override public boolean equalsTarget(TermEntry entry) {
		return (entry instanceof TermIndexEntry) && index.equals(((TermIndexEntry)entry).index);
	}

	@Override public int hashCode() {
		return super.hashCode() ^ index.hashCode();
	}

}
