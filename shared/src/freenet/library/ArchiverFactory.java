package freenet.library;

import freenet.library.io.ObjectStreamReader;
import freenet.library.io.ObjectStreamWriter;
import freenet.library.io.serial.LiveArchiver;
import freenet.library.util.exec.SimpleProgress;

public interface ArchiverFactory {
	<T, S extends ObjectStreamWriter & ObjectStreamReader> LiveArchiver<T, SimpleProgress>
		newArchiver(S rw, String mime, int size, Priority priorityLevel);
	<T, S extends ObjectStreamWriter & ObjectStreamReader> LiveArchiver<T, SimpleProgress>
	newArchiver(S rw, String mime, int size, LiveArchiver<T, SimpleProgress> archiver);
}
