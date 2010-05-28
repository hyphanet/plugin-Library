package plugins.Library.util.concurrent;

public interface StreamMergerBGenerator<K, B, X extends Exception> {
	
	public void generate(StreamMerge<K, ?, B> merge) throws X;

}
