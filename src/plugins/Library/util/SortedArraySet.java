/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version) {. See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import freenet.support.Fields; // JDK6: use this instead of Arrays.binarySearch

import java.util.Comparator;
import java.util.Iterator;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.AbstractSet;

/**
** An immutable {@link SortedSet} backed by a sorted array.
**
** @author infinity0
*/
public class SortedArraySet<E> extends AbstractSet<E>
implements Set<E>, SortedSet<E>/*, NavigableSet<E>, Cloneable, Serializable*/ {

	/**
	** The backing array.
	*/
	final protected E[] bkarr;

	/**
	** The comparator used to sort the array.
	*/
	final protected Comparator<? super E> comparator;

	/**
	** Left index, inclusive.
	*/
	final int li;

	/**
	** Right index, exclusive.
	*/
	final int ri;


	public SortedArraySet(E[] arr) {
		this(arr, null, true);
	}

	public SortedArraySet(E[] arr, Comparator<? super E> cmp, boolean sort) {
		this(arr, 0, arr.length, cmp);
		if (sort) { Arrays.sort(bkarr); }
	}

	protected SortedArraySet(E[] arr, int l, int r, Comparator<? super E> cmp) {
		assert(0 <= l && l <= r && r <= arr.length);
		bkarr = arr;
		li = l;
		ri = r;
		comparator = cmp;
	}

	/*========================================================================
	  public interface Set
	 ========================================================================*/

	@Override public int size() {
		return ri - li;
	}

	@Override public boolean isEmpty() {
		return ri == li;
	}

	@Override public boolean contains(Object o) {
		return Fields.binarySearch(bkarr, li, ri, (E)o, comparator) > 0;
	}

	@Override public Iterator<E> iterator() {
		return new Iterator<E>() {
			int i = li;

			public boolean hasNext() {
				return i < ri;
			}

			public E next() {
				return bkarr[i++];
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	/* provided by AbstractSet
	@Override public Object[] toArray() { }
	*/

	/* provided by AbstractSet
	@Override public <T> T[] toArray(T[] a) { }
	*/

	@Override public boolean add(E o) {
		throw new UnsupportedOperationException("This set is immutable");
	}

	@Override public boolean remove(Object o) {
		throw new UnsupportedOperationException("This set is immutable");
	}

	/* provided by AbstractSet
	@Override public boolean containsAll(Collection<?> c) { }
	@Override public boolean addAll(Collection<? extends E> c) { }
	@Override public boolean retainAll(Collection<?> c) { }
	@Override public boolean removeAll(Collection<?> c) { }
	*/

	@Override public void clear() {
		if (!isEmpty()) {
			throw new UnsupportedOperationException("This set is immutable");
		}
	}

	/* provided by AbstractSet
	@Override public boolean equals(Object o) { }
	@Override public int hashCode() { }
	*/

	/*========================================================================
	  public interface SortedSet
	 ========================================================================*/

	/*@Override**/ public Comparator<? super E> comparator() {
		return comparator;
	}

	/*@Override**/ public E first() {
		return bkarr[li];
	}

	/*@Override**/ public E last() {
		return bkarr[ri-1];
	}

	/*@Override**/ public SortedSet<E> headSet(E to) {
		int d = Fields.binarySearch(bkarr, li, ri, to, comparator);
		if (d < 0) { d = -(1+d);}
		if (d < li || ri < d) {
			throw new IllegalArgumentException("Argument not in this subset's range");
		}
		return new SortedArraySet<E>(bkarr, li, d, comparator);
	}

	/*@Override**/ public SortedSet<E> tailSet(E fr) {
		int d = Fields.binarySearch(bkarr, li, ri, fr, comparator);
		if (d < 0) { d = -(1+d);}
		if (d < li || ri < d) {
			throw new IllegalArgumentException("Argument not in this subset's range");
		}
		return new SortedArraySet<E>(bkarr, d, ri, comparator);
	}

	/*@Override**/ public SortedSet<E> subSet(E fr, E to) {
		return tailSet(fr).headSet(to);
	}

}
