/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.util;

import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;

import freenet.library.io.serial.MapSerialiser;
import freenet.library.util.DataNotLoadedException;
import freenet.library.util.SkeletonTreeMap;
import freenet.library.util.exec.TaskAbortException;


/**
** @author infinity0
*/
public class SkeletonTreeMapTest extends SortedMapTestSkeleton {

	SkeletonTreeMap<String, Integer> skelmap;

	private static String rndStr() {
		return UUID.randomUUID().toString();
	}

	private static String rndKey() {
		return rndStr().substring(0,8);
	}

	protected void setUp() {
		skelmap = new SkeletonTreeMap<String, Integer>();
		for (int i=0; i<1024; ++i) {
			skelmap.putGhost(rndKey(), Boolean.FALSE);
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

	class SkelMapMapSerializer<T1, T2> implements MapSerialiser<String, Integer> {

		@Override
		public void pull(Map<String, PullTask<Integer>> tasks, Object mapmeta) throws TaskAbortException {
			for (Map.Entry<String, PullTask<Integer>> en : tasks.entrySet()) {
				// Simulate existing contents
				en.getValue().data = 12;
			}
		}

		@Override
		public void push(Map<String, PushTask<Integer>> tasks, Object mapmeta) throws TaskAbortException {
			// Simulate storage.
		}

	}

	public void testInflateAndDeflate() throws TaskAbortException {
		skelmap.setSerialiser(new SkelMapMapSerializer<String, Integer>());
		assertFalse(skelmap.isLive());
		assertTrue(skelmap.isBare());
		skelmap.inflate();
		assertTrue(skelmap.isLive());
		assertFalse(skelmap.isBare());
		for (Map.Entry<String, Integer> en : skelmap.entrySet()) {
			assertTrue(skelmap.entrySet().contains(en));
			assertNotNull(skelmap.get(en.getKey()));
			assertEquals(skelmap.get(en.getKey()), new Integer(12));
		}

		assertTrue(skelmap.isLive());
		assertFalse(skelmap.isBare());
		skelmap.deflate();
		assertFalse(skelmap.isLive());
		assertTrue(skelmap.isBare());
		for (Map.Entry<String, Integer> en : skelmap.entrySet()) {
			try {
				skelmap.entrySet().contains(en);
			} catch (DataNotLoadedException e) {
				continue;
			}
			fail("Data was loaded for " + en);
		}
	}

	public void testSingleInflateAndDeflate() throws TaskAbortException {
		skelmap.setSerialiser(new SkelMapMapSerializer<String, Integer>());
		assertFalse(skelmap.isLive());
		assertTrue(skelmap.isBare());
		skelmap.inflate(skelmap.firstKey());
		assertFalse(skelmap.isLive());
		assertFalse(skelmap.isBare());
		for (Map.Entry<String, Integer> en : skelmap.entrySet()) {
			assertTrue(skelmap.entrySet().contains(en));
			assertNotNull(skelmap.get(en.getKey()));
			assertEquals(skelmap.get(en.getKey()), new Integer(12));
			break;
		}

		assertFalse(skelmap.isLive());
		assertFalse(skelmap.isBare());
		skelmap.deflate(skelmap.firstKey());
		assertFalse(skelmap.isLive());
		assertTrue(skelmap.isBare());
		for (Map.Entry<String, Integer> en : skelmap.entrySet()) {
			try {
				skelmap.entrySet().contains(en);
			} catch (DataNotLoadedException e) {
				continue;
			}
			fail("Data was loaded for " + en);
		}
	}

}
