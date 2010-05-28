package plugins.Library.util.concurrent;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;

public class IteratorStreamMergerBGenerator<K, B> implements StreamMergerBGenerator<K, B> {

	final Iterable<Map.Entry<K, B>> iterable;
	
	public IteratorStreamMergerBGenerator(Iterable<Entry<K, B>> it) {
		this.iterable = it;
	}
	
	public void start(final StreamMerge<K, ?, B> merge, Executor exec) {
		exec.execute(new Runnable() {

			public void run() {
				for(Map.Entry<K,B> entry : iterable)
					merge.submitB(entry.getKey(), entry.getValue());
			}
			
		});
	}

}
