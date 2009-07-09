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

import java.util.Random;
import java.util.TreeSet;
import java.util.SortedSet;

/**
** @author infinity0
*/
public class IndexTest extends TestCase {

	public String rndStr() {
		return java.util.UUID.randomUUID().toString();
	}

	public void testBasic() {
		SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>> test = new
		SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>(new Token(), 512);

		IndexFileSerialiser f = new IndexFileSerialiser();
		test.setSerialiser(f.s, f.sv);

		Random rand = new Random();

		for (int i=0; i<256; ++i) {
			String key = rndStr().substring(0,8);
			SortedSet<TokenEntry> entries = new TreeSet<TokenEntry>();
			int n = rand.nextInt(16) + 16;

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

		test.deflate();
		PushTask task = new PushTask(test);
		f.s.push(task);
		System.out.println("Entries generated");

		PullTask tasq = new PullTask(task.meta);
		f.s.pull(tasq);
		test.inflate();
		System.out.println("Entries re-loaded from disk");

		int s = test.size();
	}

}
