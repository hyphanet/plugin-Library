package freenet.library.uploader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import net.pterodactylus.fcp.ClientGet;
import net.pterodactylus.fcp.ClientHello;
import net.pterodactylus.fcp.ClientPut;
import net.pterodactylus.fcp.CloseConnectionDuplicateClientName;
import net.pterodactylus.fcp.FcpAdapter;
import net.pterodactylus.fcp.FcpConnection;
import net.pterodactylus.fcp.FcpMessage;
import net.pterodactylus.fcp.NodeHello;
import freenet.copied.Base64;
import freenet.copied.SHA256;
import freenet.library.Priority;
import freenet.library.io.ObjectStreamReader;
import freenet.library.io.ObjectStreamWriter;
import freenet.library.io.serial.LiveArchiver;
import freenet.library.util.exec.SimpleProgress;
import freenet.library.util.exec.TaskAbortException;

public class FcpArchiver<T,  S extends ObjectStreamWriter & ObjectStreamReader> 
		implements LiveArchiver<T, freenet.library.util.exec.SimpleProgress> {
	private FcpConnection connection;
	private File cacheDir;
	private S readerWriter;
	private String mimeType;
	private int size;
	private Priority priorityLevel;
	private String identifier;
	
	private static int identifierCounter = 0;
	private static String getNewIdentifier() {
		return "FcpWriter" + (++identifierCounter);
	}

	public FcpArchiver(FcpConnection fcpConnection, 
					   File directory,
					   S rw,
					   String mime, int s,
					   Priority pl) {
		connection = fcpConnection;
		cacheDir = directory;
		readerWriter = rw;
		mimeType = mime;
		size = s;
		priorityLevel = pl;
		identifier = getNewIdentifier();
	}

	@Override
	public void pull(freenet.library.io.serial.Serialiser.PullTask<T> task)
			throws TaskAbortException {
		pullLive(task, null);
	}

	@Override
	public void push(freenet.library.io.serial.Serialiser.PushTask<T> task)
			throws TaskAbortException {
		throw new NotImplementedException();
	}

	/**
	 * Initial implementation, fetch everything from the cache. This means 
	 * that we cannot take over someone else's index. 
	 */
	@Override
	public void pullLive(freenet.library.io.serial.Serialiser.PullTask<T> task,
			SimpleProgress progress) throws TaskAbortException {
		if (cacheDir.exists()) {
			String cacheKey = null;
			if (task.meta instanceof String) {
				cacheKey = (String) task.meta;
			} else if (task.meta instanceof byte[]) {
				cacheKey = Base64.encode(SHA256.digest((byte[]) task.meta));
			}

			try {
				if(cacheDir != null && cacheDir.exists() && cacheDir.canRead()) {
					File cached = new File(cacheDir, cacheKey);
					if(cached.exists() && 
							cached.length() != 0 &&
							cached.canRead()) {
						InputStream is = new FileInputStream(cached);
						task.data = (T) readerWriter.readObject(is);
						is.close();
					}
				}
					
				if (progress != null) {
					progress.addPartKnown(0, true);
				}
			} catch (IOException e) {
				System.out.println("IOException:");
				e.printStackTrace();
				throw new TaskAbortException("Failed to read content from local tempbucket", e, true);
			}
			return;
		}
		throw new UnsupportedOperationException(
				"Cannot find the key " +
				task.meta +
				" in the cache.");
	}

	@Override
	public void pushLive(freenet.library.io.serial.Serialiser.PushTask<T> task,
			SimpleProgress p) throws TaskAbortException {
		throw new NotImplementedException();
	}

	@Override
	public void waitForAsyncInserts() throws TaskAbortException {
		throw new UnsupportedOperationException();
	}
}
