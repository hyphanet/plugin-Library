/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import java.util.Comparator;
import java.util.SortedSet;

/**
** A comparator that is consistent with the identity operator on objects. The
** ordering is arbitrary, but consistent with the general contract for
** compare methods. This is useful when you want to have a sorted collection
** like {@link SortedSet}, but want to be able to have two objects (that would
** otherwise compare equal) to be present in the collection simultaneously.
**
** NOTE: this implementation assumes that you are using a sane JVM that
** generates identity hashcodes based on memory address, that can be used to
** test object identity.
**
** URGENT this is SERIOUSLY bugged because System.identityHashCode has a small
** chance of returning the same value for two distinct objects!!!
**
** @author infinity0
** @see System#identityHashCode(Object)
** @see Comparator#compare(Object, Object)
** @see Comparable#compareTo(Object)
*/
public class IdentityComparator<T> implements Comparator<T> {

	/**
	** A singleton comparator for use by {@link Comparable#compareTo(Object)}.
	*/
	final public static IdentityComparator comparator = new IdentityComparator();

	// why would you ever want one of these by itself? rethink your logic
	protected IdentityComparator() { }

	/**
	** Compare two objects by identity.
	*/
	public int compare(T o1, T o2) {
		int d = System.identityHashCode(o1) - System.identityHashCode(o2);
		return (d == 0)? 0: (d < 0)? 1: -1;
	}

}
