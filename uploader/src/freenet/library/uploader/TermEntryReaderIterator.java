package freenet.library.uploader;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import freenet.library.index.TermEntry;
import freenet.library.index.TermEntryReaderWriter;

class TermEntryReaderIterator implements Iterable<TermEntry> {
    private DataInputStream is;
    private Map<String, String> header;

    public TermEntryReaderIterator(DataInputStream s) {
        is = s;
        header = new HashMap<String, String>(5);
        String line = "";
        int laps = 0;
        do {
            try {
				line = is.readLine();
	            String[] parts = line.split("=", 2);
	            if (parts.length >= 2) {
	            	header.put(parts[0], parts[1]);
	            }
			} catch (IOException e) {
				System.err.println("Error: Not closed header.");
				System.exit(1);
			}
            if (laps > 100) {
                System.err.println("Error: Cannot get out of file header.");
                System.exit(1);
            }
        } while (!"End".equals(line));
    }
    
    @Override
    public Iterator<TermEntry> iterator() {
        return new Iterator<TermEntry>() {
        	TermEntry lastRead = null;

            @Override
            public boolean hasNext() {
            	if (lastRead != null) {
            		return true;
            	}
            	lastRead = next();
            	return lastRead != null;
            }

            @Override
            public TermEntry next() {
            	if (lastRead != null) {
            		TermEntry t = lastRead;
            		lastRead = null;
            		return t;
            	}
                try {
                    return TermEntryReaderWriter.getInstance().readObject(is);
                } catch (IOException e) {
                    return null;
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }
    
    Map<String, String> getHeader() {
    	return Collections.unmodifiableMap(header);
    }
}