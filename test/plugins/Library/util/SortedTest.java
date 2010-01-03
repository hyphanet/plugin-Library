/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import junit.framework.TestCase;

import plugins.Library.util.func.Tuples.*;

import java.util.Random;
import java.util.Iterator;
import java.util.Arrays;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

/**
** @author infinity0
*/
public class SortedTest extends TestCase {

	public Random rnd = new Random();

	private Integer[] sepT = { 8, 16, 24, 32 };
	private Integer[][] subjT = {
		{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},
		{19,24,25},
		{17,19,21},
		{16,24},
		{4,12,20,28,36},
		{12,20,28},
		{4,8,12,16,20,24,28,32,36}
	};
	private Integer[][][] resT = {
		{{1,7},{9,15}},
		{{19,19},{25,25}},
		{{17,21}},
		{},
		{{4,4},{12,12},{20,20},{28,28},{36,36}},
		{{12,12},{20,20},{28,28}},
		{{4,4},{12,12},{20,20},{28,28},{36,36}},
	};
	private Integer[][] fsepT = {
		{8},
		{24},
		{},
		{16,24},
		{},
		{},
		{8,16,24,32}
	};

	public void testSplitPredefined()  {
		SortedSet<Integer> sep = new TreeSet<Integer>(Arrays.asList(sepT));

		for (int i=0; i<subjT.length; ++i) {
			SortedSet<Integer> fsep = new TreeSet<Integer>();
			Collection<$2<Integer, Integer>> res = Sorted.split(new TreeSet<Integer>(Arrays.asList(subjT[i])), sep, fsep, Sorted.Inclusivity.NONE);

			Iterator<$2<Integer, Integer>> rit = res.iterator();
			for (int j=0; j<resT[i].length || rit.hasNext(); ++j) {
				$2<Integer, Integer> pair = rit.next();
				assertTrue(pair._0.equals(resT[i][j][0]));
				assertTrue(pair._1.equals(resT[i][j][1]));
			}
			assertFalse(rit.hasNext());

			Iterator<Integer> fit = fsep.iterator();
			for (int j=0; j<fsepT[i].length || fit.hasNext(); ++j) {
				assertTrue(fit.next() == fsepT[i][j]);
			}
			assertFalse(fit.hasNext());
		}
	}

	public void testSplitEmpty() {
		//TODO
	}

	public void testSplitRandom() {
		//TODO
	}

}
