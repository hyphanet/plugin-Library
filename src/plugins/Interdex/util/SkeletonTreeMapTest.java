/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import junit.framework.TestCase;

import java.util.Map;
import java.util.Iterator;
import java.util.UUID;

/**
** @author infinity0
*/
public class SkeletonTreeMapTest extends TestCase {

	SkeletonTreeMap<String, Integer> tm;

	protected void setUp() {
		tm = new SkeletonTreeMap<String, Integer>();
		for (int i=0; i<1024; ++i) {
			tm.putDummy(UUID.randomUUID().toString(), Boolean.FALSE);
		}
	}

	protected void tearDown() {
	}

	public void fillTestMap() {
		for (String s: tm.keySet()) {
			tm.put(s, 123);
		}
	}

	public void testBasic() {
		tm = new SkeletonTreeMap<String, Integer>();
		tm.putDummy("l0l", Boolean.FALSE);
		tm.putDummy("l1l", Boolean.FALSE);
		tm.putDummy("l2l", Boolean.FALSE);

		assertTrue(tm.firstKey().equals("l0l"));
		assertTrue(tm.lastKey().equals("l2l"));

		try {
			assertTrue(tm.get("zzz") == null);
			assertTrue(tm.get("123") == null);
			tm.get("l0l");
		} catch (DataNotLoadedException e) {
			assertTrue(e.getParent() == tm);
			assertTrue(e.getKey().equals("l0l"));
		}

		tm.put("l0l", new Integer(123));
		assertTrue(tm.get("l0l") == 123);

		assertTrue(tm.size() == 3);
		assertTrue(tm.remove("l0l") == 123);
		assertTrue(tm.size() == 2);

		try {
			tm.get("l1l");
		} catch (DataNotLoadedException e) {
			assertTrue(e.getParent() == tm);
			assertTrue(e.getKey().equals("l1l"));
		}

	}

	public void testIncompleteEntrySet() {
		try {
			for (Map.Entry<String, Integer> en: tm.entrySet()) {
				assertTrue(tm.entrySet().contains(en));
			}
		} catch (DataNotLoadedException e) {
			assertTrue(tm.firstKey() == e.getKey());
		}

		tm.put(tm.firstKey(), 123);
		try {
			for (Map.Entry<String, Integer> en: tm.entrySet()) {
				assertTrue(tm.entrySet().contains(en));
			}
		} catch (DataNotLoadedException e) {
			assertTrue(tm.firstKey() != e.getKey());
		}

		fillTestMap();
		for (Map.Entry<String, Integer> en: tm.entrySet()) {
			assertTrue(tm.entrySet().contains(en));
		}
	}

	public void testIncompleteValues() {
		int i = 0;

		try {
			for (Integer en: tm.values()) {
				assertTrue(tm.values().contains(en));
				++i;
			}
		} catch (DataNotLoadedException e) {
			assertTrue(e.getValue() != null);
			assertTrue(i == 0);
		}

		tm.put(tm.firstKey(), 123);
		i = 0;
		try {
			for (Integer en: tm.values()) {
				assertTrue(tm.values().contains(en));
				++i;
			}
		} catch (DataNotLoadedException e) {
			assertTrue(i == 1);
		}

		fillTestMap();
		for (Integer en: tm.values()) {
			assertTrue(tm.values().contains(en));
		}
	}

	public void testEntrySet() {
		// test that entrySet is properly backed by the map
		assertTrue(tm.entrySet() == tm.entrySet());
		assertTrue(tm.size() == tm.entrySet().size());
		tm.put(tm.firstKey(), 123);
		Map.Entry<String, Integer> entry = null;
		for (Map.Entry<String, Integer> en: tm.entrySet()) { entry = en; break; }
		assertTrue(tm.entrySet().contains(entry));
		entry.setValue(124);
		assertTrue(tm.get(tm.firstKey()) == 124);
		assertTrue(tm.entrySet().contains(entry));

		fillTestMap();
		int s = tm.size();
		int i = 0;
		Map.Entry<String, Integer> e = null;
		while (tm.size() > 0) {
			// get first entry
			for (Map.Entry<String, Integer> en: tm.entrySet()) { e = en; break; }
			assertTrue(e.getKey() == tm.firstKey());
			tm.entrySet().remove(e);
			++i;
		}
		assertTrue(s == i);
	}

	public void testKeySet() {
		// test that keySet is properly backed by the map
		assertTrue(tm.keySet() == tm.keySet());
		assertTrue(tm.size() == tm.keySet().size());
		fillTestMap();
		int s = tm.size();
		int i = 0;
		String e = null;
		while (tm.size() > 0) {
			// get first entry
			for (String en: tm.keySet()) { e = en; break; }
			assertTrue(e.equals(tm.firstKey()));
			tm.keySet().remove(e);
			++i;
		}
		assertTrue(s == i);
	}

	public void testValues() {
		// test that values is properly backed by the map
		assertTrue(tm.values() == tm.values());
		assertTrue(tm.size() == tm.values().size());
		fillTestMap();
		int s = tm.size();
		int i = 0;
		Integer e = null;
		while (tm.size() > 0) {
			// get first entry
			for (Integer en: tm.values()) { e = en; break; }
			assertTrue(e.equals(tm.get(tm.firstKey())));
			tm.values().remove(e);
			++i;
		}
		assertTrue(s == i);
	}

	public void testEntrySetIterator() {
		// test that entrySet.iterator is properly backed by the map
		fillTestMap();
		Iterator<Map.Entry<String, Integer>> it = tm.entrySet().iterator();
		while (it.hasNext()) {
			assertTrue(it.next().getKey() == tm.firstKey());
			it.remove();
		}
		assertTrue(tm.size() == 0);
	}

	public void testKeySetIterator() {
		// test that keySet.iterator is properly backed by the map
		fillTestMap();
		Iterator<String> it = tm.keySet().iterator();
		while (it.hasNext()) {
			assertTrue(it.next().equals(tm.firstKey()));
			it.remove();
		}
		assertTrue(tm.size() == 0);
	}

	public void testValuesIterator() {
		// test that values.iterator is properly backed by the map
		fillTestMap();
		Iterator<Integer> it = tm.values().iterator();
		while (it.hasNext()) {
			assertTrue(it.next().equals(tm.get(tm.firstKey())));
			it.remove();
		}
		assertTrue(tm.size() == 0);
	}

}
