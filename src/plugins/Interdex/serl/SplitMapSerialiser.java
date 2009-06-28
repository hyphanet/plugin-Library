/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.serl.Serialiser.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
** MapSerialiser that divides each map into a collection of bins with some
** fixed capacity with at most one bin half full or less.
**
** TODO clean up structure of this...
** DOCUMENT
**
** @author infinity0
*/
public class SplitMapSerialiser<K, T extends Collection>
extends CompositeSerialiser<T, Map<K, T>, IterableSerialiser<Map<K, T>>>
implements MapSerialiser<K, T> {

	/**
	** The maximum load a bin can hold.
	*/
	final protected int capacity;

	/**
	** Half of the capacity.
	*/
	final protected int caphalf;

	/**
	** A collection class which is used to instantiate new groups.
	*/
	final protected Class<? extends T> collClass;

	/**
	** Descending comparator for total bin load.
	*/
	final protected static Comparator<Bin> binComparator = new Comparator<Bin>() {
		public int compare(Bin bin1, Bin bin2) {
			if (bin1 == bin2) { return 0; }
			int d = bin2.filled() - bin1.filled();
			// this is a bit of a hack but is needed since Tree* treats two objects
			// as "equal" if their "compare" returns 0
			return (d != 0)? d: bin1.getIndex() - bin2.getIndex();
		}
	};

	/**
	** Descending comparator for bin elements.
	*/
	final protected static Comparator<Collection> binElementComparator = new Comparator<Collection>() {
		public int compare(Collection c1, Collection c2) {
			if (c1 == c2) { return 0; }
			int d = c2.size() - c1.size();
			// this is a bit of a hack but is needed since Tree* treats two objects
			// as "equal" if their "compare" returns 0
			if (d != 0) { return d; }
			int h = c2.hashCode() - c1.hashCode();
			// on the off chance that the hashCodes are equal but the objects are not,
			// test the string representations of them...
			return (h != 0)? h: (c2.equals(c1))? 0: c2.toString().compareTo(c1.toString());
		}
	};

	public SplitMapSerialiser(int c, Class<? extends T> cc, IterableSerialiser<Map<K, T>> s) {
		super(s);
		if (c <= 0) {
			throw new IllegalArgumentException("Capacity must be greater than zero.");
		}
		try {
			cc.newInstance();
		} catch (InstantiationException e) {
			throw new IllegalArgumentException("Cannot instantiate class. Make sure it has a nullary constructor.", e);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Cannot instantiate class. Make sure you have access to it.", e);
		}
		capacity = c;
		caphalf = c>>1;
		collClass = cc;
	}

	protected Bin newBin(int index) {
		return new Bin(capacity, index);
	}

	protected T newCollection() {
		try {
			return collClass.newInstance();
		} catch (InstantiationException e) {
			return null; // constructor should prevent this from ever happening, but the compiler bitches.
		} catch (IllegalAccessException e) {
			return null; // constructor should prevent this from ever happening, but the compiler bitches.
		}
	}

	/**
	** Given a map of sizeable tasks, return the bins the task data has been
	** packed into. Each collection in each bin is associated with the key of
	** the original task from which the collection was taken. Tasks larger than
	** the size of one bin are split over several bins. The resulting group of
	** bins will include at most one bin which is half full or less.
	**
	** @param tasks The tasks to pack
	** @return The bins containing the task data
	*/
	public Bin<T, K>[] binPack(Map<K, PushTask<T>> tasks) {
		///System.out.println("-----");
		// bin packing algorithm for M-sized bins
		SortedSet<Bin<T, K>> bins = new TreeSet<Bin<T, K>>(binComparator);

		// placeholder "bin" to put tasks that are caphlf big or less
		TreeMap<T, K> halftasks = new TreeMap<T, K>(binElementComparator);

		// we have an index for each bin so that we can preserve the ordering of a
		// collection that is split into multiple bins.
		int binindex = 0;

		// allocate new bins for all tasks greater than caphalf
		// the index of bins allocated will preserve the overall ordering of
		// the collection, if an order exists
		for (Map.Entry<K, PushTask<T>> en: tasks.entrySet()) {
			// for each task X:
			PushTask<T> task = en.getValue();
			if (task.data == null) { task.data = newCollection(); }
			int size = task.data.size();

			if (size > caphalf) {
				// if X's size is strictly greater than M/2, then put X into ceil(X/M) bins
				// in integer division this is (X-1)/M + 1
				int num = (size-1)/capacity + 1;

				// each bin will have floor(X/num) elements, or 1 more than this
				int max = size/num;
				int left = size - num*max;
				int i=0;
				Iterator it = task.data.iterator();

				// iterate on the groups with size max+1
				++max;
				for (; i<left; ++i) {
					Bin<T, K> bin = newBin(binindex++);
					T coll = newCollection();
					for (int j=0; j<max; ++j) {
						coll.add(it.next());
					}
					bin.put(coll, en.getKey());
					bins.add(bin);
				}
				// iterate on the groups with size max
				--max;
				for (; i<num; ++i) {
					Bin<T, K> bin = newBin(binindex++);
					T coll = newCollection();
					for (int j=0; j<max; ++j) {
						coll.add(it.next());
					}
					bin.put(coll, en.getKey());
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
			T coll = en.getKey();
			int size = coll.size();

			// get all bins that can fit task.data, by passing TreeSet.tailSet()
			// a dummy bin that makes TreeSet generate the correct subset.
			// TreeSet's implementation of tailSet() should do this efficiently
			SortedSet<Bin<T, K>> subbins = bins.tailSet(new DummyBin(capacity, capacity - size));

			if (subbins.isEmpty()) {
				// if no bin can fit it, then start a new bin
				Bin<T, K> bin = newBin(binindex++);
				bin.put(coll, en.getValue());
				// TODO perhaps we should clone the data, to be consistent with the above
				bins.add(bin);

			} else {
				// find the fullest bin that can fit X
				Bin<T, K> lowest = subbins.first();
				// TreeSet assumes its elements are immutable.
				bins.remove(lowest);
				lowest.put(coll, en.getValue());
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

			if (sm.size() < fullest.filled() - smallest.filled()) {
				// if its size is smaller than the difference between the size of F and S
				// remove it and put it in S

					smallest.put(sm, fullest.get(sm));
					fullest.remove(sm);

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

	@Override public void pull(Map<K, PullTask<T>> tasks, Object meta) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	/**
	** {@inheritDoc}
	**
	** This implementation will overwrite the task metadata with a {@link List}
	** whose first element is the size of the task data as an {@link Integer},
	** and whose other elements are the indexes of the bin(s) into which the
	** task was put (also as {@link Integer}s).
	**
	** TODO maybe have a map with keys "size" and "bins" instead?
	*/
	@Override public void push(Map<K, PushTask<T>> tasks, Object meta) {
		// tasks has form {K:(T,M)}
		Bin<T, K>[] bins = binPack(tasks);

		// prepare each task's meta data to hold a list of bins
		for (PushTask<T> task: tasks.values()) {
			List metalist = new ArrayList();
			// TODO maybe have this save { size: sz, bins: [*] } instead?
			metalist.add(new Integer(task.data.size()));
			task.meta = metalist;
		}

		// push the index of each collection to its task
		// at the same time, make a new list of tasks to pass to the next stage
		Integer i=0;
		List<PushTask<Map<K, T>>> bintasks = new ArrayList<PushTask<Map<K, T>>>(bins.length);
		for (Bin<T, K> bin: bins) {
			assert(bin.getIndex() == i);
			Map<K, T> taskmap = new HashMap<K, T>(bin.size()*2);

			for (Map.Entry<T, K> en: bin.entrySet()) {
				((List)tasks.get(en.getValue()).meta).add(i);
				taskmap.put(en.getValue(), en.getKey());
			}

			bintasks.add(new PushTask(taskmap, new Object[]{meta, i}));
			++i;
		}

		// bintasks has form [({K:T},[M,I])]
		subsrl.push(bintasks);
	}

	/************************************************************************
	** A class that represents a bin with a certain capacity.
	**
	** Note: this implementation is incomplete, since we do not override the
	** remove() methods of the collections and iterators returned by {@link
	** Map#entrySet()} etc. to also modify the weight, but these are never used
	** here so it's OK.
	*/
	protected static class Bin<T extends Collection, K> extends TreeMap<T, K> {

		final protected int capacity;
		final protected int index;
		protected int load;

		public Bin(int c, int i) {
			super(binElementComparator);
			capacity = c;
			index = i;
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
			if (!containsKey(c)) { load += c.size(); }
			return super.put(c, k);
		}

		@Override public K remove(Object c) {
			if (containsKey(c)) { load -= ((T)c).size(); }
			return super.remove(c);
		}

	}

	/************************************************************************
	** A class that pretends to be a bin with a certain capacity and load.
	** This is used when we want a such a bin for some purpose (eg. as an
	** argument to a comparator) but we don't want to have to populate a real
	** bin to get the desired load (which might be massive).
	*/
	protected static class DummyBin<T extends Collection, K> extends Bin<T, K> {

		public DummyBin(int c, int l) {
			super(c, 0);
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
