/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import junit.framework.TestCase;

import java.util.Map;
import java.util.SortedMap;

import freenet.library.util.SortedMapTestSkeleton;


/**
** @author infinity0
*/
public class SkeletonTreeMapTest extends SortedMapTestSkeleton {

	SkeletonTreeMap<String, Integer> skelmap;

	protected void setUp() {
		skelmap = new SkeletonTreeMap<String, Integer>();
		for (int i=0; i<1024; ++i) {
			skelmap.putGhost(Generators.rndKey(), Boolean.FALSE);
		}
	}

	protected void tearDown() {
	}

	public void fillSkelMap() {
		for (String s: skelmap.keySet()) {
			skelmap.put(s, 123);
		}
	}

	@Override public SortedMap<String, Integer> makeTestMap() {
		return new SkeletonTreeMap<String, Integer>();
	}

	public void testBasic() {
		skelmap = new SkeletonTreeMap<String, Integer>();
		skelmap.putGhost("l0l", Boolean.FALSE);
		skelmap.putGhost("l1l", Boolean.FALSE);
		skelmap.putGhost("l2l", Boolean.FALSE);

		assertTrue(skelmap.firstKey().equals("l0l"));
		assertTrue(skelmap.lastKey().equals("l2l"));

		try {
			assertTrue(skelmap.get("zzz") == null);
			assertTrue(skelmap.get("123") == null);
			skelmap.get("l0l");
		} catch (DataNotLoadedException e) {
			assertTrue(e.getParent() == skelmap);
			assertTrue(e.getKey().equals("l0l"));
		}

		skelmap.put("l0l", new Integer(123));
		assertTrue(skelmap.get("l0l") == 123);

		assertTrue(skelmap.size() == 3);
		assertTrue(skelmap.remove("l0l") == 123);
		assertTrue(skelmap.size() == 2);

		try {
			skelmap.get("l1l");
		} catch (DataNotLoadedException e) {
			assertTrue(e.getParent() == skelmap);
			assertTrue(e.getKey().equals("l1l"));
		}

	}

	public void testIncompleteEntrySet() {
		try {
			for (Map.Entry<String, Integer> en: skelmap.entrySet()) {
				assertTrue(skelmap.entrySet().contains(en));
			}
		} catch (DataNotLoadedException e) {
			assertTrue(skelmap.firstKey() == e.getKey());
		}

		skelmap.put(skelmap.firstKey(), 123);
		try {
			for (Map.Entry<String, Integer> en: skelmap.entrySet()) {
				assertTrue(skelmap.entrySet().contains(en));
			}
		} catch (DataNotLoadedException e) {
			assertTrue(skelmap.firstKey() != e.getKey());
		}

		fillSkelMap();
		for (Map.Entry<String, Integer> en: skelmap.entrySet()) {
			assertTrue(skelmap.entrySet().contains(en));
		}
	}

	public void testIncompleteValues() {
		int i = 0;

		try {
			for (Integer en: skelmap.values()) {
				assertTrue(skelmap.values().contains(en));
				++i;
			}
		} catch (DataNotLoadedException e) {
			assertTrue(e.getValue() != null);
			assertTrue(i == 0);
		}

		skelmap.put(skelmap.firstKey(), 123);
		i = 0;
		try {
			for (Integer en: skelmap.values()) {
				assertTrue(skelmap.values().contains(en));
				++i;
			}
		} catch (DataNotLoadedException e) {
			assertTrue(i == 1);
		}

		fillSkelMap();
		for (Integer en: skelmap.values()) {
			assertTrue(skelmap.values().contains(en));
		}
	}

}
