/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

/**
** @author infinity0
*/
public class IndexTree {

	IndexNode root;
	String subIndexFormat;
	String filterType;
	// TODO: etc

	public IndexEntry getEntry(Token t) {
		// TODO: override me
		// Maybe even return HashSet<TokenEntry> instead?
		return root.tmap.get(t);
	}

	public void addEntry(IndexTokenEntry n) {
		// TODO
	}

	public void remEntry(IndexTokenEntry n) {
		// TODO
	}

	// TODO: maybe have these in a separate class, X extends IndexTreeSerialiser?
	public void load() { }
	public void save() { }

}
