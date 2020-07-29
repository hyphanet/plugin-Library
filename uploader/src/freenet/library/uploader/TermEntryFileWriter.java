package freenet.library.uploader;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import plugins.Library.index.TermEntry;
import plugins.Library.index.TermEntryReaderWriter;

class TermEntryFileWriter {
    private DataOutputStream os;
    int counter;

    public TermEntryFileWriter(Map<String, String> params,
    		File file) {
    	counter = 0;
        try {
			os = new DataOutputStream(new FileOutputStream(file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
			return;
		}
        try {
	        for (Entry<String, String> entry : params.entrySet()) {
	        	os.writeBytes(entry.getKey() + "=" + entry.getValue() + "\n");
	        }
	        os.writeBytes("End\n");
        } catch (IOException e) {
        	e.printStackTrace();
			System.exit(1);
        }
    }
    
    void write(TermEntry tt) {
        try {
            TermEntryReaderWriter.getInstance().writeObject(tt, os);
        } catch (IOException e) {
        	e.printStackTrace();
			System.exit(1);
        }
    	counter ++;
    }
    
    void close() {
    	try {
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
    	System.out.println("Written new file with " + counter + " entries.");
    }

	public boolean isFull() {
		return counter >= 1000000;
	}
}
