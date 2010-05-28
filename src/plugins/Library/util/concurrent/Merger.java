package plugins.Library.util.concurrent;

public interface Merger<A, B, V, C, X extends Exception> {
	
	/** Combine two objects in some way to produce a third, given context data, and 
	 * possibly throwing an Exception. */
	public V merge(A a, B b, C context) throws X;

}
