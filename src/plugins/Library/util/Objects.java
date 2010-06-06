/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import plugins.Library.io.DataFormatException;

/**
** Utility methods for general {@link Object}s
**
** @author infinity0
*/
final public class Objects {

	final public static String idString(Object o) {
		// Default toString for an AbstractCollection dumps everything underneath it.
		// We don't want that here, especially as they may not be loaded.
		return o.getClass().getName() + "@" + System.identityHashCode(o);
	}

	/**
	** A checked cast which throws {@link DataFormatException} on fail, and
	** which bypasses "unchecked cast" warnings. Useful for eg. when reading
	** objects from streams.
	**
	** @throws DataFormatException
	*/
	@SuppressWarnings("unchecked")
	final public static <T> T castT(Object o) throws DataFormatException {
		try {
			return (T)o;
		} catch (ClassCastException e) {
			throw new DataFormatException("Cast failed", e, o);
		}
	}

	/**
	** A checked cast which throws {@link AssertionError} on fail, and which
	** bypasses "unchecked cast" warnings. Useful for eg. when using containers
	** from external libraries that are not sufficiently type-parameterised.
	**
	** @throws AssertionError
	*/
	@SuppressWarnings("unchecked")
	final public static <T> T castA(Object o) {
		try {
			return (T)o;
		} catch (ClassCastException e) {
			AssertionError x = new AssertionError("Cast failed");
			x.initCause(e);
			throw x;
		}
	}

}

