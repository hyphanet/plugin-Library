/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 * This is a simpler implementation of the FreenetURI than {link freenet.keys.FreenetURI}.
 * 
 * It has part of the interface in the same way but it is simpler and local to the Library.
 */
public class FreenetURI {
	private String contents;

	public FreenetURI(String uri) throws MalformedURLException {
		contents = uri;
		if (!contents.startsWith("CHK@")) {
			throw new MalformedURLException("Unhandled keytype");
		}
		if (!contents.matches("^.*@.*,.*,AA.*$")) {
			throw new MalformedURLException("Cannot find cryptoKey and routingKey structure");
		}
	}

	public static FreenetURI readFullBinaryKeyWithLength(
			DataInputStream dis) throws IOException {
		int len = dis.readShort();
		byte[] buf = new byte[len];
        dis.readFully(buf);
        return new FreenetURI(new String(buf));
	}

	public FreenetURI intern() {
		return this;
	}

	public void writeFullBinaryKeyWithLength(DataOutputStream dos) throws IOException {
		dos.writeShort(contents.length());
		dos.writeBytes(contents);
	}

	public boolean isUSK()
	throws MalformedURLException {
		// TODO Auto-generated method stub
		throw new RuntimeException("Not implemented yet.");
		// return false;
	}

	public FreenetURI sskForUSK() {
		// TODO Auto-generated method stub
		throw new RuntimeException("Not implemented yet.");
		// return null;
	}

	public boolean isSSKForUSK() {
		// TODO Auto-generated method stub
		throw new RuntimeException("Not implemented yet.");
		// return false;
	}

	public FreenetURI uskForSSK() {
		// TODO Auto-generated method stub
		throw new RuntimeException("Not implemented yet.");
		// return null;
	}

	public long getEdition() {
		// TODO Auto-generated method stub
		throw new RuntimeException("Not implemented yet.");
		// return 0;
	}

	public FreenetURI setMetaString(Object object) {
		// TODO Auto-generated method stub
		throw new RuntimeException("Not implemented yet.");
		// return this;
	}

	public FreenetURI setSuggestedEdition(int i) {
		// TODO Auto-generated method stub
		throw new RuntimeException("Not implemented yet.");
		// return this;
	}

	public String[] getAllMetaStrings() {
		// TODO Auto-generated method stub
		throw new RuntimeException("Not implemented yet.");
		// return null;
	}

	public Object lastMetaString() {
		// TODO Auto-generated method stub
		throw new RuntimeException("Not implemented yet.");
		// return null;
	}

	public Object getDocName() {
		// TODO Auto-generated method stub
		throw new RuntimeException("Not implemented yet.");
		// return null;
	}

	public FreenetURI pushMetaString(String string) {
		// TODO Auto-generated method stub
		throw new RuntimeException("Not implemented yet.");
		// return null;
	}

	@Override
	public int hashCode() {
		return this.getClass().hashCode() ^ contents.hashCode();
	}
	
	@Override
	public String toString() {
		return contents;
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof FreenetURI)) {
			return false;
		}
		return contents.equals(((FreenetURI) o).toString());
	}
}
