/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import junit.framework.TestCase;

import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.Iterator;
import java.util.UUID;

/**
** TODO maybe make some tailMap, headMap, subMap tests
**
** @author infinity0
*/
abstract public class SortedMapTestSkeleton extends TestCase {

	protected SortedMap<String, Integer> testmap;

	abstract protected SortedMap<String, Integer> makeTestMap();

	final private static Random rand = new Random();

	private static String rndStr() {
		return UUID.randomUUID().toString();
	}

	public void fillTestMap() {
		testmap = makeTestMap();
		for (int i=0; i<0x1000; ++i) {
			testmap.put(rndStr(), rand.nextInt());
		}
	}

	public void testEntrySet() {
		// test that entrySet is properly backed by the map
		fillTestMap();
		assertTrue(testmap.entrySet() == testmap.entrySet());
		assertTrue(testmap.size() == testmap.entrySet().size());
		testmap.put(testmap.firstKey(), 123);
		Map.Entry<String, Integer> entry = null;
		for (Map.Entry<String, Integer> en: testmap.entrySet()) { entry = en; break; }
		assertTrue(testmap.entrySet().contains(entry));
		entry.setValue(124);
		assertTrue(testmap.get(testmap.firstKey()) == 124);
		assertTrue(testmap.entrySet().contains(entry));

		int s = testmap.size(), i = 0;
		for (Map.Entry<String, Integer> en: testmap.entrySet()) { ++i; }
		assertTrue(s == i);
		i = 0;

		Map.Entry<String, Integer> e = null;
		while (testmap.size() > 0) {
			// get first entry
			for (Map.Entry<String, Integer> en: testmap.entrySet()) { e = en; break; }
			assertTrue(e.getKey() == testmap.firstKey());
			testmap.entrySet().remove(e);
			++i;
		}
		assertTrue(s == i);
	}

	public void testKeySet() {
		// test that keySet is properly backed by the map
		fillTestMap();
		assertTrue(testmap.keySet() == testmap.keySet());
		assertTrue(testmap.size() == testmap.keySet().size());
		int s = testmap.size(), i = 0;
		for (String en: testmap.keySet()) { ++i; }
		assertTrue(s == i);
		i = 0;
		String e = null;
		while (testmap.size() > 0) {
			// get first entry
			for (String en: testmap.keySet()) { e = en; break; }
			assertTrue(e.equals(testmap.firstKey()));
			testmap.keySet().remove(e);
			++i;
		}
		assertTrue(s == i);
	}

	public void testValues() {
		// test that values is properly backed by the map
		fillTestMap();
		assertTrue(testmap.values() == testmap.values());
		assertTrue(testmap.size() == testmap.values().size());
		int s = testmap.size(), i = 0;
		for (Integer en: testmap.values()) { ++i; }
		assertTrue(s == i);
		i = 0;
		Integer e = null;
		while (testmap.size() > 0) {
			// get first entry
			for (Integer en: testmap.values()) { e = en; break; }
			assertTrue(e.equals(testmap.get(testmap.firstKey())));
			testmap.values().remove(e);
			++i;
		}
		assertTrue(s == i);
	}

	public void testEntrySetIterator() {
		// test that entrySet.iterator is properly backed by the map
		fillTestMap();
		Iterator<Map.Entry<String, Integer>> it = testmap.entrySet().iterator();
		int s=testmap.size(), i=0;
		while (it.hasNext()) {
			assertTrue(it.next().getKey() == testmap.firstKey());
			it.remove();
			++i;
		}
		assertTrue(i == s);
		assertTrue(testmap.size() == 0);
	}

	public void testKeySetIterator() {
		// test that keySet.iterator is properly backed by the map
		fillTestMap();
		Iterator<String> it = testmap.keySet().iterator();
		int s=testmap.size(), i=0;
		while (it.hasNext()) {
			assertTrue(it.next().equals(testmap.firstKey()));
			it.remove();
			++i;
		}
		assertTrue(i == s);
		assertTrue(testmap.size() == 0);
	}

	public void testValuesIterator() {
		// test that values.iterator is properly backed by the map
		fillTestMap();
		Iterator<Integer> it = testmap.values().iterator();
		int s=testmap.size(), i=0;
		while (it.hasNext()) {
			assertTrue(it.next().equals(testmap.get(testmap.firstKey())));
			it.remove();
			++i;
		}
		assertTrue(i == s);
		assertTrue(testmap.size() == 0);
	}

}
