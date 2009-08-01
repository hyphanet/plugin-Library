/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import junit.framework.TestCase;

import plugins.Library.util.*;
import plugins.Library.serial.*;
import plugins.Library.serial.Serialiser.*;
import plugins.Library.index.*;

import freenet.keys.FreenetURI;

import java.util.*;

/**
** TODO most of this code needs to go into WriteableIndex at some point.
**
** @author infinity0
*/
public class BIndexTest extends TestCase {

	final public static boolean disabled = false;

	static {
		//plugins.Library.serial.YamlArchiver.setTestMode();
		ProtoIndex.BTREE_NODE_MIN = 0x40; // DEBUG so we see tree splits
	}

	long time = 0;

	public long timeDiff() {
		long oldtime = time;
		time = System.currentTimeMillis();
		return time - oldtime;
	}

	BIndexSerialiser srl = new BIndexSerialiser();
	ProtoIndex idx;

	Random rand = new Random();

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
			idx = new ProtoIndex(new FreenetURI("CHK@yeah"), "test");
		} catch (java.net.MalformedURLException e) {
			// not gonna happen
		}
		srl.setSerialiserFor(idx);
		timeDiff();
	}

	public void fullInflate() throws TaskAbortException {
		newTestSkeleton();

		int totalentries = 0;

		for (int i=0; i<0x100; ++i) {
			String key = Generators.rndKey();
			SkeletonBTreeSet<TokenEntry> entries = new SkeletonBTreeSet<TokenEntry>(ProtoIndex.BTREE_NODE_MIN);
			srl.setSerialiserFor(entries);

			int n = rand.nextInt(0xF0) + 0x10;
			totalentries += n;

			try {
				for (int j=0; j<n; ++j) {
					TokenEntry e = new TokenURIEntry(key, new FreenetURI("CHK@" + Generators.rndStr().replace('-', 'Z')));
					e.setRelevance((float)Math.random());
					entries.add(e);
				}
			} catch (java.net.MalformedURLException e) {
				// should not happen
				throw new RuntimeException(e);
			}

			idx.ttab.put(key, entries);
		}
		System.out.print(totalentries + " entries generated in " + timeDiff() + " ms, ");

		for (SkeletonBTreeSet<TokenEntry> entries: idx.ttab.values()) {
			entries.deflate();
			assertTrue(entries.isBare());
		}
		idx.ttab.deflate();
		assertTrue(idx.ttab.isBare());
		assertFalse(idx.ttab.isLive());
		PushTask<ProtoIndex> task1 = new PushTask<ProtoIndex>(idx);
		srl.push(task1);

		System.out.print("deflated in " + timeDiff() + " ms, root at " + task1.meta + ", ");

		PullTask<ProtoIndex> task2 = new PullTask<ProtoIndex>(task1.meta);
		srl.pull(task2);
		idx = task2.data;

		idx.ttab.inflate();
		assertTrue(idx.ttab.isLive());
		assertFalse(idx.ttab.isBare());
		System.out.println("inflated in " + timeDiff() + " ms");
	}

	public void testBasicMulti() throws TaskAbortException {
		if (disabled) { return; }
		int n = 4;
		for (int i=0; i<n; ++i) {
			System.out.print(i + "/" + n + ": ");
			fullInflate();
		}
	}

	public void partialInflate() throws TaskAbortException {
		newTestSkeleton();
		int totalentries = 0;

		for (String word: randomWords) {
			SkeletonBTreeSet<TokenEntry> entries = new SkeletonBTreeSet<TokenEntry>(ProtoIndex.BTREE_NODE_MIN);
			srl.setSerialiserFor(entries);

			int n = rand.nextInt(0xF0) + 0x10;
			totalentries += n;

			try {
				for (int j=0; j<n; ++j) {
					TokenEntry e = new TokenURIEntry(word, new FreenetURI("CHK@" + Generators.rndStr().replace('-', 'Z')));
					e.setRelevance((float)Math.random());
					entries.add(e);
				}
			} catch (java.net.MalformedURLException e) {
				// pass
			}

			idx.ttab.put(word, entries);
		}

		System.out.print(totalentries + " entries generated in " + timeDiff() + " ms, ");

		for (SkeletonBTreeSet<TokenEntry> entries: idx.ttab.values()) {
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
		if (disabled) { return; }
		int n = 4;
		for (int i=0; i<n; ++i) {
			System.out.print(i + "/" + n + ": ");
			partialInflate();
		}
	}


	public void testProgress() throws TaskAbortException {
		if (disabled) { return; }
		newTestSkeleton();

		int totalentries = 0;
		int numterms = 0x100;
		int save = rand.nextInt(numterms);
		String sterm = null;

		System.out.println("Generating a shit load of entries to test progress polling. This may take a while...");
		for (int i=0; i<numterms; ++i) {
			String key = Generators.rndStr().substring(0,8);
			if (i == save) { sterm = key; }

			SkeletonBTreeSet<TokenEntry> entries = new SkeletonBTreeSet<TokenEntry>(ProtoIndex.BTREE_NODE_MIN);
			srl.setSerialiserFor(entries);

			int n = rand.nextInt(0x200) + 0x200;
			totalentries += n;

			try {
				for (int j=0; j<n; ++j) {
					TokenEntry e = new TokenURIEntry(key, new FreenetURI("CHK@" + Generators.rndStr().replace('-', 'Z')));
					e.setRelevance((float)Math.random());
					entries.add(e);
				}
			} catch (java.net.MalformedURLException e) {
				// should not happen
				throw new RuntimeException(e);
			}

			idx.ttab.put(key, entries);
		}
		System.out.print(totalentries + " entries generated in " + timeDiff() + " ms, ");

		for (SkeletonBTreeSet<TokenEntry> entries: idx.ttab.values()) {
			entries.deflate();
			assertTrue(entries.isBare());
		}
		idx.ttab.deflate();
		assertTrue(idx.ttab.isBare());
		assertFalse(idx.ttab.isLive());

		System.out.println("deflated in " + timeDiff() + " ms");
		plugins.Library.serial.YamlArchiver.setTestMode();

		Request<Collection<TokenEntry>> rq1 = idx.getTermEntries(sterm);
		Request<Collection<TokenEntry>> rq2 = idx.getTermEntries(sterm);
		Request<Collection<TokenEntry>> rq3 = idx.getTermEntries(sterm);

		assertTrue(rq1 == rq2);
		assertTrue(rq2 == rq3);
		assertTrue(rq3 == rq1);

		while (rq1.getResult() == null) {
			System.out.println(rq1.getCurrentStage() + ": " + rq1.getCurrentStatus());
			try { Thread.sleep(1000); } catch (InterruptedException x) { }
		}

	}


}
