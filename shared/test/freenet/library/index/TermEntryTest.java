/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.index;

import junit.framework.TestCase;

import freenet.library.index.TermEntry;
import freenet.library.index.TermEntryReaderWriter;
import freenet.library.index.TermIndexEntry;
import freenet.library.index.TermPageEntry;
import freenet.library.index.TermTermEntry;
import freenet.library.io.YamlReaderWriter;
import freenet.library.io.serial.FileArchiver;
import freenet.library.io.serial.Packer;
import freenet.library.io.FreenetURI;
import freenet.library.io.serial.Serialiser.*;
import freenet.library.util.exec.TaskAbortException;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.*;
import java.net.MalformedURLException;

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
			throw new AssertionError(e);
		}
	}
	final static TermTermEntry y  = new TermTermEntry("test", 0.8f, "lol2");

	public void testBasic() throws TaskAbortException {
	    File f = new File("TermEntryTest");
	    f.mkdir();
		FileArchiver<Map<String, Object>> ym = new FileArchiver<Map<String, Object>>(new YamlReaderWriter(), "test", null, ".yml", f);

		Map<String, Object> map = new HashMap<String, Object>();

		List<TermEntry> l = new ArrayList<TermEntry>();
		l.add(w);
		l.add(w);
		l.add(x);
		l.add(y);
		l.add(z);
		map.put("test", l);
		map.put("test2", new Packer.BinInfo("CHK@WtWIvOZXLVZkmDrY5929RxOZ-woRpRoMgE8rdZaQ0VU,rxH~D9VvOOuA7bCnVuzq~eux77i9RR3lsdwVHUgXoOY,AAIC--8/Library.jar", 123));

		ym.push(new PushTask<Map<String, Object>>(map));
		PullTask<Map<String, Object>> pt = new PullTask<Map<String, Object>>("");

		ym.pull(pt);

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
		assertTrue(inf.getID() instanceof String);
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
		assertTrue(a + " and " + b + " are identical.", a != b);
		assertTrue(a + " and " + b + " not equal.", a.equals(b));
		assertTrue(a + " and " + b + " not same hashCode.", a.hashCode() == b.hashCode());
	}

	private TermEntry TE(String s) {
		return new TermEntry(s, 0) {
			@Override
			public boolean equalsTarget(TermEntry entry) {
				return false;
			}
			
			@Override
			public EntryType entryType() {
				return null;
			}
		};
	}

	public void testToBeDropped() {
		assertFalse(TE("").toBeDropped());
		assertFalse(TE("1h1").toBeDropped());
		assertTrue(TE("1hh1").toBeDropped());
		assertFalse(TE("r2d2").toBeDropped());
		assertFalse(TE("c3po").toBeDropped());
		assertTrue(TE("a1b2c3d4e5").toBeDropped());
		assertFalse(TE("conventional").toBeDropped());
		assertTrue(TE("abcdef12345fedcba54321aabbee").toBeDropped());
	}
}
