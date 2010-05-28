package plugins.Library.util.concurrent;

import java.util.Map;
import java.util.Map.Entry;

public class IteratorStreamMergerBGenerator<K, B, X extends Exception> implements StreamMergerBGenerator<K, B, X> {

	final Iterable<Map.Entry<K, B>> iterable;
	
	public IteratorStreamMergerBGenerator(Iterable<Entry<K, B>> it) {
		this.iterable = it;
	}
	
	public void generate(final StreamMerge<K, ?, B> merge) {
		for(Map.Entry<K,B> entry : iterable)
			merge.submitB(entry.getKey(), entry.getValue());
	}

}
