/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.serl.Serialiser.*;
import plugins.Interdex.util.IdentityComparator;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Iterator;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
** {@link MapSerialiser} that packs a map into an array of fixed-capacity bins,
** with at most one bin half full or less.
**
** @author infinity0
*/
abstract public class Packer<K, T>
implements MapSerialiser<K, T>,
           Serialiser.Composite<IterableSerialiser<Map<K, T>>> {

	/**
	** The maximum load a bin can hold.
	*/
	final protected int capacity;

	/**
	** Half of the capacity.
	*/
	final protected int caphalf;

	/**
	** An element class which is used to instantiate new elements.
	*/
	final protected Class<? extends T> elementClass;

	/**
	** Descending comparator for bin elements.
	*/
	final protected Comparator<? super T> binElementComparator;

	/**
	** DOCUMENT
	*/
	final protected IterableSerialiser<Map<K, T>> subsrl;
	public IterableSerialiser<Map<K, T>> getChildSerialiser() { return subsrl; }

	/**
	** DOCUMENT
	**
	** @param cc A class with a nullary constructor that is used to instantiate
	**           new elements of bins. This argument may be null if a subclass
	**           overrides {@link #newElement()}.
	*/
	public Packer(IterableSerialiser<Map<K, T>> s, int c, Comparator<? super T> binElemComp, Class<? extends T> cc) {
		if (s == null) {
			throw new IllegalArgumentException("Can't have a null child serialiser");
		}
		if (c <= 0) {
			throw new IllegalArgumentException("Capacity must be greater than zero.");
		}
		try {
			if (cc != null) { cc.newInstance(); }
		} catch (InstantiationException e) {
			throw new IllegalArgumentException("Cannot instantiate class. Make sure it has a nullary constructor.", e);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Cannot instantiate class. Make sure you have access to it.", e);
		}
		subsrl = s;
		capacity = c;
		caphalf = c>>1;
		elementClass = cc;
		binElementComparator = binElemComp;
	}

	/**
	** Creates a new bin with the appropriate settings for this packer.
	*/
	protected Bin newBin(int index) {
		return new Bin(capacity, index, this);
	}

	/**
	** Creates a new bin element from the class passed into the constructor.
	*/
	protected T newElement() {
		if (elementClass == null) {
			throw new IllegalStateException("Packer cannot create element: No class was given to the constructor, but newElement() was not overriden.");
		}
		try {
			return elementClass.newInstance();
		} catch (InstantiationException e) {
			return null; // constructor should prevent this from ever happening, but the compiler bitches.
		} catch (IllegalAccessException e) {
			return null; // constructor should prevent this from ever happening, but the compiler bitches.
		}
	}

	/**
	** Creates a new partition a bin element, given an iterator through the
	** element and the number of items to add to the partition.
	**
	** @param i An iterator through the items of the element
	** @param num Number of items in the partition
	** @return The partition
	*/
	abstract protected T newPartitionOf(Iterator i, int num);

	/**
	** Adds the items of a partition of an element to the object representing
	** the whole element.
	**
	** @param element The parent element
	** @param partition The partition containing the items to add
	*/
	abstract protected void addPartitionTo(T element, T partition);

	/**
	** Returns an iterator over a bin element, that can be passed to {@link
	** #newPartitionOf(Iterator, int)}.
	*/
	abstract protected Iterable iterableOf(T element);

	/**
	** Returns the size of a bin element.
	*/
	abstract protected int sizeOf(T element);

	/**
	** Any tasks that need to be done after the bin tasks have been formed,
	** but before they have been passed to the child serialiser. The default
	** implementation does nothing.
	*/
	protected void preprocessPullBins(Map<K, PullTask<T>> tasks, Collection<PullTask<Map<K, T>>> bintasks) { }

	/**
	** Any tasks that need to be done after the bin tasks have been formed,
	** but before they have been passed to the child serialiser. The default
	** implementation does nothing.
	*/
	protected void preprocessPushBins(Map<K, PushTask<T>> tasks, Collection<PushTask<Map<K, T>>> bintasks) { }

	/**
	** Given partition and its containing bin, register its details with the
	** given metadata.
	**
	** This implementation assumes the metadata map contains "size" and "bins"
	** keys mapping to to {@link List}<{@link Integer}>s, and adds the
	** partition's size and the bin's index to these lists, respectively.
	*/
	protected void addBinToMeta(Map<String, Object> meta, T partition, int binindex) {
		List<Integer> size = (List<Integer>)meta.get("size");
		if (size == null) { meta.put("size", size = new ArrayList<Integer>()); }
		size.add(sizeOf(partition));

		List<Object> bins = (List<Object>)meta.get("bins");
		if (bins == null) { meta.put("bins", bins = new ArrayList<Object>()); }
		bins.add(new Integer(binindex));
	}

	/**
	** Given a map of metadata, retrieve the list of bins that it describes.
	*/
	protected List<Object> getBinsFromMeta(Map<String, Object> meta) {
		Object list = meta.get("bins");
		return (List<Object>)list;
	}

	/**
	** Given a map of {@link PushTask}s, pack the task data into a set of bins,
	** partitioning each task data (element) if it it is too big to fit into
	** a single bin. Each element in each bin is associated with the key of the
	** original task from which the element was taken. The resulting group of
	** bins will include at most one bin which is half full or less.
	**
	** @param tasks The tasks to pack
	** @return The bins containing the task data
	*/
	public Bin<T, K>[] binPack(Map<K, PushTask<T>> tasks) {
		///System.out.println("-----");
		// bin packing algorithm for M-sized bins
		SortedSet<Bin<T, K>> bins = new TreeSet<Bin<T, K>>();

		// placeholder "bin" to put tasks that are caphalf big or less
		TreeMap<T, K> halftasks = new TreeMap<T, K>(binElementComparator);

		// we have an index for each bin so that we can preserve the ordering of an
		// element that is split into multiple bins.
		int binindex = 0;

		// allocate new bins for all tasks greater than caphalf
		// the index of bins allocated will preserve the overall ordering of the
		// components within each element, if an order exists
		for (Map.Entry<K, PushTask<T>> en: tasks.entrySet()) {
			// for each task X:
			PushTask<T> task = en.getValue();
			if (task.data == null) { task.data = newElement(); }
			int size = sizeOf(task.data);

			if (size > caphalf) {
				// if X's size is strictly greater than M/2, then put X into ceil(X/M) bins
				// in integer division this is (X-1)/M + 1
				int num = (size-1)/capacity + 1;

				// each bin will have floor(X/num) elements, or 1 more than this
				int max = size/num;
				int left = size - num*max;
				int i=0;
				Iterator it = iterableOf(task.data).iterator();

				// iterate on the groups with size max+1
				++max;
				for (; i<left; ++i) {
					Bin<T, K> bin = newBin(binindex++);
					T el = newPartitionOf(it, max);
					bin.put(el, en.getKey());
					bins.add(bin);
				}
				// iterate on the groups with size max
				--max;
				for (; i<num; ++i) {
					Bin<T, K> bin = newBin(binindex++);
					T el = newPartitionOf(it, max);
					bin.put(el, en.getKey());
					bins.add(bin);
				}

			} else {
				// keep track of the other tasks
				halftasks.put(task.data, en.getKey());
			}
		}

		// go through the halftasks in descending order and try to fit them
		// into the bins allocated in the previous stage
		for (Map.Entry<T, K> en: halftasks.entrySet()) {
			T el = en.getKey();
			int size = sizeOf(el);

			// get all bins that can fit task.data, by passing TreeSet.tailSet()
			// a dummy bin that makes TreeSet generate the correct subset.
			// TreeSet's implementation of tailSet() should do this efficiently
			SortedSet<Bin<T, K>> subbins = bins.tailSet(new DummyBin(capacity, capacity - size, this));

			if (subbins.isEmpty()) {
				// if no bin can fit it, then start a new bin
				Bin<T, K> bin = newBin(binindex++);
				bin.put(el, en.getValue());
				// TODO perhaps we should clone the data, to be consistent with the above
				bins.add(bin);

			} else {
				// find the fullest bin that can fit X
				Bin<T, K> lowest = subbins.first();
				// TreeSet assumes its elements are immutable.
				bins.remove(lowest);
				lowest.put(el, en.getValue());
				bins.add(lowest);
			}
		}

		///for (Bin<T, K> bin: bins) {
		///	System.out.println(bin.filled());
		///}

		// at this point, there should be a maximum of one bin which is half-full
		// or less (if there were two then one of them would have been put into the
		// other one by the loop, contradiction).

		// keep track of bins we have finalised
		//List<Bin<T, K>> binsFinal = new ArrayList<Bin<T, K>>(bins.size());
		Bin<T, K>[] binsFinal = (Bin<T, K>[])new Bin[bins.size()];

		// special case
		if (bins.size() == 0) { return binsFinal; }

		// let S point to the most empty bin
		Bin<T, K> smallest = bins.last();
		bins.remove(smallest);

		// TODO maybe only do the below loop if smallest.filled() <= caphalf
		int i = 0;
		int maxsteps = tasks.size() * bins.size();
		// stop when the queue is empty, or when the loop has run for more than
		// (number of elements * number of bins) iterations. (I cba working out
		// whether the algorithm halts or not, but each step of the loop serves to
		// "even out" the bins more than the last step, so the algorithm tends to
		// an optimal solution.)
		while (!bins.isEmpty() && i++ < maxsteps) {
			// pop the fullest bin, call this F
			Bin<T, K> fullest = bins.first();
			bins.remove(fullest);

			// get the smallest element
			T sm = fullest.lastKey();

			if (sizeOf(sm) < fullest.filled() - smallest.filled()) {
				// if its size is smaller than the difference between the size of F and S
				// remove it and put it in S

					smallest.put(sm, fullest.remove(sm));

					if (fullest.filled() < smallest.filled()) {
						// if this makes F become smaller than S, then push S's referent back onto
						// the queue, and point S to this bin
						bins.add(smallest);
						smallest = fullest;

					} else {
						// if F is bigger than S, push F back onto the queue
						bins.add(fullest);
					}

			} else {
				// if there does not exist such an element, then this bin will never take
				// part in any further activities even if it were to be put back into the
				// queue (think about it) so leave it off the queue, and mark it as final.

				binsFinal[fullest.getIndex()] = fullest;
			}
		}

		binsFinal[smallest.getIndex()] = smallest;
		for (Bin<T, K> bin: bins) {
			binsFinal[bin.getIndex()] = bin;
		}

		return binsFinal;
	}

	/*========================================================================
	  public interface MapSerialiser
	 ========================================================================*/

	/**
	** {@inheritDoc}
	**
	** This implementation will pull data for each task in the map with
	** non-null metadata.
	**
	** The child serialiser should process metadata of the form Array[{@link
	** Object} metadata, {@link Integer} binindex].
	*/
	@Override public void pull(Map<K, PullTask<T>> tasks, Object meta) {
		// tasks has form {K:(*,M)}
		// put all the bins from each task into a list of new tasks for each bin
		Map<Object, PullTask<Map<K, T>>> bins = new HashMap<Object, PullTask<Map<K, T>>>();
		for (Map.Entry<K, PullTask<T>> en: tasks.entrySet()) {
			for (Object o: getBinsFromMeta((Map<String, Object>)en.getValue().meta)) {
				if (!bins.containsKey(o)) {
					if (o instanceof Integer) {
						bins.put(o, new PullTask<Map<K, T>>(new Object[]{meta, (Integer)o}));
					} else {
						bins.put(o, new PullTask<Map<K, T>>(o));
					}
				}
			}
		}
		Collection<PullTask<Map<K, T>>> bintasks = bins.values();
		preprocessPullBins(tasks, bintasks);

		// bintasks has form [(*,[meta,I])]
		// pull each bin
		subsrl.pull(bintasks);
		// bintasks has form [({K:T},[meta,I])]

		// for each task, grab and remove its partitions from its bins
		for (Map.Entry<K, PullTask<T>> en: tasks.entrySet()) {
			PullTask<T> task = en.getValue();
			task.data = newElement();
			for (Object o: getBinsFromMeta((Map<String, Object>)task.meta)) {
				PullTask<Map<K, T>> bintask = bins.get(o);
				T partition;
				if (bintask.data == null || (partition = bintask.data.remove(en.getKey())) == null) {
					// TODO use DFEx
					throw new IllegalArgumentException("Packer did not find the expected partition in the given bin. Either the data is corrupt, or the child serialiser is buggy.");
				}
				addPartitionTo(task.data, partition);
			}
		}

		// if there is any leftover data in the bins, load them anyway
		for (PullTask<Map<K, T>> bintask: bintasks) {
			for (Map.Entry<K, T> en: bintask.data.entrySet()) {
				if (tasks.containsKey(en.getKey())) {
					throw new IllegalArgumentException("Packer found an extra unexpected partition for a bin. Either the data is corrupt, or the child serialiser is buggy.");
				}
				PullTask<T> task = new PullTask<T>(new HashMap<String, Object>());
				// set the metadata properly
				addBinToMeta((Map<String, Object>)task.meta, en.getValue(), (Integer)((Object[])bintask.meta)[1]);
				task.data = newElement();
				tasks.put(en.getKey(), task);
				addPartitionTo(task.data, en.getValue());
			}
		}

	}

	/**
	** {@inheritDoc}
	**
	** This implementation requires all keys of the subgroup to be present in
	** the map. It will push data for each task with null metadata. (TODO at
	** the moment it also requires each task to have null metadata.)
	**
	** The child serialiser should process metadata of the form Array[{@link
	** Object} metadata, {@link Integer} binindex].
	**
	** The metadata passed back to the caller is determined by the
	** implementation of {@link #addBinToMeta}. By default, this is a map of
	** (a list of bins) and (a list of bin sizes).
	*/
	@Override public void push(Map<K, PushTask<T>> tasks, Object meta) {
		// PRIORITY make binPack() able to try to do bin-packing even when
		// some of the data is already in a bin. for now just throw this
		// exception
		for (Map.Entry<K, PushTask<T>> en: tasks.entrySet()) {
			if (en.getValue().meta != null) {
				throw new UnsupportedOperationException("Cannot perform packing when some of the bins are not loaded.");
			}
		}

		// tasks has form {K:(T,*)}
		Bin<T, K>[] bins = binPack(tasks);

		// prepare each task's meta data to hold information about the bins
		for (PushTask<T> task: tasks.values()) {
			task.meta = new HashMap<String, Object>();
		}

		// push the index of each element to its task
		// at the same time, make a new list of tasks to pass to the next stage
		Integer i=0;
		List<PushTask<Map<K, T>>> bintasks = new ArrayList<PushTask<Map<K, T>>>(bins.length);
		for (Bin<T, K> bin: bins) {
			assert(bin.getIndex() == i);
			Map<K, T> taskmap = new HashMap<K, T>(bin.size()<<1);

			for (Map.Entry<T, K> en: bin.entrySet()) {
				addBinToMeta((Map)tasks.get(en.getValue()).meta, en.getKey(), i);
				taskmap.put(en.getValue(), en.getKey());
			}

			bintasks.add(new PushTask<Map<K, T>>(taskmap, new Object[]{meta, i}));
			++i;
		}
		// tasks has form {K:(T,M)} where M is whatever setMetaAfterPack() returns
		preprocessPushBins(tasks, bintasks);

		// bintasks has form [({K:T},[meta,I])]
		subsrl.push(bintasks);
		i=0;
		for (PushTask<Map<K, T>> btask: bintasks) {
			if (!(btask.meta instanceof Object[])) {
				for (K key: btask.data.keySet()) {
					List<Object> binlist = getBinsFromMeta((Map<String, Object>)tasks.get(key).meta);
					binlist.set(binlist.indexOf(i), btask.meta);
				}
			}
			++i;
		}
	}

	/************************************************************************
	** A class that represents a bin with a certain capacity.
	**
	** NOTE: this implementation is incomplete, since we do not override the
	** remove() methods of the collections and iterators returned by {@link
	** Map#entrySet()} etc. to also modify the weight, but these are never used
	** here so it's OK.
	*/
	protected static class Bin<T, K> extends TreeMap<T, K> implements Comparable<Bin> {

		final protected int capacity;
		final protected int index;

		// we do this instead of having a non-static class because otherwise
		// we can't create a new Bin[] in binPack() ("generic array creation")
		// ideally, Collection and Map should both extend a Sizeable interface
		// but oh well, java sucks.
		final protected Packer packer;

		protected int load;

		public Bin(int c, int i, Packer p) {
			super(p.binElementComparator);
			capacity = c;
			index = i;
			packer = p;
		}

		public int getIndex() {
			return index;
		}

		public int filled() {
			return load;
		}

		public int remainder() {
			return capacity - load;
		}

		@Override public K put(T c, K k) {
			if (!containsKey(c)) { load += packer.sizeOf(c); }
			return super.put(c, k);
		}

		@Override public K remove(Object c) {
			if (containsKey(c)) { load -= packer.sizeOf((T)c); }
			return super.remove(c);
		}

		/**
		** Descending comparator for total bin load. Ie. ascending comparator
		** for total space left.
		*/
		@Override public int compareTo(Bin bin) {
			if (this == bin) { return 0; }
			int f1 = filled();
			int f2 = bin.filled();
			if (f1 != f2) { return (f2 > f1)? 1: -1; }
			return IdentityComparator.comparator.compare(this, bin);
		}

	}

	/************************************************************************
	** A class that pretends to be a bin with a certain capacity and load.
	** This is used when we want a such a bin for some purpose (eg. as an
	** argument to a comparator) but we don't want to have to populate a real
	** bin to get the desired load (which might be massive).
	*/
	protected static class DummyBin<T, K> extends Bin<T, K> {

		public DummyBin(int c, int l, Packer p) {
			super(c, -1, p);
			load = l;
		}

		@Override public K put(T c, K k) {
			throw new UnsupportedOperationException("Dummy bins cannot be modified");
		}

		@Override public K remove(Object c) {
			throw new UnsupportedOperationException("Dummy bins cannot be modified");
		}

	}


}
