/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version) {. See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.AbstractSet;

/**
** A {@link SortedSet} backed by a {@link SortedMap}, with one additional bonus
** method which I felt was lacking from the {@link Set} interface, namely
** {@link #get(Object)}.
**
** This implementation assumes that for the backing map, looking up items by
** key is more efficient than by value. It is up to the programmer to ensure
** that this holds; most maps are designed to have this property though.
**
** TODO this could be made to extend {@code MapSet} but I haven't yet bothered
** since I haven't needed it as a separate class of its own. Feel free.
**
** @author infinity0
*/
public class SortedMapSet<E, M extends SortedMap<E, E>> extends AbstractSet<E>
implements Set<E>, SortedSet<E>/*, NavigableSet<E>, Cloneable, Serializable*/ {

	/**
	** {@link SortedMap} backing this {@link SortedSet}.
	*/
	final protected M bkmap;

	/**
	** Construct a set backed by the given {@link SortedMap}.
	**
	** Note: this constructor assumes that all of the mappings in given map are
	** self-mappings, ie. for all {@code (k,v)} in {@code m}: {@code k == v}.
	** It is up to the calling code to ensure that this holds.
	*/
	protected SortedMapSet(M m) {
		assert(verifyBackingMapIntegrity(m));
		bkmap = m;
	}

	/**
	** Verifies the backing map for the condition described in the constructor.
	**
	** Note: this test is disabled (will always return true) since it is
	** impossible to test this for partially-loaded data structures. Code kept
	** here to remind future programmers of this.
	*/
	final static <E> boolean verifyBackingMapIntegrity(SortedMap<E, E> m) {
		/*for (Map.Entry<E, E> en: m.entrySet()) {
			assert(en.getKey() == en.getValue());
		}*/
		return true;
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
		return bkmap.get(o);
	}

	/*========================================================================
	  public interface Set
	 ========================================================================*/

	public int size() {
		return bkmap.size();
	}

	public boolean isEmpty() {
		return bkmap.isEmpty();
	}

	public boolean contains(Object o) {
		return bkmap.containsKey(o);
	}

	public Iterator<E> iterator() {
		return bkmap.keySet().iterator();
	}

	/* provided by AbstractSet
	public Object[] toArray() { }
	*/

	/* provided by AbstractSet
	public <T> T[] toArray(T[] a) { }
	*/

	public boolean add(E o) {
		if (o == null) {
			// BTreeMap doesn't support null keys at the time of coding, but this may change
			return !bkmap.containsKey(null)? bkmap.put(null, null) == null: false;
		}
		return bkmap.put(o, o) == null;
	}

	public boolean remove(Object o) {
		if (o == null) {
			// BTreeMap doesn't support null keys at the time of coding, but this may change
			return bkmap.containsKey(null)? bkmap.remove(null) == null: false;
		}
		return bkmap.remove(o) == o;
	}

	/* provided by AbstractSet
	public boolean containsAll(Collection<?> c) { }
	public boolean addAll(Collection<? extends E> c) { }
	public boolean retainAll(Collection<?> c) { }
	public boolean removeAll(Collection<?> c) { }
	*/

	public void clear() {
		bkmap.clear();
	}

	/* provided by AbstractSet
	public boolean equals(Object o) { }
	public int hashCode() { }
	*/

	/*========================================================================
	  public interface SortedSet
	 ========================================================================*/

	public Comparator<? super E> comparator() {
		return bkmap.comparator();
	}

	public E first() {
		return bkmap.firstKey();
	}

	public E last() {
		return bkmap.lastKey();
	}

	public SortedSet<E> headSet(E to) {
		return new SortedMapSet<E, SortedMap<E, E>>(bkmap.headMap(to));
	}

	public SortedSet<E> tailSet(E fr) {
		return new SortedMapSet<E, SortedMap<E, E>>(bkmap.tailMap(fr));
	}

	public SortedSet<E> subSet(E fr, E to) {
		return new SortedMapSet<E, SortedMap<E, E>>(bkmap.subMap(fr, to));
	}

}
