package plugins.Library.util.concurrent;

import java.util.concurrent.Executor;

/** Once start()ed, this will constantly feed data, in order, to a StreamMerger.
 * It is responsible for its own scheduling i.e. it will run on a separate thread. */
public interface StreamMergerBGenerator<K, B> {
	
	public void start(StreamMerge<K, ?, B> merge, Executor exec);

}
