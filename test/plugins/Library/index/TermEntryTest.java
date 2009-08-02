/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import junit.framework.TestCase;

import plugins.Library.serial.Serialiser.*;
import plugins.Library.serial.YamlArchiver;
import plugins.Library.serial.TaskAbortException;
import plugins.Library.serial.Packer;

import freenet.keys.FreenetURI;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import java.net.MalformedURLException;

/**
** @author infinity0
*/
public class TermEntryTest extends TestCase {

	public void testBasic() throws TaskAbortException {
		YamlArchiver<Map<String, Object>> ym = new YamlArchiver<Map<String, Object>>("test", null);

		Map<String, Object> map = new HashMap<String, Object>();

		Token nulltest1 = Token.intern((Token)null);
		Token nulltest2 = Token.intern((String)null);
		assertTrue(nulltest1 == null);
		assertTrue(nulltest2 == null);

		TermTermEntry w  = new TermTermEntry("test", "lol");
		w.setRelevance(0.8f);
		TermIndexEntry x = null;
		TermPageEntry z = null;
		try {
			x = new TermIndexEntry("test", new FreenetURI("CHK@yeah"));
			x.setRelevance(0.8f);
			z = new TermPageEntry("lol", new FreenetURI("CHK@yeah"));
			z.setRelevance(0.8f);
		} catch (MalformedURLException e) {
			// pass
		}
		TermTermEntry y  = new TermTermEntry("test", "lol2");
		y.setRelevance(0.8f);

		List<TermEntry> l = new ArrayList<TermEntry>();
		l.add(w);
		l.add(w);
		l.add(x);
		l.add(y);
		l.add(z);
		map.put("test", l);
		try {
			map.put("test2", new Packer.BinInfo(new FreenetURI("CHK@yeah"), 123));
		} catch (java.net.MalformedURLException e) {
			assert(false);
		}

		ym.push(new PushTask<Map<String, Object>>(map));
		PullTask<Map<String, Object>> pt = new PullTask<Map<String, Object>>("");
		try{
		ym.pull(pt);
		} catch (Exception e) {
			e.printStackTrace();
		}

		assertTrue(pt.data instanceof Map);
		Map<String, Object> m = pt.data;
		assertTrue(m.get("test") instanceof List);
		List ll = (List)m.get("test");
		assertTrue(ll.get(0) instanceof TermTermEntry);
		assertTrue(ll.get(1) == ll.get(0));
		// NOTE these tests fail in snakeYAML 1.2 and below, fixed in hg
		assertTrue(ll.get(2) instanceof TermIndexEntry);
		assertTrue(ll.get(3) instanceof TermTermEntry);

		assertTrue(m.get("test2") instanceof Packer.BinInfo);
		Packer.BinInfo inf = (Packer.BinInfo)m.get("test2");
		assertTrue(inf.getID() instanceof FreenetURI);
	}

}
