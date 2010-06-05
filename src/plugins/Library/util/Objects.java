/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

final public class Objects {

	public static String idString(Object o) {
		// Default toString for an AbstractCollection dumps everything underneath it.
		// We don't want that here, especially as they may not be loaded.
		return o.getClass().getName() + "@" + System.identityHashCode(o);
	}

}

