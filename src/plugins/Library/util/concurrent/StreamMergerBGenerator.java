package plugins.Library.util.concurrent;

/** Once start()ed, this will constantly feed data, in order, to a StreamMerger.
 * It is responsible for its own scheduling i.e. it will run on a separate thread. */
public interface StreamMergerBGenerator {
	
	public void start(StreamMerge merge);

}
