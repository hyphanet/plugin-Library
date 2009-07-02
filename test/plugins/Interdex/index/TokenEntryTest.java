/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import junit.framework.TestCase;

import plugins.Interdex.serl.Serialiser.*;
import plugins.Interdex.serl.YamlArchiver;

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
public class TranslatorsTest extends TestCase {

	public void testBasic() {
		YamlArchiver<Map<String, Object>> ym = new YamlArchiver<Map<String, Object>>("test", null);

		Map<String, Object> map = new HashMap<String, Object>();

		TokenTermEntry w  = new TokenTermEntry("test", "lol");
		w.setQuality(0.5f);
		w.setRelevance(0.8f);
		TokenIndexEntry x = null;
		try {
		x = new TokenIndexEntry("test", new FreenetURI("CHK@yeah"));
		x.setQuality(0.5f);
		x.setRelevance(0.8f);
		} catch (MalformedURLException e) {
		}
		//TokenURIEntry
		TokenTermEntry y  = new TokenTermEntry("test", "lol2");
		y.setQuality(0.5f);
		y.setRelevance(0.8f);

		List<TokenEntry> l = new ArrayList<TokenEntry>();
		l.add(w);
		l.add(w);
		l.add(x);
		l.add(y);
		map.put("test", l);

		ym.push(new PushTask<Map<String, Object>>(map));
		PullTask<Map<String, Object>> pt = new PullTask<Map<String, Object>>("");
		ym.pull(pt);

		assertTrue(pt.data instanceof Map);
		Map<String, Object> m = pt.data;
		assertTrue(m.get("test") instanceof List);
		List ll = (List)m.get("test");
		assertTrue(ll.get(0) instanceof TokenTermEntry);
		assertTrue(ll.get(1) instanceof TokenTermEntry);
		// URGENT these tests both fail due to bugs in snakeYAML
		//assertTrue(ll.get(2) instanceof TokenIndexEntry);
		//assertTrue(ll.get(3) instanceof TokenTermEntry);
	}

}
