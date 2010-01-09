/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import junit.framework.TestCase;

import java.util.Random;
import java.util.Arrays;

/**
** @author infinity0
*/
public class IntegersTest extends TestCase {

	public Random rnd = new Random();

	private int[] totals = {
		10,
		6,
		10
	};
	private int[] nums = {
		5,
		4,
		6
	};
	private int[][] shares = {
		{2,2,2,2,2},
		{1,2,1,2},
		{2,1,2,2,1,2}
	};

	public void testAllocateEvenlyPredefined()  {
		for (int i=0; i<totals.length; ++i) {
			int[] testsh = shares[i];
			int j=0;
			for (Integer ii: Integers.allocateEvenly(totals[i], nums[i])) {
				assertTrue(ii == testsh[j]);
				++j;
			}
			assertTrue(j == testsh.length);
		}
	}

	public void testAllocateEvenlyRandom() {
		for (int i=0; i<0x10; ++i) {
			int total = rnd.nextInt(0x4000)+1;
			int num = rnd.nextInt(total)+1;
			int share[] = new int[num];

			Iterable<Integer> it = Integers.allocateEvenly(total, num);
			//System.out.println(it);

			int j=0;
			for (Integer ii: it) {
				share[j++] = ii;
			}

			int k = total / num;
			int r = total % num;
			Arrays.sort(share);
			assertTrue(share[0] == k);
			assertTrue(share[num-r-1] == k);
			if (r > 0) {
				assertTrue(share[num-r] == k+1);
				assertTrue(share[num-1] == k+1);
			}
		}
	}

}
