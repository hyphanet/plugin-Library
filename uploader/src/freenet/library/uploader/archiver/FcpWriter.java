package freenet.library.uploader.archiver;

import java.io.IOException;

import net.pterodactylus.fcp.ClientGet;
import net.pterodactylus.fcp.ClientHello;
import net.pterodactylus.fcp.ClientPut;
import net.pterodactylus.fcp.CloseConnectionDuplicateClientName;
import net.pterodactylus.fcp.FcpAdapter;
import net.pterodactylus.fcp.FcpConnection;
import net.pterodactylus.fcp.FcpMessage;
import net.pterodactylus.fcp.NodeHello;
import freenet.library.Priority;
import freenet.library.io.ObjectStreamWriter;
import freenet.library.io.serial.LiveArchiver;
import freenet.library.util.exec.SimpleProgress;
import freenet.library.util.exec.TaskAbortException;

public class FcpWriter<T> implements
		LiveArchiver<T, freenet.library.util.exec.SimpleProgress> {
	private FcpConnection connection;
	private ObjectStreamWriter writer;
	private String mimeType;
	private int size;
	private Priority priorityLevel;
	private String identifier;
	
	private static int identifierCounter = 0;
	private static String getNewIdentifier() {
		return "FcpWriter" + (++identifierCounter);
	}

	public FcpWriter(FcpConnection fcpConnection, ObjectStreamWriter w,
					 String mime, int s,
					 Priority pl) {
		connection = fcpConnection;
		writer = w;
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
		throw new UnsupportedOperationException();
	}

	@Override
	public void pullLive(freenet.library.io.serial.Serialiser.PullTask<T> task,
			SimpleProgress p) throws TaskAbortException {
		throw new UnsupportedOperationException();
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
