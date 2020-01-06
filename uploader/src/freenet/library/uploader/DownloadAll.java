/*
 */

/*
 * Log levels used:
 * None/Warning: Serious events and small problems.
 * FINE: Stats for fetches and overview of contents of fetched keys. Minor events.
 * FINER: Queue additions, length, ETA, rotations.
 * FINEST: Really minor events.
 */

package freenet.library.uploader;

import java.net.MalformedURLException;

import freenet.library.io.FreenetURI;

/**
 * Class to download the entire index.
 */
public class DownloadAll {
    public static void main(String[] argv) {
    	if (argv.length > 1 && argv[0].equals("--move")) {
    		try {
				new FetchAllOnce(new FreenetURI(argv[1])).doMove();
			} catch (MalformedURLException e) {
				e.printStackTrace();
				System.exit(2);
			}
    	} else {
    		try {
				new FetchAllOnce(new FreenetURI(argv[0])).doDownload();
			} catch (MalformedURLException e) {
				e.printStackTrace();
				System.exit(2);
			}
    	}
    }
}
