/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import junit.framework.TestCase;

import java.util.SortedSet;

/**
** @author infinity0
*/
public class SortedArraySetTest extends TestCase {

	SortedArraySet<Integer> marr = new SortedArraySet<Integer>(new Integer[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15});

	public void testSubsets() {
		assertTrue(marr.first() == 0);
		assertTrue(marr.last() == 15);
		assertTrue(marr.size() == 16);

		SortedSet<Integer> head = marr.headSet(8);
		SortedSet<Integer> tail = marr.tailSet(8);
		SortedSet<Integer> sub = marr.subSet(4, 12);

		assertTrue(head.first() == 0);
		assertTrue(head.last() == 7);
		assertTrue(head.size() == 8);

		assertTrue(tail.first() == 8);
		assertTrue(tail.last() == 15);
		assertTrue(tail.size() == 8);

		assertTrue(sub.first() == 4);
		assertTrue(sub.last() == 11);
		assertTrue(sub.size() == 8);
	}

	public void testNoDuplicates() {
		try {
			SortedArraySet<Integer> arr = new SortedArraySet<Integer>(new Integer[]{0,1,1,2,3,4,5,6,7,8,9,10,11,12,13,14});
			fail("failed to detect an array with duplicates in");
		} catch (RuntimeException e) {
			assertTrue(e instanceof IllegalArgumentException);
		}
	}

}
