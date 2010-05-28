package plugins.Library.util.concurrent;

public interface Merger<V, X extends Exception> {
	
	public V merge(V a, V b) throws X;

}
