/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import junit.framework.TestCase;

import java.util.UUID;
import java.util.Random;

/**
** @author infinity0
*/
abstract public class Generators {

	public static String rndStr() {
		return UUID.randomUUID().toString();
	}

	public static String rndKey() {
		return rndStr().substring(0,8);
	}

	public static Random rand = new Random();

}
