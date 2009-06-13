/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import java.util.MissingResourceException;

/**
** Thrown when data hasn't been loaded yet, eg. when a data structure hasn't
** been fully populated from the network.
**
** @author infinity0
*/
public class DataNotLoadedException extends MissingResourceException {

	final Object incomplete;
	final Object key;

	public DataNotLoadedException(String s, Object o, Object k) {
		super(s, o.getClass().getName(), k.toString());
		incomplete = o;
		key = k;
	}

}
