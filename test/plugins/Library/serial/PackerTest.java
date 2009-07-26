/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

import junit.framework.TestCase;

import plugins.Library.util.Generators;
import plugins.Library.serial.Packer.Bin;
import plugins.Library.serial.Serialiser.*;

import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.util.HashSet;
import java.util.HashMap;

/**
** TODO: toad : unit tests should be reproducible so they can be debugged; it
** can be different each time but it must output a seed to reproduce the exact
** run.
**
** @author infinity0
*/
public class PackerTest extends TestCase {

	final public static CollectionPacker<String, HashSet> srl = new
	CollectionPacker<String, HashSet>(new IterableSerialiser<Map<String, HashSet>>() {
		public void pull(Iterable<PullTask<Map<String, HashSet>>> t) {}
		public void push(Iterable<PushTask<Map<String, HashSet>>> t) {}
		public void pull(PullTask<Map<String, HashSet>> t) {}
		public void push(PushTask<Map<String, HashSet>> t) {}
	}, 256, HashSet.class);

	protected Map<String, PushTask<HashSet>> generateTasks(int[] sizes) {
		String meta = "dummy metadata";
		Map<String, PushTask<HashSet>> tasks = new HashMap<String, PushTask<HashSet>>();
		for (int size: sizes) {
			HashSet<Integer> hs = new HashSet<Integer>(size*2);
			for (int i=0; i<size; ++i) {
				hs.add(new Integer(i));
			}
			tasks.put(Generators.rndStr(), new PushTask<HashSet>(hs, meta));
		}
		return tasks;
	}

	public void testBasic() {
		Bin<HashSet, String>[] bins;

		for (int i=0; i<16; ++i) {
			// do this several times since random UUID changes the order of the task map

			bins = srl.binPack(generateTasks(new int[]{17}));
			assertTrue(bins.length == 1);
			assertTrue(bins[0].filled() == 17);
			assertTrue(bins[0].size() == 1);
			assertTrue(bins[0].firstKey().size() == 17);

			bins = srl.binPack(generateTasks(new int[]{256}));
			assertTrue(bins.length == 1);
			assertTrue(bins[0].filled() == 256);
			assertTrue(bins[0].size() == 1);
			assertTrue(bins[0].firstKey().size() == 256);

			bins = srl.binPack(generateTasks(new int[]{257}));
			assertTrue(bins.length == 2);
			assertTrue(bins[0].filled() == 129);
			assertTrue(bins[0].size() == 1);
			assertTrue(bins[0].firstKey().size() == 129);
			assertTrue(bins[1].filled() == 128);
			assertTrue(bins[1].size() == 1);
			assertTrue(bins[1].firstKey().size() == 128);

			bins = srl.binPack(generateTasks(new int[]{257, 17}));
			assertTrue(bins.length == 2);
			assertTrue(bins[0].filled() == 129);
			assertTrue(bins[0].size() == 1);
			assertTrue(bins[0].firstKey().size() == 129);
			assertTrue(bins[1].filled() == 145);
			assertTrue(bins[1].size() == 2);
			assertTrue(bins[1].firstKey().size() == 128);
			assertTrue(bins[1].lastKey().size() == 17);

			bins = srl.binPack(generateTasks(new int[]{1024}));
			assertTrue(bins.length == 4);
			for (int j=0; j<4; ++j) {
				assertTrue(bins[j].filled() == 256);
				assertTrue(bins[j].size() == 1);
				assertTrue(bins[j].firstKey().size() == 256);
			}

			bins = srl.binPack(generateTasks(new int[]{1027}));
			assertTrue(bins.length == 5);
			for (int j=0; j<2; ++j) {
				assertTrue(bins[j].filled() == 206);
				assertTrue(bins[j].size() == 1);
				assertTrue(bins[j].firstKey().size() == 206);
			}
			for (int j=2; j<5; ++j) {
				assertTrue(bins[j].filled() == 205);
				assertTrue(bins[j].size() == 1);
				assertTrue(bins[j].firstKey().size() == 205);
			}

		}

	}

	// TODO write some more tests for this...


}
