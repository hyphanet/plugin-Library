/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.event;

import junit.framework.TestCase;

import java.util.Iterator;

/**
** @author infinity0
*/
public class SweepersTest extends TestCase {

	public static Object[] testobj = new Object[]{ new Integer(0), new Integer(1), new Integer(2) };

	public void testCountingSweeper() {
		testGenericSweeper(new CountingSweeper(false), new CountingSweeper(false));
	}

	public void testTrackingSweeper() {
		testGenericSweeper(new TrackingSweeper(false), new TrackingSweeper(false));
		testIterableSweeper(new TrackingSweeper(false), new TrackingSweeper(false));
	}

	public <S extends Sweeper> void testGenericSweeper(S sw1, S sw2) {
		testAfterNew(sw1);
		testCloseClear(sw1);
		testAfterClear(sw1);

		testAfterNew(sw2);
		testReleaseClear(sw2);
		testAfterClear(sw2);
	}

	public <S extends Sweeper & Iterable> void testIterableSweeper(S sw1, S sw2) {
		testAfterNew(sw1);
		testCloseClearIterable(sw1);
		testAfterClear(sw1);

		testAfterNew(sw2);
		testReleaseClearIterable(sw2);
		testAfterClear(sw2);
	}

	@SuppressWarnings("unchecked")
	public void testAfterNew(final Sweeper sw) {
		assertTrue(sw.getState() == Sweeper.SweeperState.NEW);
		testIllegalStateException(new Runnable() { public void run() { sw.acquire(testobj[0]); } }, "acquiring when not open");
		testIllegalStateException(new Runnable() { public void run() { sw.acquire(testobj[1]); } }, "acquiring when not open");
		testIllegalStateException(new Runnable() { public void run() { sw.acquire(testobj[2]); } }, "acquiring when not open");
		testIllegalStateException(new Runnable() { public void run() { sw.release(testobj[0]); } }, "releasing when not open");
		testIllegalStateException(new Runnable() { public void run() { sw.close(); } }, "closing when not open");
		assertTrue(sw.size() == 0);
		assertTrue(sw.getState() == Sweeper.SweeperState.NEW);
		sw.open();
		testIllegalStateException(new Runnable() { public void run() { sw.open(); } }, "opening twice");
		assertTrue(sw.getState() == Sweeper.SweeperState.OPEN);
	}

	@SuppressWarnings("unchecked")
	public void testCloseClear(final Sweeper sw) {
		assertTrue(sw.getState() == Sweeper.SweeperState.OPEN);
		sw.acquire(testobj[0]);
		sw.acquire(testobj[1]);
		sw.acquire(testobj[2]);
		sw.release(testobj[0]);
		sw.release(testobj[1]);
		sw.release(testobj[2]);
		assertTrue(sw.getState() == Sweeper.SweeperState.OPEN);
		sw.acquire(testobj[0]);
		sw.acquire(testobj[1]);
		sw.acquire(testobj[2]);
		sw.release(testobj[0]);
		sw.release(testobj[1]);
		sw.release(testobj[2]);
		assertTrue(sw.getState() == Sweeper.SweeperState.OPEN);
		sw.close();
		assertTrue(sw.getState() == Sweeper.SweeperState.CLEARED);
	}

	@SuppressWarnings("unchecked")
	public <S extends Sweeper & Iterable> void testCloseClearIterable(final S sw) {
		assertTrue(sw.getState() == Sweeper.SweeperState.OPEN);
		sw.acquire(testobj[0]);
		sw.acquire(testobj[1]);
		sw.acquire(testobj[2]);
		for (Iterator it = sw.iterator(); it.hasNext();) { it.next(); it.remove(); }
		assertTrue(sw.getState() == Sweeper.SweeperState.OPEN);
		sw.acquire(testobj[0]);
		sw.acquire(testobj[1]);
		sw.acquire(testobj[2]);
		for (Iterator it = sw.iterator(); it.hasNext();) { it.next(); it.remove(); }
		assertTrue(sw.getState() == Sweeper.SweeperState.OPEN);
		sw.close();
		assertTrue(sw.getState() == Sweeper.SweeperState.CLEARED);
	}

	@SuppressWarnings("unchecked")
	public void testReleaseClear(final Sweeper sw) {
		assertTrue(sw.getState() == Sweeper.SweeperState.OPEN);
		sw.acquire(testobj[0]);
		sw.acquire(testobj[1]);
		sw.acquire(testobj[2]);
		sw.release(testobj[0]);
		sw.release(testobj[1]);
		sw.release(testobj[2]);
		assertTrue(sw.getState() == Sweeper.SweeperState.OPEN);
		sw.acquire(testobj[0]);
		sw.acquire(testobj[1]);
		sw.acquire(testobj[2]);
		assertTrue(sw.getState() == Sweeper.SweeperState.OPEN);
		sw.close();
		assertTrue(sw.getState() == Sweeper.SweeperState.CLOSED);
		testAfterClose(sw);
		sw.release(testobj[0]);
		sw.release(testobj[1]);
		sw.release(testobj[2]);
		assertTrue(sw.getState() == Sweeper.SweeperState.CLEARED);
	}

	@SuppressWarnings("unchecked")
	public <S extends Sweeper & Iterable> void testReleaseClearIterable(final S sw) {
		assertTrue(sw.getState() == Sweeper.SweeperState.OPEN);
		sw.acquire(testobj[0]);
		sw.acquire(testobj[1]);
		sw.acquire(testobj[2]);
		for (Iterator it = sw.iterator(); it.hasNext();) { it.next(); it.remove(); }
		assertTrue(sw.getState() == Sweeper.SweeperState.OPEN);
		sw.acquire(testobj[0]);
		sw.acquire(testobj[1]);
		sw.acquire(testobj[2]);
		sw.close();
		assertTrue(sw.getState() == Sweeper.SweeperState.CLOSED);
		testAfterClose(sw);
		for (Iterator it = sw.iterator(); it.hasNext();) { it.next(); it.remove(); }
		assertTrue(sw.getState() == Sweeper.SweeperState.CLEARED);
	}

	@SuppressWarnings("unchecked")
	public void testAfterClose(final Sweeper sw) {
		assertTrue(sw.getState() == Sweeper.SweeperState.CLOSED);
		int s = sw.size();
		assertTrue(s > 0);
		testIllegalStateException(new Runnable() { public void run() { sw.open(); } }, "opening when closed");
		testIllegalStateException(new Runnable() { public void run() { sw.acquire(testobj[0]); } }, "acquiring when closed");
		assertTrue(sw.getState() == Sweeper.SweeperState.CLOSED);
		assertTrue(sw.size() == s);
	}

	@SuppressWarnings("unchecked")
	public void testAfterClear(final Sweeper sw) {
		assertTrue(sw.getState() == Sweeper.SweeperState.CLEARED);
		testIllegalStateException(new Runnable() { public void run() { sw.open(); } }, "opening when cleared");
		testIllegalStateException(new Runnable() { public void run() { sw.acquire(testobj[0]); } }, "acquiring when cleared");
		testIllegalStateException(new Runnable() { public void run() { sw.acquire(testobj[1]); } }, "acquiring when cleared");
		testIllegalStateException(new Runnable() { public void run() { sw.acquire(testobj[2]); } }, "acquiring when cleared");
		testIllegalStateException(new Runnable() { public void run() { sw.release(testobj[0]); } }, "releasing when cleared");
		testIllegalStateException(new Runnable() { public void run() { sw.close(); } }, "closing when cleared");
		assertTrue(sw.size() == 0);
		assertTrue(sw.getState() == Sweeper.SweeperState.CLEARED);
	}

	public void testIllegalStateException(Runnable r, String s) {
		try {
			r.run();
			fail("Failed to throw correct exception when: " + s);
		} catch (IllegalStateException e) {
			/* correct */
		} catch (RuntimeException e) {
			fail("Failed to throw correct exception when: " + s);
		}
	}

}
