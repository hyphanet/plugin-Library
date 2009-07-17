/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import junit.framework.TestCase;

import java.util.Map;
import java.util.SortedMap;
import java.util.HashMap;
import java.util.Iterator;

/**
** @author infinity0
*/
public class BTreeMapTest extends SortedMapTestSkeleton {

	@Override public SortedMap<String, Integer> makeTestMap() {
		return new BTreeMap<String, Integer>(16);
	}

	public void testBasic() {

		BTreeMap<String, String> testmap = new BTreeMap<String, String>(16);
		Map<String, String> backmap = new HashMap<String, String>();
		try {
			for (int i=0; i<0x10000;) {
				for (int j=0; j<0x400; ++j, ++i) {
					String k = Generators.rndKey(), v = Generators.rndStr();
					testmap.put(k, v);
					backmap.put(k, v);
				}
				testmap.verifyTreeIntegrity();
			}
			int s = testmap.size(); // random there so could be dupes
			assert(s <= 0x10000 && testmap.size() == backmap.size());
			System.out.println("Successfully put " + s + " entries to the BTreeMap");

			Iterator<String> it = backmap.keySet().iterator();
			for (int i=0; i<s;) {
				for (int j=0; j<0x400 && i<s; ++j, ++i) {
					testmap.remove(it.next());
					it.remove();
				}
				testmap.verifyTreeIntegrity();
			}
			assert(testmap.size() == 0 && backmap.size() == 0);
			System.out.println("Successfully removed " + s + " entries to the BTreeMap");

		} catch (AssertionError e) {
			System.out.println(testmap.toTreeString());
			throw e;
		}

	}

}
