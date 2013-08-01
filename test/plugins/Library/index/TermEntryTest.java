/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import junit.framework.TestCase;

import plugins.Library.io.serial.Serialiser.*;
import plugins.Library.io.serial.FileArchiver;
import plugins.Library.util.exec.TaskAbortException;
import plugins.Library.io.serial.Packer;

import plugins.Library.io.YamlReaderWriter;

import freenet.keys.FreenetURI;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import java.net.MalformedURLException;
import java.io.*;

/**
** @author infinity0
*/
public class TermEntryTest extends TestCase {

	final static TermTermEntry w  = new TermTermEntry("test", 0.8f, "lol");
	final static TermIndexEntry x;
	final static TermPageEntry z;
	final static TermPageEntry v;
	static {
		try {
			x = new TermIndexEntry("test", 0.8f, new FreenetURI("CHK@MIh5-viJQrPkde5gmRZzqjBrqOuh~Wbjg02uuXJUzgM,rKDavdwyVF9Z0sf5BMRZsXj7yiWPFUuewoe0CPesvXE,AAIC--8"));
			z = new TermPageEntry("lol", 0.8f, new FreenetURI("CHK@9eDo5QWLQcgSuDh1meTm96R4oE7zpoMBuV15jLiZTps,3HJaHbdW~-MtC6YsSkKn6I0DTG9Z1gKDGgtENhHx82I,AAIC--8"), null);
			v = new TermPageEntry("lol", 0.8f, new FreenetURI("CHK@9eDo5QWLQcgSuDh1meTm96R4oE7zpoMBuV15jLiZTps,3HJaHbdW~-MtC6YsSkKn6I0DTG9Z1gKDGgtENhHx82I,AAIC--8"), "title", null);
		} catch (MalformedURLException e) {
			throw new AssertionError();
		}
	}
	final static TermTermEntry y  = new TermTermEntry("test", 0.8f, "lol2");

	public void testBasic() throws TaskAbortException {
		FileArchiver<Map<String, Object>> ym = new FileArchiver<Map<String, Object>>(new YamlReaderWriter(), "test", null, ".yml", null);

		Map<String, Object> map = new HashMap<String, Object>();

		List<TermEntry> l = new ArrayList<TermEntry>();
		l.add(w);
		l.add(w);
		l.add(x);
		l.add(y);
		l.add(z);
		map.put("test", l);
		try {
			map.put("test2", new Packer.BinInfo(new FreenetURI("http://127.0.0.1:8888/CHK@WtWIvOZXLVZkmDrY5929RxOZ-woRpRoMgE8rdZaQ0VU,rxH~D9VvOOuA7bCnVuzq~eux77i9RR3lsdwVHUgXoOY,AAIC--8/Library.jar"), 123));
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

	public void testBinaryReadWrite() throws IOException, TaskAbortException {
		TermEntryReaderWriter rw = TermEntryReaderWriter.getInstance();
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		DataOutputStream oo = new DataOutputStream(bo);
		rw.writeObject(v, oo);
		rw.writeObject(w, oo);
		rw.writeObject(x, oo);
		rw.writeObject(y, oo);
		rw.writeObject(z, oo);
		oo.close();
		ByteArrayInputStream bi = new ByteArrayInputStream(bo.toByteArray());
		DataInputStream oi = new DataInputStream(bi);
		TermEntry v1 = rw.readObject(oi);
		TermEntry w1 = rw.readObject(oi);
		TermEntry x1 = rw.readObject(oi);
		TermEntry y1 = rw.readObject(oi);
		TermEntry z1 = rw.readObject(oi);
		oi.close();
		assertEqualButNotIdentical(v, v1);
		assertEqualButNotIdentical(w, w1);
		assertEqualButNotIdentical(x, x1); // this will fail before fred@a6e73dbbaa7840bd20d5e3fb95cd2c678a106e85
		assertEqualButNotIdentical(y, y1);
		assertEqualButNotIdentical(z, z1);
	}

	public static void assertEqualButNotIdentical(Object a, Object b) {
		assertTrue(a != b);
		assertTrue(a.equals(b));
		assertTrue(a.hashCode() == b.hashCode());
	}

}
