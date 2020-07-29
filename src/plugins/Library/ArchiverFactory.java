package plugins.Library;

import plugins.Library.io.ObjectStreamReader;
import plugins.Library.io.ObjectStreamWriter;
import plugins.Library.io.serial.LiveArchiver;
import plugins.Library.util.exec.SimpleProgress;

public interface ArchiverFactory {
    <T, S extends ObjectStreamWriter & ObjectStreamReader>
		  LiveArchiver<T, SimpleProgress>
		  newArchiver(S rw, String mime, int size,
                      Priority priorityLevel);

    <T, S extends ObjectStreamWriter & ObjectStreamReader>
		  LiveArchiver<T, SimpleProgress>
		  newArchiver(S rw, String mime, int size,
                      LiveArchiver<T, SimpleProgress> archiver);
}
