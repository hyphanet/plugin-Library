package freenet.library.uploader.archiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.pterodactylus.fcp.FcpConnection;
import freenet.copied.Base64;
import freenet.copied.SHA256;
import freenet.library.Priority;
import freenet.library.io.ObjectStreamReader;
import freenet.library.io.serial.LiveArchiver;
import freenet.library.util.exec.SimpleProgress;
import freenet.library.util.exec.TaskAbortException;

public class FcpReader<T> implements
		LiveArchiver<T, freenet.library.util.exec.SimpleProgress> {
	private File cacheDir;
	private ObjectStreamReader reader;
	private String mimeType;
	private int size;
	private Priority priorityLevel;

	public FcpReader(File directory, ObjectStreamReader r, 
					 String mime, int s,
					 Priority pl) {
		cacheDir = directory;
		reader = r;
		mimeType = mime;
		size = s;
		priorityLevel = pl;
	}

	@Override
	public void pull(freenet.library.io.serial.Serialiser.PullTask<T> task)
			throws TaskAbortException {
		pullLive(task, null);
	}

	@Override
	public void push(freenet.library.io.serial.Serialiser.PushTask<T> task)
			throws TaskAbortException {
		throw new UnsupportedOperationException();
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
						task.data = (T) reader.readObject(is);
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
		throw new UnsupportedOperationException();
	}

	@Override
	public void waitForAsyncInserts() throws TaskAbortException {
		throw new UnsupportedOperationException();
	}
}
