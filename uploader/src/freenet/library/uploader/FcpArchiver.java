package freenet.library.uploader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import net.pterodactylus.fcp.FcpConnection;
import freenet.copied.Base64;
import freenet.copied.SHA256;
import freenet.library.Priority;
import freenet.library.io.ObjectStreamReader;
import freenet.library.io.ObjectStreamWriter;
import freenet.library.io.serial.LiveArchiver;
import freenet.library.util.exec.SimpleProgress;
import freenet.library.util.exec.TaskAbortException;


public class FcpArchiver<T,  S extends ObjectStreamWriter & ObjectStreamReader>
		extends FileClientPutter 
		implements LiveArchiver<T, freenet.library.util.exec.SimpleProgress> {
	private File cacheDir;
	private ObjectStreamReader<T> reader;
	private ObjectStreamWriter<T> writer;
	private String mimeType;
	private int size;
	private Priority priorityLevel;


	public FcpArchiver(FcpConnection fcpConnection, 
					   File directory,
					   S rw,
					   String mime, int s,
					   Priority pl) {
		super(fcpConnection);
		cacheDir = directory;
		reader = rw;
		writer = rw;
		mimeType = mime;
		size = s;
		priorityLevel = pl;
	}
	
	@Override
	protected net.pterodactylus.fcp.Priority getPriority() {
		switch (priorityLevel) {
		case Interactive:
			return net.pterodactylus.fcp.Priority.interactive;
		case Bulk:
			return net.pterodactylus.fcp.Priority.bulkSplitfile;
		}
		return net.pterodactylus.fcp.Priority.bulkSplitfile;		
	}

	@Override
	public void pull(freenet.library.io.serial.Serialiser.PullTask<T> task)
			throws TaskAbortException {
		pullLive(task, null);
	}

	@Override
	public void push(freenet.library.io.serial.Serialiser.PushTask<T> task)
			throws TaskAbortException {
		pushLive(task, null);
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
			SimpleProgress progress) throws TaskAbortException {
		if (connection == null) {
			throw new IllegalArgumentException("No connection.");
		}

		final String token = "FcpArchiverPushLive" + counter;

		// Writing to file.
		File file = new File(cacheDir, token);
		FileOutputStream fileOut = null;
		try {
			fileOut = new FileOutputStream(file);
			writer.writeObject(task.data, fileOut);
		} catch (IOException e) {
			throw new TaskAbortException("Cannot write to file " + file, e);
		} finally {
			try {
				fileOut.close();
			} catch (IOException e) {
				throw new TaskAbortException("Cannot close file " + file, e);
			}
		}
		
		PushAdapter putterListener;
		try {
			putterListener = startFileUpload(token, file);
		} catch (IOException e1) {
			throw new TaskAbortException("Cannot start upload of file " + file, e1);
		}

    	if (progress != null) {
    		progress.addPartKnown(1, true);
    	}

    	// Wait for identifier
        synchronized (putterListener) {
			while (putterListener.getURI() == null) {
				try {
					putterListener.wait();
				} catch (InterruptedException e) {
					throw new TaskAbortException("Iterrupted wait", e);
				}
			}
		}

        if (progress != null) {
        	progress.addPartDone();
        }
        task.meta = putterListener.getURI();

        // Moving file.
        file.renameTo(new File(cacheDir, putterListener.getURI()));

		startCleanupThread();        
	}


	@Override
	public void waitForAsyncInserts() throws TaskAbortException {
		boolean moreJobs = false;
		do {
			if (moreJobs) {
				synchronized (stillRunning) {
					try {
						stillRunning.wait();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			synchronized (stillRunning) {
				moreJobs = !stillRunning.isEmpty();
			}
		} while (moreJobs);
	}
}
