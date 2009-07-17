/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import junit.framework.TestCase;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

/**
** @author infinity0
*/
public class BTreeMapTest extends TestCase {

	SkeletonTreeMap<String, Integer> tm;

	public String rndStr() {
		return java.util.UUID.randomUUID().toString();
	}

	public void testBasic() {

		BTreeMap<String, String> testmap = new BTreeMap<String, String>(16);
		Map<String, String> backmap = new HashMap<String, String>();
		try {
			for (int i=0; i<0x10000;) {
				for (int j=0; j<0x400; ++j, ++i) {
					String k = rndStr().substring(0,8), v = rndStr();
					testmap.put(k, v);
					backmap.put(k, v);
				}
				testmap.verifyTreeIntegrity();
			}
			System.out.println("Successfully put 0x10000 entries to the BTreeMap");
			Iterator<String> it = backmap.keySet().iterator();
			for (int i=0; i<0x10000;) {
				for (int j=0; j<0x400; ++j, ++i) {
					testmap.remove(it.next());
				}
				testmap.verifyTreeIntegrity();
			}
			System.out.println("Successfully removed 0x10000 entries to the BTreeMap");

		} catch (AssertionError e) {
			//System.out.println(testmap.toPrettyString());
			throw e;
		}

	}

}
