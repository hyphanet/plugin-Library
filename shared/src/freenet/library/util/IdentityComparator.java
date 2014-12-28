/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.util;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.IdentityHashMap;
import java.util.HashMap;

/**
** A comparator that is consistent with the identity operator on objects. The
** ordering is arbitrary, but consistent with the general contract for
** compare methods. This is useful when you want to have a sorted collection
** like {@link SortedSet}, but want to be able to have two objects (that would
** otherwise compare equal) to be present in the collection simultaneously.
**
** This implementation will remain consistent even if it encounters two objects
** with the *same* identity hash code, by assigning a unique 64-bit id to each
** distinct object, as it encounters such objects.
**
** Since identity hashcodes are generated arbitrarily by the JVM, the ordering
** imposed by this comparator will change between different runs of the JVM.
** Hence, the exact ordering must not be treated as an instrinsic or immutable
** property of the underlying collection.
**
** The intended use of this class is for another class to subclass it, or to
** have a {@link Comparable#compareTo(Object)} call the method of the singleton
** provided in the {@link #comparator} field.
**
** @author infinity0
** @see System#identityHashCode(Object)
** @see Comparator#compare(Object, Object)
** @see Comparable#compareTo(Object)
*/
abstract public class IdentityComparator<T> implements Comparator<T> {

	/**
	** A singleton comparator for use by {@link Comparable#compareTo(Object)}.
	*/
	final public static IdentityComparator comparator = new IdentityComparator() {};

	/**
	** Keeps track of objects with the same identity hashcode, and the unique
	** IDs assigned to them by the comparator. This map is *ONLY* used if the
	** comparator encounters two distinct objects with the same identity
	** hashcodes.
	*/
	final private static IdentityHashMap<Object, Long> objectid = new IdentityHashMap<Object, Long>(4);

	/**
	** Counts the number of objects that have a given identity hashcode. This
	** map is *ONLY* used if the comparator encounters two distinct objects
	** with the same identity hashcodes.
	*/
	final private static HashMap<Integer, Long> idcounter = new HashMap<Integer, Long>(4);

	/**
	** Compare two objects by identity.
	*/
	public int compare(T o1, T o2) {
		if (o1 == o2) { return 0; }
		int h1 = System.identityHashCode(o1);
		int h2 = System.identityHashCode(o2);
		if (h1 != h2) {
			return (h1 > h2)? 1: -1;
		} else {
			synchronized (IdentityComparator.class) {
				Long counter = idcounter.get(h1);
				if (counter == null) { counter = 0L; }

				Long l1 = objectid.get(o1);
				Long l2 = objectid.get(o2);
				if (l1 == null) { l1 = counter++; objectid.put(o1, l1); }
				if (l2 == null) { l2 = counter++; objectid.put(o2, l2); }

				idcounter.put(h1, counter);
				assert((long)l1 != (long)l2);
				return (l1 > l2)? 1: -1;
			}
		}
	}

}
