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
public class SplitMapSerialiser<T extends Collection> implements MapSerialiser<T> {

	final protected int capacity;
	final protected int caphalf;

	/**
	** A collection class which is instantiated for a new group.
	*/
	final protected Class<T> collClass;

	public SplitMapSerialiser(int c, Class<T> cc) {
		if (c <= 0) {
			throw new IllegalArgumentException("Capacity must be greater than zero");
		}
		try {
			cc.newInstance();
		} catch (Throwable e) {
			throw new IllegalArgumentException("Cannot instantiate class", e);
		}
		capacity = c;
		caphalf = c/2;
		collClass = cc;
	}

	protected Bin newBin(int index) {
		return new Bin(capacity, index);
	}

	protected T newCollection() {
		try {
			return collClass.newInstance();
		} catch (InstantiationException e) {
			return null; // constructor should prevent this from happening
		} catch (IllegalAccessException e) {
			return null; // constructor should prevent this from happening
		}
	}

	/**
	** Descending comparator for total bin load.
	*/
	final protected static Comparator<Bin> binComparator = new Comparator<Bin>() {
		public int compare(Bin bin1, Bin bin2) {
			return bin1.filled() - bin2.filled();
		}
	};

	/**
	** Ascending comparator for bin elements.
	*/
	final protected static Comparator<Collection> binElementComparator = new Comparator<Collection>() {
		public int compare(Collection c1, Collection c2) {
			return c2.size() - c1.size();
		}
	};

	public <K> List<Bin<T, K>> binPack(Map<K, PushTask<T>> tasks) {
		// bin packing algorithm for M-sized bins:

		SortedSet<Bin<T, K>> bins = new TreeSet<Bin<T, K>>(binComparator);

		int binindex = 0;
		// we have an index for each bin so that we can preserve the ordering of a
		// collection that is split into multiple bins.

		for (Map.Entry<K, PushTask<T>> en: tasks.entrySet()) {
			// for each task X:
			PushTask<T> task = en.getValue();
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
				for (; i<size; ++i) {
					Bin<T, K> bin = newBin(binindex++);
					T coll = newCollection();
					for (int j=0; j<max; ++j) {
						coll.add(it.next());
					}
					bin.put(coll, en.getKey());
					bins.add(bin);
				}

			} else {
				// get all bins that can fit task.data, by passing TreeSet.tailSet()
				// a dummy bin that makes TreeSet generate the correct subset.
				// TreeSet's implementation of tailSet() should do this efficiently

				SortedSet<Bin<T, K>> subbins = bins.tailSet(new DummyBin(capacity, capacity - size));

				if (subbins.isEmpty()) {
					// if no bin can fit it, then start a new bin
					Bin<T, K> bin = newBin(binindex++);
					bin.put(task.data, en.getKey());
					// TODO perhaps we should clone the data, to be consistent with the above
					bins.add(bin);
				} else {
					// find the fullest bin that can fit X
					Bin<T, K> lowest = subbins.first();
					bins.remove(lowest);
					// TreeSet assumes its elements are immutable.
					lowest.put(task.data, en.getKey());
					bins.add(lowest);

				}
			}
		}

		// at this point, there should be a maximum of one bin which is half-full
		// or less (if there were two then one of them would have been put into the
		// other one by the loop, contradiction).

		// let S point to the most empty bin
		Bin<T, K> smallest = bins.first();
		bins.remove(smallest);

		// keep track of bins we have finalised
		List<Bin<T, K>> binsFinal = new ArrayList<Bin<T, K>>(bins.size());

		int i = 0;
		int maxsteps = tasks.size() * bins.size();
		// stop when the queue is empty, or when the loop has run for more than
		// (number of elements * number of bins) iterations. (I cba working out
		// whether the algorithm halts or not, but each step of the loop serves to
		// "even out" the bins more than the last step, so the algorithm tends to
		// an optimal solution.)
		while (!bins.isEmpty() && i++ < maxsteps) {
			// pop the fullest bin, call this F
			Bin<T, K> fullest = bins.last();
			bins.remove(fullest);

			// get the smallest element
			T sm = fullest.firstKey();

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
						// OPTIMISE, if there are more elements that can be moved, then keep moving them
						// without re-adding F
						bins.add(fullest);
					}

			} else {
				// if there does not exist such an element, then this bin will never take
				// part in any further activities even if it were to be put back into the
				// queue (think about it) so leave it off the queue, and mark it as final.

				binsFinal.set(fullest.getIndex(), fullest);
			}
		}

		binsFinal.set(smallest.getIndex(), smallest);
		for (Bin<T, K> bin: bins) {
			binsFinal.set(bin.getIndex(), bin);
		}

		return binsFinal;

	}


	/************************************************************************
	 * public interface MapSerialiser
	 ************************************************************************/

	@Override public <K> void pull(Map<K, PullTask<T>> tasks, Object meta) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override public <K> void push(Map<K, PushTask<T>> tasks, Object meta) {

		// tasks has form {K:(T,M)}

		List<Bin<T, K>> bins = binPack(tasks);

		// prepare each task's meta data to hold a list of bins
		for (PushTask<T> task: tasks.values()) {
			List metalist = new ArrayList();
			metalist.add(task.meta);
			task.meta = metalist;
		}

		// push the index of each collection to its task
		// at the same time, make a new list of tasks to pass to the next stage
		Integer i=0;
		List<PushTask<HashMap<K, T>>> bintasks = new ArrayList<PushTask<HashMap<K, T>>>(bins.size());
		for (Bin<T, K> bin: bins) {
			assert(bin.getIndex() == i);
			HashMap<K, T> taskmap = new HashMap<K, T>(bin.size()*2);

			for (Map.Entry<T, K> en: bin.entrySet()) {
				((List)tasks.get(en.getValue()).meta).add(i);
				taskmap.put(en.getValue(), en.getKey());
			}

			bintasks.add(new PushTask(taskmap, new Object[]{meta, i}));
			++i;
		}

		// bintasks has form [({K:T},[M,I])]

		// TODO do something with this..

	}


	/**
	** DOCUMENT
	**
	** Note: implementation is incomplete, since we do not override the
	** remove() methods of the collections and iterators returned by {@link
	** Map#entrySet} to also modify the weight, but these are never used here
	** so it's OK.
	**
	*/
	public static class Bin<T extends Collection, K> extends TreeMap<T, K> {

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

	public static class DummyBin<T extends Collection, K> extends Bin<T, K> {

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
