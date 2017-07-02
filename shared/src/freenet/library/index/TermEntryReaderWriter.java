/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.index;


import freenet.library.io.DataFormatException;
import freenet.library.io.ObjectStreamReader;
import freenet.library.io.ObjectStreamWriter;
import freenet.library.io.FreenetURI;
import freenet.copied.Base64;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URLEncoder;

import org.yaml.snakeyaml.reader.ReaderException;

/**
** Reads and writes {@link TermEntry}s in binary form, for performance.
**
** This needs to be able to read FreenetURI:s as generated from Spider but
** everything written uses the simpler String-version of the FreenetURI.
** To distringuish between them, when using the String-version, it is always
** preceeded with a short that is 0.
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

	static final byte CHK = 1;
	static final byte SSK = 2;
	static final byte KSK = 3;
	static final byte USK = 4;
	static final short ClientCHK_EXTRA_LENGTH = 5;
	static final short ClientSSK_EXTRA_LENGTH = 5;

	/**
	 * This is to be able to read the data created by Spider.
	 */
	private String readFreenetURI(DataInputStream dis1) throws IOException {
	    int len = dis1.readShort();
	    if (len == 0) {
		// This is the new format.
		return dis1.readUTF();
	    }
	    byte[] buf = new byte[len];
	    dis1.readFully(buf);
	    ByteArrayInputStream bais = new ByteArrayInputStream(buf);
	    DataInputStream dis = new DataInputStream(bais);
	    byte type = dis.readByte();
	    String keyType;
	    if(type == CHK)
		keyType = "CHK";
	    else if(type == SSK)
		keyType = "SSK";
	    else if(type == KSK)
		keyType = "KSK";
	    else
		throw new IOException("Unrecognized FreenetURI type " + type);
	    byte[] routingKey = null;
	    byte[] cryptoKey = null;
	    byte[] extra = null;
	    if((type == CHK) || (type == SSK)) {
		// routingKey is a hash, so is exactly 32 bytes
		routingKey = new byte[32];
		dis.readFully(routingKey);
		// cryptoKey is a 256 bit AES key, so likewise
		cryptoKey = new byte[32];
		dis.readFully(cryptoKey);
		// Number of bytes of extra depends on key type
		int extraLen;
		extraLen = (type == CHK ? ClientCHK_EXTRA_LENGTH : ClientSSK_EXTRA_LENGTH);
		extra = new byte[extraLen];
		dis.readFully(extra);
	    }

	    String docName = null;
	    if(type != CHK)
		docName = dis.readUTF();
	    int count = dis.readInt();
	    String[] metaStrings = new String[count];
	    for(int i = 0; i < metaStrings.length; i++)
		metaStrings[i] = dis.readUTF();

	    StringBuilder b = new StringBuilder();

	    b.append(keyType).append('@');
	    if(!"KSK".equals(keyType)) {
		if(routingKey != null)
		    b.append(Base64.encode(routingKey));
		if(cryptoKey != null)
		    b.append(',').append(Base64.encode(cryptoKey));
		if(extra != null)
		    b.append(',').append(Base64.encode(extra));
		if(docName != null)
		    b.append('/');
	    }

	    if(docName != null)
		b.append(URLEncoder.encode(docName, "UTF-8"));

	    //if(keyType.equals("USK")) {
	    // b.append('/');
	    // b.append(suggestedEdition);
	    // }

	    for(int i = 0; i < metaStrings.length; i++) {
		b.append('/').append(URLEncoder.encode(metaStrings[i], "UTF-8"));
	    }
	    return b.toString();
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
				if (!isValid(title)) {
					title = clean(title);
				}
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

    final static Pattern NON_PRINTABLE = Pattern
            .compile("[^\t\n\r\u0020-\u007E\u0085\u00A0-\uD7FF\uE000-\uFFFC]");

    private boolean isValid(String str) {
        return !NON_PRINTABLE.matcher(str).find();
	}
    
    private String clean(String str) {
        return NON_PRINTABLE.matcher(str).replaceAll("");
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
			enn.getPage().writeFullBinaryKeyWithLength(dos);
			int size = enn.hasPositions() ? enn.positionsSize() : 0;
			if(enn.title == null)
				dos.writeInt(size);
			else {
				if (!isValid(enn.title)) {
					throw new RuntimeException("Invalid title " + enn.title);
				}
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
