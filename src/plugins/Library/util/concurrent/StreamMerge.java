package plugins.Library.util.concurrent;

import java.util.HashMap;

/** Merge two incoming ordered streams and feed them to finish(), which can block. */
public abstract class StreamMerge<K, A, B> {
	
	private final HashMap<K, A> onlyA = new HashMap<K, A>();
	private final HashMap<K, B> onlyB = new HashMap<K, B>();
	
	public void submitA(K key, A a) {
		B b;
		synchronized(this) {
			b = onlyB.remove(key);
			if(b == null) {
				onlyA.put(key, a);
				return;
			}
		}
		finish(key, a, b);
	}
	
	public void submitB(K key, B b) {
		A a;
		synchronized(this) {
			a = onlyA.remove(key);
			if(a == null) {
				onlyB.put(key, b);
				return;
			}
		}
		finish(key, a, b);
	}

	protected abstract void finish(K key, A a, B b);

}
