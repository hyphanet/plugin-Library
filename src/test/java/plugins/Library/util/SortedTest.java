/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import junit.framework.TestCase;
import static plugins.Library.util.Generators.rand;

import plugins.Library.util.Sorted.Inclusivity;

import java.util.Random;
import java.util.Iterator;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
** @author infinity0
*/
public class SortedTest extends TestCase {

	final public static int rruns = 0x80;
	final public static int rsizelow = 0x40; // must be > 8

	private Integer[] split_sep = { 8, 16, 24, 32 };
	private Integer[][] split_subj = {
		{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},
		{4,5,6,7,8,9,10,11,12},
		{19,24,25},
		{17,19,21},
		{16,24},
		{4,12,20,28,36},
		{12,20,28},
		{4,8,12,16,20,24,28,32,36}
	};
	private Integer[][][] split_res = {
		{{1,7},{9,15}},
		{{4,7},{9,12}},
		{{19,19},{25,25}},
		{{17,21}},
		{},
		{{4,4},{12,12},{20,20},{28,28},{36,36}},
		{{12,12},{20,20},{28,28}},
		{{4,4},{12,12},{20,20},{28,28},{36,36}},
	};
	private Integer[][] split_fsep = {
		{8},
		{8},
		{24},
		{},
		{16,24},
		{},
		{},
		{8,16,24,32}
	};

	public void testSplitPredefined()  {
		SortedSet<Integer> sep = new TreeSet<Integer>(Arrays.asList(split_sep));

		for (int i=0; i<split_subj.length; ++i) {
			SortedSet<Integer> fsep = new TreeSet<Integer>();
			List<SortedSet<Integer>> res = Sorted.split(new TreeSet<Integer>(Arrays.asList(split_subj[i])), sep, fsep);

			Iterator<SortedSet<Integer>> rit = res.iterator();
			for (int j=0; j<split_res[i].length || rit.hasNext(); ++j) {
				SortedSet<Integer> range = rit.next();
				assertTrue(range.first().equals(split_res[i][j][0]));
				assertTrue(range.last().equals(split_res[i][j][1]));
			}
			assertFalse(rit.hasNext());

			assertTrue(Arrays.deepEquals(split_fsep[i], fsep.toArray()));
			Iterator<Integer> fit = fsep.iterator();
			for (int j=0; j<split_fsep[i].length || fit.hasNext(); ++j) {
				assertTrue(fit.next() == split_fsep[i][j]);
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

	// TODO more tests
	private Integer[][] select_subj = {
		{0,1,2,3,4,5,6,7}
	};

	private Integer[][][] select_res = {
		{{2,5}, {0,4}, {3,7}, {0,7}}
	};

	public void testSelectPredefined() {
		for (int i=0; i<select_subj.length; ++i) {
			Inclusivity[] incs = Inclusivity.values();
			for (int j=0; j<incs.length; ++j) {
				List<Integer> res = Sorted.select(new TreeSet<Integer>(Arrays.asList(select_subj[i])), 2, incs[j]);
				assertTrue(Arrays.deepEquals(select_res[i][j], res.toArray()));
			}
		}
	}

	public void verifySplit(SortedSet<String> subj, SortedSet<String> sep) {
		// FIXME LOW this method assumes comparator is consistent with equals() and
		// there are no null entries
		SortedSet<String> fsep = new TreeSet<String>();
		List<SortedSet<String>> subs = null;
		try {
			subs = Sorted.split(subj, sep, fsep);
		} catch (Error e) {
			System.out.println(subj);
			System.out.println(sep);
			throw e;
		}

		try {
			Iterator<SortedSet<String>> lit = subs.iterator();
			Iterator<String> pit = fsep.iterator();
			Iterator<String> subit = null;
			String cursep = pit.hasNext()? pit.next(): null;
			for (Iterator<String> it = subj.iterator(); it.hasNext(); ) {
				String key = it.next();
				if (subit != null && subit.hasNext()) {
					assertTrue(key.equals(subit.next()));
					continue;
				}
				if (key.equals(cursep)) {
					cursep = pit.hasNext()? pit.next(): null;
					continue;
				}
				assertTrue(lit.hasNext());
				subit = lit.next().iterator();
				assertTrue(subit.hasNext());
				assertTrue(key.equals(subit.next()));
			}
			assertFalse(lit.hasNext());
			assertFalse(pit.hasNext());
		} catch (Error e) {
			System.out.println(subj);
			System.out.println(sep);
			System.out.println(fsep);
			System.out.println(subs);
			throw e;
		}
	}

	protected void randomSelectSplit(int n, int k) {
		SortedSet<String> subj = new TreeSet<String>();
		for (int i=0; i<n; ++i) { subj.add(Generators.rndKey()); }

		SortedSet<String> sep = new TreeSet<String>();
		List<String> candsep = Sorted.select(subj, k);
		assertTrue(candsep.size() == k);
		for (String key: candsep) {
			sep.add((rand.nextInt(2) == 0)? key: Generators.rndKey());
		}
		assertTrue(sep.size() == k);

		verifySplit(subj, sep);
	}

	public void testRandomSelectSplit() {
		int f = 0x10;
		for (int n=0; n<f; ++n) {
			for (int k=0; k<=n; ++k) {
				randomSelectSplit(n, k);
			}
		}
		for (int i=0; i<rruns; ++i) {
			int n = rand.nextInt(rsizelow) + rsizelow;
			randomSelectSplit(n, 0);
			randomSelectSplit(n, 1);
			randomSelectSplit(n, 2);
			randomSelectSplit(n, n);
			randomSelectSplit(n, n-1);
			randomSelectSplit(n, n-2);
			for (int j=0; j<0x10; ++j) {
				int k = rand.nextInt(n-8)+4;
				randomSelectSplit(n, k);
			}
		}
	}

}
