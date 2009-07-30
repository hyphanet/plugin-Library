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


public class BTreeSet<E> extends AbstractSet<E>
implements Set<E>, SortedSet<E>/*, NavigableSet<E>, Cloneable, Serializable*/ {

	final protected SortedMap<E, E> map;

	/**
	** Creates a new empty set, sorted according to the given comparator, and
	** with each non-root node having the given minimum number of subnodes.
	**
	** @param cmp The comparator for the tree, or {@code null} to use the keys'
	**            {@link Comparable natural} ordering.
	** @param node_min Minimum number of subnodes in each node
	*/
	public BTreeSet(Comparator<? super E> cmp, int node_min) {
		map = new BTreeMap<E, E>(cmp, node_min);
	}

	/**
	** Creates a new empty set, sorted according to the keys' {@link Comparable
	** natural} ordering, and with each non-root node having the given minimum
	** number of subnodes.
	**
	** @param node_min Minimum number of subnodes in each node
	**/
	public BTreeSet(int node_min) {
		map = new BTreeMap<E, E>(node_min);
	}

	/**
	** Creates a new empty set, sorted according to the keys' {@link Comparable
	** natural} ordering, and with each non-root node having at least 256
	** subnodes.
	*/
	public BTreeSet() {
		map = new BTreeMap<E, E>();
	}

	/**
	** Protected constructor for use by the {@link #subSet(Object, Object)}
	** etc. methods.
	*/
	protected BTreeSet(SortedMap<E, E> m) {
		map = m;
	}

	/*========================================================================
	  public interface Set
	 ========================================================================*/

	@Override public int size() {
		return map.size();
	}

	@Override public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override public boolean contains(Object o) {
		return map.containsKey(o);
	}

	@Override public Iterator<E> iterator() {
		return map.keySet().iterator();
	}

	/* provided by AbstractSet
	@Override public Object[] toArray() { }
	*/

	/* provided by AbstractSet
	@Override public <T> T[] toArray(T[] a) { }
	*/

	@Override public boolean add(E o) {
		if (o == null) {
			// BTreeMap doesn't support null keys at the time of coding, but this may change
			return !map.containsKey(null)? map.put(null, null) == null: false;
		}
		return map.put(o, o) == null;
	}

	@Override public boolean remove(Object o) {
		if (o == null) {
			// BTreeMap doesn't support null keys at the time of coding, but this may change
			return map.containsKey(null)? map.remove(null) == null: false;
		}
		return map.remove(o) == o;
	}

	/**
	** Returns the object reference-identical to the one added into the set.
	**
	** @param o An object "equal" (ie. compares 0) to the one in the set.
	** @return The object contained in the set.
	** @throws ClassCastException object cannot be compared with the objects
	**         currently in the map
	** @throws NullPointerException o is {@code null} and this map uses
	**         natural order, or its comparator does not tolerate {@code null}
	**         keys
	*/
	public E get(Object o) {
		return map.get(o);
	}

	/* provided by AbstractSet
	@Override public boolean containsAll(Collection<?> c) { }
	@Override public boolean addAll(Collection<? extends E> c) { }
	@Override public boolean retainAll(Collection<?> c) { }
	@Override public boolean removeAll(Collection<?> c) { }
	*/

	@Override public void clear() {
		map.clear();
	}

	/* provided by AbstractSet
	@Override public boolean equals(Object o) { }
	@Override public int hashCode() { }
	*/

	/*========================================================================
	  public interface SortedSet
	 ========================================================================*/

	@Override public Comparator<? super E> comparator() {
		return map.comparator();
	}

	@Override public E first() {
		return map.firstKey();
	}

	@Override public E last() {
		return map.lastKey();
	}

	@Override public SortedSet<E> headSet(E to) {
		return new BTreeSet(map.headMap(to));
	}
	@Override public SortedSet<E> tailSet(E fr) {
		return new BTreeSet(map.tailMap(fr));
	}

	@Override public SortedSet<E> subSet(E fr, E to) {
		return new BTreeSet(map.subMap(fr, to));
	}

}
