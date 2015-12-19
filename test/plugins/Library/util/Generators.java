/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import junit.framework.TestCase;

import plugins.Library.index.*;

import freenet.library.index.TermPageEntry;
import freenet.library.io.FreenetURIForTest;

import java.util.UUID;
import java.util.Random;
import java.net.MalformedURLException;

/**
** @author infinity0
*/
final public class Generators {

	final public static Random rand;

	static {
		long seed = System.currentTimeMillis();
		rand = new Random(seed);
		System.err.println("Generators.rand initialised with seed " + seed);
	}

	private Generators() {}

	public static String rndStr() {
		return UUID.randomUUID().toString();
	}

	public static String rndKey() {
		return rndStr().substring(0,8);
	}

	public static TermPageEntry rndEntry(String key) {
		return new TermPageEntry(key, (float)Math.random(), FreenetURIForTest.generateRandomCHK(rand), null);
	}

}
