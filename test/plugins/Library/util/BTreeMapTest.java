/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import java.util.*;

/**
** @author infinity0
*/
public class BTreeMapTest extends SortedMapTestSkeleton {

	@Override public SortedMap<String, Integer> makeTestMap() {
		return new BTreeMap<String, Integer>(0x40);
	}

	final public static int sz0 = 0x400;
	final public static int sz1 = sz0<<2; //sz0<<6;

	private static String rndStr() {
		return UUID.randomUUID().toString();
	}

	private static String rndKey() {
		return rndStr().substring(0,8);
	}

	public void testBasic() {

		BTreeMap<String, String> testmap = new BTreeMap<String, String>(0x40);
		Map<String, String> backmap = new HashMap<String, String>();
		try {
			for (int i=0; i<sz1;) {
				for (int j=0; j<sz0; ++j, ++i) {
					String k = rndKey(), v = rndStr();
					testmap.put(k, v);
					backmap.put(k, v);
				}
				testmap.verifyTreeIntegrity();
			}
			int s = testmap.size(); // random there so could be dupes
			assertTrue(s <= sz1 && testmap.size() == backmap.size());

			Iterator<String> it = backmap.keySet().iterator();
			for (int i=0; i<s;) {
				for (int j=0; j<sz0 && i<s; ++j, ++i) {
					testmap.remove(it.next());
					it.remove();
				}
				testmap.verifyTreeIntegrity();
			}
			assertTrue(testmap.size() == 0 && backmap.size() == 0);

		} catch (AssertionError e) {
			System.out.println(testmap.toTreeString());
			throw e;
		}

	}

	public void testBulkLoading() {

		for (int n=0; n<0x100; ++n) {
			Map<String, String> backmap = new TreeMap<String, String>();
			for (int i=0; i<n; ++i) {
				String k = rndKey(), v = rndStr();
				backmap.put(k, v);
			}

			BTreeMap<String, String> testmap = new BTreeMap<String, String>(2);
			testmap.putAll(backmap);
			testmap.verifyTreeIntegrity();
			//if (n<10) { System.out.println(testmap.toTreeString()); }
		}

	}

	public void testNumericIndexes() {

		BTreeMap<Integer, Integer> testmap = new BTreeMap<Integer, Integer>(0x40);
		int i=0;

		for (i=0; i<sz1; ++i) {
			testmap.put(i, i);
		}

		for (i=0; i<sz1; ++i) {
			assertTrue(testmap.getEntry(i).getKey().equals(i));
		}

	}

	public void testUtilityMethods() {
		// TODO HIGH more of these, like node.subEntries etc
		SortedSet<String> ts = (new BTreeMap<String, String>(0x40)).subSet(new TreeSet<String>(
			Arrays.asList("91665799", "93fc4806", "94ff78b2", "952225c8", "9678e897", "96bb4208",
			"989c8d3f", "9a1f0877", "9faea63f", "9fec4192", "a19e7d4e", "a61a2c10",
			"a6c681ec", "a72d4e4e", "a845d2cb")), "96bb4208", "9fec4192");
		assertTrue(ts.first().equals("989c8d3f"));
		assertTrue(ts.last().equals("9faea63f"));
	}

}
