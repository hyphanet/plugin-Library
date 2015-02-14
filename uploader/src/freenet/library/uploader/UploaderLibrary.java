/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.uploader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.UnsupportedOperationException;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import freenet.library.ArchiverFactory;
import freenet.library.io.ObjectStreamReader;
import freenet.library.io.ObjectStreamWriter;
import freenet.library.io.serial.LiveArchiver;
import freenet.library.uploader.archiver.FcpReader;
import freenet.library.uploader.archiver.FcpWriter;
import freenet.library.util.exec.SimpleProgress;
import freenet.library.util.exec.TaskAbortException;

import net.pterodactylus.fcp.FcpConnection;
import net.pterodactylus.fcp.Priority;



/**
 * Library class is the api for others to use search facilities, it is used by the interfaces
 * @author MikeB
 */
final public class UploaderLibrary implements ArchiverFactory {

    public static final String BOOKMARK_PREFIX = "bookmark:";
    public static final String DEFAULT_INDEX_SITE = BOOKMARK_PREFIX + "liberty-of-information" + " " + BOOKMARK_PREFIX + "free-market-free-people" + " " +
        BOOKMARK_PREFIX + "gotcha" + " " + BOOKMARK_PREFIX + "wanna" + " " + BOOKMARK_PREFIX + "wanna.old" + " " + BOOKMARK_PREFIX + "gogo";
    private static int version = 36;
    public static final String plugName = "Library " + getVersion();

    public static String getPlugName() {
        return plugName;
    }

    public static long getVersion() {
        return version;
    }

    /**
     ** Library singleton.
     */
    private static UploaderLibrary lib;
    public static UploaderLibrary getInstance() {
        if (lib == null) {
            lib = new UploaderLibrary();
        }
        return lib;
    }
        
    public static FcpConnection fcpConnection;
        
    public synchronized static void init(FcpConnection connection) {
        fcpConnection = connection;
    }


    public static String convertToHex(byte[] data) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            int halfbyte = (data[i] >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                if ((0 <= halfbyte) && (halfbyte <= 9))
                    buf.append((char) ('0' + halfbyte));
                else
                    buf.append((char) ('a' + (halfbyte - 10)));
                halfbyte = data[i] & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }

    //this function will return the String representation of the MD5 hash for the input string
    public static String MD5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = text.getBytes("UTF-8");
            md.update(b, 0, b.length);
            byte[] md5hash = md.digest();
            return convertToHex(md5hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T, S extends ObjectStreamWriter & ObjectStreamReader> 
                         LiveArchiver<T, SimpleProgress> 
                         newArchiver(S rw, String mime, int size, 
                                     freenet.library.Priority priorityLevel) {
        if (rw instanceof ObjectStreamWriter) {
            return new FcpReader<T>(new File(UploaderPaths.LIBRARY_CACHE),
            		rw, mime, size, priorityLevel);
        } else if (rw instanceof ObjectStreamReader) {
            return new FcpWriter<T>(fcpConnection, rw, mime, size, priorityLevel);
        } else {
            // This is a Shouldn't happen.
            throw new IllegalArgumentException("Unknown reader/writer: " + rw);
        }
    }

    @Override
    public <T, S extends ObjectStreamWriter & ObjectStreamReader> 
                         LiveArchiver<T, SimpleProgress> 
                         newArchiver(S rw, String mime, int size,
                                     LiveArchiver<T, SimpleProgress> archiver) {
        freenet.library.Priority priorityLevel = freenet.library.Priority.Bulk;
        /*
        if (archiver != null &&
            archiver isinstance ??) {
            priorityLevel = ((??) archiver).getPriorityLevel();
        }
        */
        return newArchiver(rw, mime, size, priorityLevel);
    }
}
