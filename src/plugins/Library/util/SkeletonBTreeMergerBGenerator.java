package plugins.Library.util;

import plugins.Library.util.concurrent.StreamMerge;
import plugins.Library.util.concurrent.StreamMergerBGenerator;
import plugins.Library.util.exec.TaskAbortException;

public class SkeletonBTreeMergerBGenerator<K, B> implements StreamMergerBGenerator<K, B, TaskAbortException> {

	final SkeletonBTreeMap<K, B> tree;
	
	public SkeletonBTreeMergerBGenerator(SkeletonBTreeMap<K, B> tree) {
		this.tree = tree;
	}
	
	public void generate(final StreamMerge<K, ?, B> merge) throws TaskAbortException {
		((SkeletonBTreeMap.SkeletonNode)(tree.root)).generate(merge);
	}

}
