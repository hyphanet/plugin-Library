/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.io.DataFormatException;
import plugins.Library.io.ObjectStreamReader;
import plugins.Library.io.ObjectStreamWriter;

import freenet.keys.FreenetURI;

import java.util.Map;
import java.util.HashMap;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
** Reads and writes {@link TermEntry}s in binary form, for performance.
**
** @author infinity0
*/
public class TermEntryReaderWriter implements ObjectStreamReader<TermEntry>, ObjectStreamWriter<TermEntry> {

	final private static TermEntryReaderWriter instance = new TermEntryReaderWriter();

	protected TermEntryReaderWriter() {}

	public static TermEntryReaderWriter getInstance() {
		return instance;
	}

	/*@Override**/ public TermEntry readObject(InputStream is) throws IOException {
		return readObject(new DataInputStream(is));
	}

	public TermEntry readObject(DataInputStream dis) throws IOException {
		long svuid = dis.readLong();
		if (svuid != TermEntry.serialVersionUID) {
			throw new DataFormatException("Incorrect serialVersionUID", null, svuid);
		}
		int type = dis.readInt();
		String subj = dis.readUTF();
		float rel = dis.readFloat();
		TermEntry.EntryType[] types = TermEntry.EntryType.values();
		if (type < 0 || type >= types.length) {
			throw new DataFormatException("Unrecognised entry type", null, type);
		}
		switch (types[type]) {
		case TERM:
			return new TermTermEntry(subj, rel, dis.readUTF());
		case INDEX:
			return new TermIndexEntry(subj, rel, FreenetURI.readFullBinaryKeyWithLength(dis));
		case PAGE:
			FreenetURI page = FreenetURI.readFullBinaryKeyWithLength(dis);
			int size = dis.readInt();
			String title = null;
			if (size < 0) {
				title = dis.readUTF();
				size = ~size;
			}
			Map<Integer, String> pos = new HashMap<Integer, String>(size<<1);
			for (int i=0; i<size; ++i) {
				int index = dis.readInt();
				String val = dis.readUTF();
				pos.put(index, "".equals(val) ? null : val);
			}
			return new TermPageEntry(subj, rel, page, title, pos);
		default:
			throw new AssertionError();
		}
	}

	/*@Override**/ public void writeObject(TermEntry en, OutputStream os) throws IOException {
		writeObject(en, new DataOutputStream(os));
	}

	public void writeObject(TermEntry en, DataOutputStream dos) throws IOException {
		dos.writeLong(TermEntry.serialVersionUID);
		TermEntry.EntryType type = en.entryType();
		dos.writeInt(type.ordinal());
		dos.writeUTF(en.subj);
		dos.writeFloat(en.rel);
		switch (type) {
		case TERM:
			dos.writeUTF(((TermTermEntry)en).term);
			return;
		case INDEX:
			((TermIndexEntry)en).index.writeFullBinaryKeyWithLength(dos);
			return;
		case PAGE:
			TermPageEntry enn = (TermPageEntry)en;
			enn.page.writeFullBinaryKeyWithLength(dos);
			int size = enn.hasPositions() ? enn.positionsSize() : 0;
			if(enn.title == null)
				dos.writeInt(size);
			else {
				dos.writeInt(~size); // invert bits to signify title is set
				dos.writeUTF(enn.title);
			}
			if(size != 0) {
				if(enn.hasFragments()) {
					for(Map.Entry<Integer, String> p : enn.positionsMap().entrySet()) {
						dos.writeInt(p.getKey());
						if(p.getValue() == null)
							dos.writeUTF("");
						else
							dos.writeUTF(p.getValue());
					}
				} else {
					for(int x : enn.positionsRaw()) {
						dos.writeInt(x);
						dos.writeUTF("");
					}
				}
			}
			return;
		}
	}

}
