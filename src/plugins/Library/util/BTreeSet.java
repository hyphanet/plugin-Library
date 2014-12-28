/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version) {. See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import java.util.Comparator;
import java.util.Set;
import java.util.SortedSet;

import freenet.library.util.SortedMapSet;

/**
** A B-tree set implementation backed by a {@link BTreeMap}. DOCUMENT
**
** @author infinity0
*/
public class BTreeSet<E> extends SortedMapSet<E, BTreeMap<E, E>>
implements Set<E>, SortedSet<E>/*, NavigableSet<E>, Cloneable, Serializable*/ {

	/**
	** Creates a new empty set, sorted according to the given comparator, and
	** with each non-root node having the given minimum number of subnodes.
	**
	** @param cmp The comparator for the tree, or {@code null} to use the keys'
	**            {@link Comparable natural} ordering.
	** @param node_min Minimum number of subnodes in each node
	*/
	public BTreeSet(Comparator<? super E> cmp, int node_min) {
		super(new BTreeMap<E, E>(cmp, node_min));
	}

	/**
	** Creates a new empty set, sorted according to the keys' {@link Comparable
	** natural} ordering, and with each non-root node having the given minimum
	** number of subnodes.
	**
	** @param node_min Minimum number of subnodes in each node
	**/
	public BTreeSet(int node_min) {
		super(new BTreeMap<E, E>(node_min));
	}

	/**
	** Protected constructor for use by the {@link SkeletonBTreeSet}
	** constructors.
	*/
	protected BTreeSet(BTreeMap<E, E> m) {
		super(m);
	}

	/**
	** Returns the number of entries contained in the root node.
	*/
	public int sizeRoot() {
		return bkmap.sizeRoot();
	}

	public int nodeMin() {
		return bkmap.nodeMin();
	}

	public int entMax() {
		return bkmap.entMax();
	}

	public int heightEstimate() {
		return bkmap.heightEstimate();
	}

	/**
	** Returns the element at a particular (zero-based) index.
	*/
	public E get(int i) {
		return bkmap.getEntry(i).getKey();
	}


}
