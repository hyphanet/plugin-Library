/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.io.serial;

import junit.framework.TestCase;

import plugins.Library.util.Generators;
import plugins.Library.io.serial.Packer.Bin;

import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.util.HashSet;
import java.util.HashMap;

import freenet.library.io.serial.IterableSerialiser;
import freenet.library.io.serial.Serialiser.*;
import freenet.library.util.SkeletonTreeMap;
import freenet.library.util.exec.TaskAbortException;

/**
** PRIORITY actually write some tests for this...
**
** @author infinity0
*/
public class PackerTest extends TestCase {

	final static public int NODE_MAX = 64;

	final public static Packer<String, HashSet> srl = new Packer<String, HashSet>(
		new IterableSerialiser<Map<String, HashSet>>() {

			public void pull(Iterable<PullTask<Map<String, HashSet>>> t) {}
			public void push(Iterable<PushTask<Map<String, HashSet>>> t) {
				for (PushTask<Map<String, HashSet>> task: t) {
					System.out.print("[");
					for (Map.Entry<String, HashSet> en: task.data.entrySet()) {
						System.out.print(en.getKey() + ": " + en.getValue().size() + ", ");
					}
					System.out.println("]");
				}
			}

			public void pull(PullTask<Map<String, HashSet>> t) {}
			public void push(PushTask<Map<String, HashSet>> t) {}

		},
		new Packer.Scale<HashSet>() {
			@Override public int weigh(HashSet elem) {
				return elem.size();
			}
		},
		NODE_MAX
	);

	protected Map<String, PushTask<HashSet>> generateTasks(int[] sizes) {
		String meta = "dummy metadata";
		Map<String, PushTask<HashSet>> tasks = new HashMap<String, PushTask<HashSet>>();
		for (int size: sizes) {
			HashSet<Integer> hs = new HashSet<Integer>(size>>1);
			for (int i=0; i<size; ++i) {
				hs.add(new Integer(i));
			}
			tasks.put(Generators.rndKey(), new PushTask<HashSet>(hs, meta));
		}
		return tasks;
	}

	public void testBasic() throws TaskAbortException {

//		for (int i=0; i<16; ++i) {
			// do this several times since random UUID changes the order of the task map

			srl.push(generateTasks(new int[]{1,2,3,4,5}), null);
			srl.push(generateTasks(new int[]{1,2,3,4,5,6,7,8,9,10,11,12}), null);
			srl.push(generateTasks(new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}), null);

//		}

	}

	// TODO write some more tests for this...


}
