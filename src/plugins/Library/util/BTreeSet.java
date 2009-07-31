/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version) {. See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.AbstractSet;

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
	** Creates a new empty set, sorted according to the keys' {@link Comparable
	** natural} ordering, and with each non-root node having at least 256
	** subnodes.
	*/
	public BTreeSet() {
		super(new BTreeMap<E, E>());
	}

	/**
	** Protected constructor for use by the {@link SkeletonBTreeSet}
	** constructors.
	*/
	protected BTreeSet(BTreeMap<E, E> m) {
		super(m);
	}

	/**
	** Returns the number of entries contained in the root node. If this object
	** is actually a {@link #subSet(Object, Object) subSet} of an actual {@code
	** BTreeSet}, then this will throw {@link UnsupportedOperationException}.
	*/
	public int rootSize() {
		if (map instanceof BTreeMap) {
			return ((BTreeMap)map).rootSize();
		}
		throw new UnsupportedOperationException("This is not a full BTreeSet and so cannot access the root");
	}

}
