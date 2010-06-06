/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

/**
** Defines an interface that provides prefix-related operations, such as
** returning the next component of a key,
**
** @author infinity0
*/
abstract public class PrefixKey<K extends PrefixKey<K>> implements Cloneable, Comparable<K> {

	abstract public PrefixKey<K> clone();

	/**
	** Returns the number of possible symbols at each cell of the key. This
	** should return the same value for any instance.
	*/
	abstract public int symbols();

	/**
	** Returns the size of the key. This should return the same value for
	** any instance.
	*/
	abstract public int size();

	/**
	** Gets one cell of the key.
	*/
	abstract public int get(int i);

	/**
	** Sets one cell of the key.
	*/
	abstract public void set(int i, int v);

	/**
	** Clears one cell of the key.
	*/
	abstract public void clear(int i);

	/**
	** Returns a new key with a new value set for one of the cells.
	*/
	public K spawn(int i, int v) {
		K p = (K)clone();
		p.set(i, v);
		return p;
	}

	/**
	** Clears all cells from a given index.
	*/
	public void clearFrom(int len) {
		for (int i=len; i<size(); ++i) { clear(i); }
	}

	/**
	** Whether two keys have matching prefixes.
	**
	** @param p The key to match against
	** @param len Length of prefix to match
	*/
	public boolean match(K p, int len) {
		for (int i=0; i<len; ++i) {
			if (get(i) != p.get(i)) { return false; }
		}
		return true;
	}

	@Override public int compareTo(K p) {
		for (int i=0; i<size(); ++i) {
			int x = get(i);
			int y = p.get(i);
			if (x != y) { return (x > y) ? 1: -1; }
		}
		return 0;
	}

}
