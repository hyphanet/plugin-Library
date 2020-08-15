/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import junit.framework.TestCase;
import static plugins.Library.util.Generators.rand;

import plugins.Library.util.*;
import plugins.Library.util.func.*;
import plugins.Library.util.exec.*;
import plugins.Library.io.serial.*;
import plugins.Library.io.serial.Serialiser.*;
import plugins.Library.index.*;

import freenet.keys.FreenetURI;

import java.util.*;
import java.io.*;

/**
** TODO most of this code needs to go into WriteableIndex at some point.
**
** @author infinity0
*/
public class BIndexTest extends TestCase {

	// mininum number of entries in a b-tree node.
	final public static int node_size = 0x04;

	// base number of keys in an index. the final size (after async-update) will be around double this.
	final public static int index_size = 0x80;

	// base number of entries for a key. the actual size (per key) varies between 1x-2x this.
	final public static int entry_size = 0x20;

	final public boolean extensive = Boolean.getBoolean("extensiveTesting");

	final public static int it_full = 4;
	final public static int it_partial = 2;
	final public static boolean fuller = false;

	// TODO HIGH code a test that stores a huge on-disk index with repeated calls to update()
	// this could be done in the same test as the progress test
	final public static boolean disabled_progress = true;

	static {
		ProtoIndex.BTREE_NODE_MIN = node_size;
		System.out.println("ProtoIndex B-tree node_min set to " + ProtoIndex.BTREE_NODE_MIN);
	}

	long time = 0;

	public long timeDiff() {
		long oldtime = time;
		time = System.currentTimeMillis();
		return time - oldtime;
	}
	
	public BIndexTest() throws IOException {
		f = File.createTempFile("tmp", "BindexTest");
		f.mkdir();
		srl = ProtoIndexSerialiser.forIndex(f);
		csrl = ProtoIndexComponentSerialiser.get(ProtoIndexComponentSerialiser.FMT_FILE_LOCAL, srl.getChildSerialiser());
	}
	
	private final File f;
	private final ProtoIndexSerialiser srl; 
	private final ProtoIndexComponentSerialiser csrl;
	private ProtoIndex idx;

	Set<String> randomWords = new HashSet<String>(Arrays.asList(
		"Lorem", "ipsum", "dolor", "sit", "amet,", "consectetur", "adipisicing",
		"elit,", "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore",
		"et", "dolore", "magna", "aliqua.", "Ut", "enim", "ad", "minim",
		"veniam,", "quis", "nostrud", "exercitation", "ullamco", "laboris", "nisi",
		"ut", "aliquip", "ex", "ea", "commodo", "consequat.", "Duis", "aute",
		"irure", "dolor", "in", "reprehenderit", "in", "voluptate", "velit",
		"esse", "cillum", "dolore", "eu", "fugiat", "nulla", "pariatur.",
		"Excepteur", "sint", "occaecat", "cupidatat", "non", "proident,", "sunt",
		"in", "culpa", "qui", "officia", "deserunt", "mollit", "anim", "id", "est",
		"laborum."
	));

	protected void newTestSkeleton() {
		try {
			idx = new ProtoIndex(new FreenetURI("CHK@yeah"), "test", null, null, 0);
		} catch (java.net.MalformedURLException e) {
			assertTrue(false);
		}
		csrl.setSerialiserFor(idx);
		timeDiff();
	}

	protected SkeletonBTreeSet<TermEntry> makeEntryTree() {
		SkeletonBTreeSet<TermEntry> tree = new SkeletonBTreeSet<TermEntry>(ProtoIndex.BTREE_NODE_MIN);
		csrl.setSerialiserFor(tree);
		return tree;
	}

	protected int fillEntrySet(String key, SortedSet<TermEntry> tree) {
		int n = rand.nextInt(entry_size) + entry_size;
		for (int j=0; j<n; ++j) {
			tree.add(Generators.rndEntry(key));
		}
		return n;
	}

	protected int fillRootTree(SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> tree) {
		int total = 0;
		for (int i=0; i<index_size; ++i) {
			String key = Generators.rndKey();
			SkeletonBTreeSet<TermEntry> entries = makeEntryTree();
			total += fillEntrySet(key, entries);
			tree.put(key, entries);
		}
		return total;
	}

	protected SortedSet<String> randomMixset(Set<String> set) {
		SortedSet<String> sub = new TreeSet<String>();
		for (String s: set) {
			sub.add((rand.nextInt(2) == 0)? s: Generators.rndKey());
		}
		return sub;
	}

	public void fullInflateDeflateUpdate() throws TaskAbortException {
		newTestSkeleton();
		int totalentries = fillRootTree(idx.ttab);
		System.out.print(totalentries + " entries generated in " + timeDiff() + " ms, ");

		// make a copy of the tree
		Map<String, SortedSet<TermEntry>> origtrees = new TreeMap<String, SortedSet<TermEntry>>();
		for (Map.Entry<String, SkeletonBTreeSet<TermEntry>> en: idx.ttab.entrySet()) {
			origtrees.put(en.getKey(), new TreeSet<TermEntry>(en.getValue()));
		}
		assertTrue(origtrees.equals(idx.ttab));

		// full deflate
		for (SkeletonBTreeSet<TermEntry> entries: idx.ttab.values()) {
			entries.deflate();
			assertTrue(entries.isBare());
		}
		idx.ttab.deflate();
		assertTrue(idx.ttab.isBare());
		assertFalse(idx.ttab.isLive());
		PushTask<ProtoIndex> task1 = new PushTask<ProtoIndex>(idx);
		srl.push(task1);
		System.out.println("deflated in " + timeDiff() + " ms, root at " + task1.meta + ".");

		if (fuller) {
			// full inflate
			PullTask<ProtoIndex> task2 = new PullTask<ProtoIndex>(task1.meta);
			srl.pull(task2);
			idx = task2.data;
			idx.ttab.inflate();
			assertTrue(idx.ttab.isLive());
			assertFalse(idx.ttab.isBare());
			System.out.print("inflated in " + timeDiff() + " ms, ");

			// full deflate (1)
			for (SkeletonBTreeSet<TermEntry> entries: idx.ttab.values()) {
				// inflating the root tree does not automatically inflate the trees for
				// each entry, so these should already be bare
				assertTrue(entries.isBare());
			}
			idx.ttab.deflate();
			assertTrue(idx.ttab.isBare());
			assertFalse(idx.ttab.isLive());
			PushTask<ProtoIndex> task3 = new PushTask<ProtoIndex>(idx);
			srl.push(task3);
			System.out.println("re-deflated in " + timeDiff() + " ms, root at " + task3.meta + ".");
		}

		// generate new set to merge
		final SortedSet<String> randAdd = randomMixset(origtrees.keySet());
		final Map<String, SortedSet<TermEntry>> newtrees = new HashMap<String, SortedSet<TermEntry>>();
		int entriesadded = 0;
		for (String k: randAdd) {
			SortedSet<TermEntry> set = new TreeSet<TermEntry>();
			entriesadded += fillEntrySet(k, set);
			newtrees.put(k, set);
		}

		// async merge
		Closure<Map.Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException> clo = new
		Closure<Map.Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException>() {
			/*@Override**/ public void invoke(Map.Entry<String, SkeletonBTreeSet<TermEntry>> entry) throws TaskAbortException {
				String key = entry.getKey();
				SkeletonBTreeSet<TermEntry> tree = entry.getValue();
				//System.out.println("handling " + key + ((tree == null)? " (new)":" (old)"));
				if (tree == null) {
					entry.setValue(tree = makeEntryTree());
				}
				assertTrue(tree.isBare());
				tree.update(newtrees.get(key), null);
				assertTrue(tree.isBare());
				//System.out.println("handled " + key);
			}
		};
		assertTrue(idx.ttab.isBare());
		idx.ttab.update(randAdd, null, clo, new TaskAbortExceptionConvertor());
		assertTrue(idx.ttab.isBare());
		PushTask<ProtoIndex> task4 = new PushTask<ProtoIndex>(idx);
		srl.push(task4);
		System.out.print(entriesadded + " entries merged in " + timeDiff() + " ms, root at " + task4.meta + ", ");

		// Iterate it.
		Iterator<String> keys = idx.ttab.keySetAutoDeflate().iterator();
		long count = 0;
		String prev = null;
		while(keys.hasNext()) {
			count++;
			String x = keys.next();
			assertFalse("Inconsistent iteration: Previous was "+prev+" this is "+x, prev != null && x.compareTo(prev) <= 0);
			prev = x;
		}
		assertTrue(count == idx.ttab.size());
		System.out.println("Iterated keys, total is "+count+" size is "+idx.ttab.size());
		assertTrue(idx.ttab.isBare());
		
		// full inflate (2)
		PullTask<ProtoIndex> task5 = new PullTask<ProtoIndex>(task4.meta);
		srl.pull(task5);
		idx = task5.data;
		idx.ttab.inflate();
		assertTrue(idx.ttab.isLive());
		assertFalse(idx.ttab.isBare());
		System.out.println("re-inflated in " + timeDiff() + " ms.");

		// merge added trees into backup, and test against the stored version
		for (Map.Entry<String, SortedSet<TermEntry>> en: newtrees.entrySet()) {
			String key = en.getKey();
			SortedSet<TermEntry> tree = origtrees.get(key);
			if (tree == null) {
				origtrees.put(key, tree = new TreeSet<TermEntry>());
			}
			tree.addAll(en.getValue());
		}
		System.out.println("validating merge. this will take a few minutes, mostly due to Thread.sleep(). just be patient :-)");
		for (SkeletonBTreeSet<TermEntry> entries: idx.ttab.values()) {
			// FIXME HIGH make some way of doing this in parallel, maybe
			entries.inflate();
			assertTrue(entries.isLive());
		}
		assertTrue(origtrees.equals(idx.ttab));
		System.out.println("merge validated in " + timeDiff() + " ms.");

		System.out.println("");

	}

	public void testBasicMulti() throws TaskAbortException {
		if (!extensive) { return; }
		for (int i=0; i<it_full; ++i) {
			System.out.print(i + "/" + it_full + ": ");
			fullInflateDeflateUpdate();
		}
	}

	public void partialInflate() throws TaskAbortException {
		newTestSkeleton();
		int totalentries = 0;

		for (String word: randomWords) {
			SkeletonBTreeSet<TermEntry> entries = new SkeletonBTreeSet<TermEntry>(ProtoIndex.BTREE_NODE_MIN);
			csrl.setSerialiserFor(entries);

			int n = rand.nextInt(0xF0) + 0x10;
			totalentries += n;

			for (int j=0; j<n; ++j) {
				entries.add(Generators.rndEntry(word));
			}
			idx.ttab.put(word, entries);
		}

		System.out.print(totalentries + " entries generated in " + timeDiff() + " ms, ");

		for (SkeletonBTreeSet<TermEntry> entries: idx.ttab.values()) {
			entries.deflate();
			assertTrue(entries.isBare());
		}
		idx.ttab.deflate();
		assertTrue(idx.ttab.isBare());
		assertFalse(idx.ttab.isLive());
		PushTask<ProtoIndex> task = new PushTask<ProtoIndex>(idx);
		srl.push(task);

		System.out.print("deflated in " + timeDiff() + " ms, root at " + task.meta + ", ");

		PullTask<ProtoIndex> tasq = new PullTask<ProtoIndex>(task.meta);
		srl.pull(tasq);

		for (String s: randomWords) {
			//assertFalse(test.isLive()); // might be live if inflate(key) inflates some other keys too
			idx.ttab.inflate(s);
			idx.ttab.get(s);
			assertFalse(idx.ttab.isBare());
		}
		assertTrue(idx.ttab.isLive());
		assertFalse(idx.ttab.isBare());
		System.out.println("inflated all terms separately in " + timeDiff() + " ms");

	}

	public void testPartialInflateMulti() throws TaskAbortException {
		if (!extensive) { return; }
		for (int i=0; i<it_partial; ++i) {
			System.out.print(i + "/" + it_full + ": ");
			partialInflate();
		}
	}

	public void testProgress() throws TaskAbortException {
		if (!extensive) { return; }
		if (disabled_progress) { return; }
		newTestSkeleton();

		int totalentries = 0;
		int numterms = 0x100;
		int save = rand.nextInt(numterms);
		String sterm = null;

		System.out.println("Generating a shit load of entries to test progress polling. This may take a while...");
		for (int i=0; i<numterms; ++i) {
			String key = Generators.rndStr().substring(0,8);
			if (i == save) { sterm = key; }

			SkeletonBTreeSet<TermEntry> entries = new SkeletonBTreeSet<TermEntry>(ProtoIndex.BTREE_NODE_MIN);
			csrl.setSerialiserFor(entries);

			int n = rand.nextInt(0x200) + 0x200;
			totalentries += n;

			for (int j=0; j<n; ++j) {
				entries.add(Generators.rndEntry(key));
			}
			idx.ttab.put(key, entries);
		}
		System.out.print(totalentries + " entries generated in " + timeDiff() + " ms, ");

		for (SkeletonBTreeSet<TermEntry> entries: idx.ttab.values()) {
			entries.deflate();
			assertTrue(entries.isBare());
		}
		idx.ttab.deflate();
		assertTrue(idx.ttab.isBare());
		assertFalse(idx.ttab.isLive());
		PushTask<ProtoIndex> task = new PushTask<ProtoIndex>(idx);
		srl.push(task);

		System.out.print("deflated in " + timeDiff() + " ms, root at " + task.meta + ", ");
		plugins.Library.io.serial.FileArchiver.setTestMode();

		System.out.println("Requesting entries for term " + sterm);
		Execution<Set<TermEntry>> rq1 = idx.getTermEntries(sterm);
		Execution<Set<TermEntry>> rq2 = idx.getTermEntries(sterm);
		Execution<Set<TermEntry>> rq3 = idx.getTermEntries(sterm);

		assertTrue(rq1 == rq2);
		assertTrue(rq2 == rq3);
		assertTrue(rq3 == rq1);

		Set<TermEntry> entries;
		while ((entries = rq1.getResult()) == null) {
			System.out.println(rq1.getStatus());
			try { Thread.sleep(1000); } catch (InterruptedException x) { }
		}

		int count=0;
		for (TermEntry en: entries) {
			++count;
		}
		assertTrue(count == entries.size());
		System.out.println(count + " entries successfully got in " + rq1.getTimeElapsed() + "ms");

	}

}
