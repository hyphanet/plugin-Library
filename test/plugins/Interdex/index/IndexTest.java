/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import junit.framework.TestCase;

import plugins.Interdex.util.*;
import plugins.Interdex.serl.*;
import plugins.Interdex.serl.Serialiser.*;
import plugins.Interdex.index.*;

import freenet.keys.FreenetURI;

import java.util.*;

/**
** @author infinity0
*/
public class IndexTest extends TestCase {

	public String rndStr() {
		return java.util.UUID.randomUUID().toString();
	}

	long time = 0;

	public long timeDiff() {
		long oldtime = time;
		time = System.currentTimeMillis();
		return time - oldtime;
	}

	IterableSerialiser<SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>> srl = new
	IndexFileSerialiser.PrefixTreeMapSerialiser<Token, SortedSet<TokenEntry>>(new IndexFileSerialiser.TokenTranslator());

	MapSerialiser<Token, SortedSet<TokenEntry>> vsrl = new
	IndexFileSerialiser.TokenEntrySerialiser();

	SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>> test;

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

	protected void setUp() {
		test = new SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>(new Token(), 512);
		test.setSerialiser(srl, vsrl);
		timeDiff();
	}

	public void testBasic() {
		int totalentries = 0;

		for (int i=0; i<256; ++i) {
			String key = rndStr().substring(0,8);
			SortedSet<TokenEntry> entries = new TreeSet<TokenEntry>();
			int n = rand.nextInt(16) + 16;
			totalentries += n;

			try {
				for (int j=0; j<n; ++j) {
					TokenEntry e = new TokenURIEntry(key, new FreenetURI("CHK@" + rndStr().replace('-', 'Z')));
					e.setRelevance((float)Math.random());
					entries.add(e);
				}
			} catch (java.net.MalformedURLException e) {
				// pass
			}

			test.put(new Token(key), entries);
		}
		System.out.print(totalentries + " entries generated in " + timeDiff() + " ms, ");

		test.deflate();
		assertTrue(test.isBare());
		assertFalse(test.isLive());
		PushTask<SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>> task = new
		PushTask<SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>>(test);
		srl.push(task);
		System.out.print("deflated in " + timeDiff() + " ms, ");

		PullTask<SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>> tasq = new
		PullTask<SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>>(task.meta);
		srl.pull(tasq);
		test.inflate();
		assertTrue(test.isLive());
		assertFalse(test.isBare());
		System.out.println("inflated in " + timeDiff() + " ms");

	}

	public void testBasic16() {
		for (int i=0; i<4/*16*/; ++i) {
			testBasic();
		}
	}

	public void testPartialInflate() {
		int totalentries = 0;

		for (String word: randomWords) {
			SortedSet<TokenEntry> entries = new TreeSet<TokenEntry>();
			int n = rand.nextInt(16) + 16;
			totalentries += n;

			try {
				for (int j=0; j<n; ++j) {
					TokenEntry e = new TokenURIEntry(word, new FreenetURI("CHK@" + rndStr().replace('-', 'Z')));
					e.setRelevance((float)Math.random());
					entries.add(e);
				}
			} catch (java.net.MalformedURLException e) {
				// pass
			}

			test.put(new Token(word), entries);
		}

		System.out.print(totalentries + " entries generated in " + timeDiff() + " ms, ");

		test.deflate();
		assertTrue(test.isBare());
		assertFalse(test.isLive());
		PushTask<SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>> task = new
		PushTask<SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>>(test);
		srl.push(task);
		System.out.print("deflated in " + timeDiff() + " ms, ");

		PullTask<SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>> tasq = new
		PullTask<SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>>(task.meta);
		srl.pull(tasq);

		for (String s: randomWords) {
			assertFalse(test.isLive());
			Token t = Token.intern(s);
			test.inflate(t);
			test.get(t);
			assertFalse(test.isBare());
		}
		assertTrue(test.isLive());
		assertFalse(test.isBare());
		System.out.println("inflated all terms separately in " + timeDiff() + " ms");

	}

	public void testPartialInflate16() {
		for (int i=0; i<16; ++i) {
			testPartialInflate();
		}
	}


}
