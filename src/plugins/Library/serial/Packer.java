/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

import plugins.Library.serial.Serialiser.*;
import plugins.Library.util.IdentityComparator;

import java.util.Collections;
import java.util.Collection;
import java.util.Iterator;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.SortedSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

/**
** {@link MapSerialiser} that packs a map of weighable elements (eg. elements
** with "size") into a group of fixed-capacity bins. There are two main modes
** of operation:
**
** ; {@link #no_tiny} is {@code false} :
**   Uses the Best-Fit Decreasing bin-packing algorithm, plus an additional
**   redistribution algorithm that "evens out" the bins. Each bin will weigh
**   between BIN_CAP / 2 and BIN_CAP inclusive, except for at most one bin.
** ; {@link #no_tiny} is {@code true} :
**   Same as above, but if the exceptional bin exists, it will be merged with
**   the next-smallest bin. As before, each bin will weigh between BIN_CAP / 2
**   and BIN_CAP inclusive, except for the merged element (if it exists), which
**   will weigh between BIN_CAP and BIN_CAP * 3/2 exclusive.
**
** In addition to this, there is an {@link #aggression} attribute that affects
** the speed vs. optimality of the packing.
**
** The class requires that the weights of each element is never greater than
** BIN_CAP, so that an element can always totally fit into an additional bin;
** {@link IllegalArgumentException} will be thrown when elements that violate
** this are encountered.
**
** This class is useful for packing together a collection of root B-tree nodes.
**
** To implement this class, the programmer must implement:
**
** * {@link #newScale(Map)}
** * {@link Scale} (by implementing {@link Scale#weigh(Object)})
** * {@link #makeBinMeta(Object, Object)}
** * {@link #readBinMetaID(Object)}
**
** @author infinity0
*/
abstract public class Packer<K, T>
implements MapSerialiser<K, T>,
           Serialiser.Composite<IterableSerialiser<Map<K, T>>> {


	final protected int BIN_CAP;
	final protected int BIN_CAPHF;
	final protected boolean no_tiny;

	/**
	** How aggressive the bin-packing algorithm is. Eg. will it load bins that
	** are not loaded, to perform better optimisation?
	**
	** ;0 : discount all unloaded bins (unrecommended)
	** ;1 (default) : discount all unchanged bins after the BFD step
	** ;2 : discount all unchaged bins after the redistribution step
	** ;3 : load all bins before doing any packing (ie. re-pack from scratch)
	*/
	private Integer aggression = 1;

	/**
	** DOCUMENT
	*/
	final protected IterableSerialiser<Map<K, T>> subsrl;
	public IterableSerialiser<Map<K, T>> getChildSerialiser() { return subsrl; }

	/**
	** Constructs a Packer with the given parameters
	**
	** @param s The child serialiser
	** @param c The maximum weight of a bin
	** @param n Whether to merge or overlook the "tiny" element
	*/
	public Packer(IterableSerialiser<Map<K, T>> s, int c, boolean n) {
		if (s == null) {
			throw new IllegalArgumentException("Can't have a null child serialiser");
		}
		if (c <= 0) {
			throw new IllegalArgumentException("Capacity must be greater than zero.");
		}
		/*try {
			if (cc != null) { cc.newInstance(); }
		} catch (InstantiationException e) {
			throw new IllegalArgumentException("Cannot instantiate class. Make sure it has a nullary constructor.", e);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Cannot instantiate class. Make sure you have access to it.", e);
		}*/
		subsrl = s;
		BIN_CAP = c;
		BIN_CAPHF = c>>1;
		no_tiny = n;
	}

	/**
	** Constructs a Packer with the given parameters and {@link #no_tiny}
	** set to {@code true}.
	*/
	public Packer(IterableSerialiser<Map<K, T>> s, int c) {
		this(s, c, true);
	}

	/**
	** Atomically set the {@link #aggression}.
	*/
	public void setAggression(int i) {
		synchronized (aggression) {
			aggression = i;
		}
	}

	/**
	** Atomically get the {@link #aggression}.
	*/
	public int getAggression() {
		synchronized (aggression) {
			return aggression;
		}
	}

	/**
	** Constructs a {@link Scale} that measures the given map of elements.
	*/
	abstract public Scale<K, T> newScale(Map<K, ? extends Task<T>> elems);

	/**
	** Constructs the metadata for a bin, from the bin ID and the map-wide
	** metadata.
	*/
	abstract public Object makeBinMeta(Object mapmeta, Object binid);

	/**
	** Read the bin ID from the given bin's metadata.
	*/
	abstract public Object readBinMetaID(Object binmeta);

	/**
	** Creates a new {@link IDGenerator}.
	*/
	public IDGenerator generator() {
		return new IDGenerator();
	}

	/**
	** Looks through {@code elems} for {@link PushTask}s with null data and
	** reads its metadata to determine which bin to add it to.
	*/
	protected SortedSet<Bin<K>> initialiseBinSet(SortedSet<Bin<K>> bins, Map<K, PushTask<T>> elems, Scale<K, T> sc, IDGenerator gen) {

		Map<Object, Set<K>> binincubator = new HashMap<Object, Set<K>>();

		for (Map.Entry<K, PushTask<T>> en: elems.entrySet()) {
			PushTask<T> task = en.getValue();
			if (task.data == null) {
				if (task.meta != null) {
					throw new IllegalArgumentException("Packer error: null data and null meta for key" + en.getKey());
				}
				Object binID = sc.readMetaID(task.meta);

				Set<K> binegg = binincubator.get(binID);
				if (binegg == null) {
					binegg = new HashSet<K>();
					binincubator.put(binID, binegg);
				}
				binegg.add(en.getKey());
			}
		}

		for (Map.Entry<Object, Set<K>> en: binincubator.entrySet()) {
			bins.add(new Bin(BIN_CAP, sc, gen.registerID(en.getKey()), en.getValue()));
		}

		return bins;
	}

	/**
	** Looks through {@code elems} for {@link PushTask}s with non-null data and
	** packs it into the given collection of bins.
	**
	** NOTE: this method assumes the conditions described in the description
	** for this class. It is up to the calling code to ensure that they hold.
	*/
	protected void packBestFitDecreasing(SortedSet<Bin<K>> bins, Map<K, PushTask<T>> elems, Scale<K, T> sc, IDGenerator gen) {

		if (no_tiny) {
			// locate the single bin heavier than BIN_CAP (if it exists), and keep
			// moving the heaviest element from that bin into another bin, until that
			// bin weighs more than BIN_CAP / 2 (and both bins will weigh between
			// BIN_CAP / 2 and BIN_CAP

			// proof that this always succeeds:
			//
			// - since we are visiting elements in descending order of weights, we will
			//   never reach an element that takes the total weight of the second bin
			//   from strictly less than BIN_CAP / 2 to strictly more than BIN_CAP
			// - in other words, we will always "hit" our target weight range of
			//   BIN_CAP / 2 and BIN_CAP, and the other bin will be in this zone too
			//   (since the single bin would weight between BIN_CAP and BIN_CAP * 3/2
			//
			Bin<K> heaviest = bins.first();
			if (heaviest.filled() > BIN_CAP) {
				Bin<K> second = new Bin(BIN_CAP, sc, gen.nextID(), null);
				bins.remove(heaviest);
				while (second.filled() < BIN_CAPHF) {
					K key = heaviest.last();
					heaviest.remove(key);
					second.add(key);
				}
				bins.add(heaviest);
				bins.add(second);
			}
		}

		// heaviest bin is <= BIN_CAP
		assert(bins.first().filled() <= BIN_CAP);
		// 2nd-lightest bin is >= BIN_CAP / 2
		assert(bins.headSet(bins.last()).last().filled() >= BIN_CAPHF);

		// sort keys in descending weight order of their elements
		SortedSet<K> sorted = new TreeSet<K>(Collections.reverseOrder(sc));
		for (Map.Entry<K, PushTask<T>> en: elems.entrySet()) {
			if (en.getValue().data != null) {
				sorted.add(en.getKey());
			}
		}

		// go through the sorted set and try to fit the keys into the bins
		for (K key: sorted) {
			int weight = sc.getWeight(key);

			// get all bins that can fit the key; tailSet() should do this efficiently
			SortedSet<Bin<K>> subbins = bins.tailSet(new DummyBin(BIN_CAP, BIN_CAP - weight));

			if (subbins.isEmpty()) {
				// if no bin can fit it, then start a new bin
				Bin<K> bin = new Bin(BIN_CAP, sc, gen.nextID(), null);
				bin.add(key);
				bins.add(bin);

			} else {
				// get the fullest bin that can fit the key
				Bin<K> lowest = subbins.first();
				// TreeSet assumes its elements are immutable.
				bins.remove(lowest);
				lowest.add(key);
				bins.add(lowest);
			}
		}

		// heaviest bin is <= BIN_CAP
		assert(bins.first().filled() <= BIN_CAP);
		// 2nd-lightest bin is >= BIN_CAP / 2
		assert(bins.headSet(bins.last()).last().filled() >= BIN_CAPHF);

		if (no_tiny) {
			// locate the single bin lighter than BIN_CAP / 2 (if it exists), and
			// combine it with the next smallest bin
			Bin<K> lightest = bins.last();
			if (lightest.filled() < BIN_CAPHF) {
				bins.remove(lightest);
				Bin<K> second = bins.last();
				bins.remove(second);
				for (K k: lightest) {
					second.add(k);
				}
				bins.add(second);
			}
		}
	}

	/**
	** Given a group of bins, attempts to redistribute the items already
	** contained within them to make the weights more even.
	*/
	protected void redistributeWeights(SortedSet<Bin<K>> bins, Scale<K, T> sc) {

		// keep track of bins we have finalised
		List<Bin<K>> binsFinal = new ArrayList<Bin<K>>(bins.size());

		// special cases
		if (bins.size() < 2) { return; }

		// proof that the algorithm halts:
		//
		// - let D be the weight difference between the heaviest and lightest bins
		// - let N be the set containing all maximum and minimum bins
		// - at each stage of the algorithm, |N| strictly decreases, since the
		//   selected bin pair is either strictly "evened out", or its heavier
		//   member is discarded
		// - when |N| decreases to 0, D strictly decreases, and we get a new N
		// - D is bounded-below by 0
		// - hence the algorithm terminates
		//
		Bin<K> lightest = bins.last();
		while (!bins.isEmpty()) {
			// we don't need to find the lightest bin every time
			Bin<K> heaviest = bins.first();
			bins.remove(heaviest);
			int weightdiff = heaviest.filled() - lightest.filled();

			// get the lightest element
			K feather = heaviest.first();
			if (sc.getWeight(feather) < weightdiff) {
				// can be strictly "evened out"

				bins.remove(lightest);

				heaviest.remove(feather);
				lightest.add(feather);
				bins.add(heaviest);
				bins.add(lightest);

				lightest = bins.last();

			} else {
				// if there does not exist such an element, then it is impossible for the
				// heaviest bin to be evened out any further, since the loop does not
				// decrease the load of the lightest bin. so, remove it from the bins set.

				binsFinal.add(heaviest);
			}

			// TODO some tighter assertions than this
			assert(bins.first().filled() - bins.last().filled() <= weightdiff);
		}

		for (Bin<K> bin: binsFinal) {
			bins.add(bin);
		}

	}

	/**
	** Dicards all the bins which remain unchanged from what they were
	** initialised to contain. This means that we don't re-push something that
	** is already pushed.
	*/
	protected void discardUnchangedBins(SortedSet<Bin<K>> bins, Map<K, PushTask<T>> elems) {
		Iterator<Bin<K>> it = bins.iterator();
		while (it.hasNext()) {
			Bin<K> bin = it.next();
			if (bin.unchanged()) {
				it.remove();
				for (K key: bin) {
					elems.remove(key);
				}
			}
		}
	}

	/**
	** Pulls all PushTasks with null data. This is used when we need to push a
	** bin that has been assigned to hold the (unloaded) data of these tasks.
	*/
	protected void pullUnloaded(Map<K, PushTask<T>> tasks, Object meta) throws TaskAbortException {
		Map<K, PullTask<T>> newtasks = new HashMap<K, PullTask<T>>(tasks.size()<<1);
		for (Map.Entry<K, PushTask<T>> en: tasks.entrySet()) {
			PushTask<T> task = en.getValue();
			if (task.data == null) {
				newtasks.put(en.getKey(), new PullTask<T>(task.meta));
			}
		}
		pull(newtasks, meta);
		for (Map.Entry<K, PushTask<T>> en: tasks.entrySet()) {
			PushTask<T> task = en.getValue();
			PullTask<T> newtask = newtasks.get(en.getKey());
			if (newtask == null) {
				throw new IllegalStateException("Packer got unexpected data when pulling unloaded bins; the pull method is buggy.");
			}
			task.data = newtask.data;
			task.meta = newtask.meta;
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation loads all the data for each bin referred to in the
	** task map, and will add extra tasks to the map if those pulling those
	** bins result in extra data being loaded.
	*/
	@Override public void pull(Map<K, PullTask<T>> tasks, Object mapmeta) throws TaskAbortException {

		try {
			Scale<K, T> sc = newScale(tasks);
			Map<Object, PullTask<Map<K, T>>> bintasks = new HashMap<Object, PullTask<Map<K, T>>>();

			// put all the bins from each task into a list of new tasks for each bin
			for (Map.Entry<K, PullTask<T>> en: tasks.entrySet()) {
				Object binid = sc.readMetaID(en.getValue().meta);
				if (!bintasks.containsKey(binid)) {
					bintasks.put(binid, new PullTask<Map<K, T>>(makeBinMeta(mapmeta, binid)));
				}
			}

			// pull each bin
			subsrl.pull(bintasks.values());

			// for each task, grab and remove its element from its bin
			for (Map.Entry<K, PullTask<T>> en: tasks.entrySet()) {
				PullTask<T> task = en.getValue();
				PullTask<Map<K, T>> bintask = bintasks.get(sc.readMetaID(task.meta));
				if (bintask == null) {
					// task was marked as redundant by child serialiser
					tasks.remove(en.getKey());
				} else if (bintask.data == null || (task.data = bintask.data.remove(en.getKey())) == null) {
					throw new TaskAbortException("Packer did not find the element (" + en.getKey() + ") in the expected bin (" + sc.readMetaID(task.meta) + "). Either the data is corrupt, or the child serialiser is buggy.", null);
				}
			}

			// if there is any leftover data in the bins, load them anyway
			Map<K, PullTask<T>> leftovers = new HashMap<K, PullTask<T>>();
			for (Map.Entry<Object, PullTask<Map<K, T>>> en: bintasks.entrySet()) {
				PullTask<Map<K, T>> bintask = en.getValue();
				for (Map.Entry<K, T> el: bintask.data.entrySet()) {
					if (tasks.containsKey(el.getKey())) {
						throw new TaskAbortException("Packer found an extra unexpected element (" + el.getKey() + ") inside a bin (" + en.getKey() + "). Either the data is corrupt, or the child serialiser is buggy.", null);
					}
					PullTask<T> task = new PullTask<T>(sc.makeMeta(en.getKey(), sc.weigh(el.getValue())));
					task.data = el.getValue();
					leftovers.put(el.getKey(), task);
				}
			}
			tasks.putAll(leftovers);

		} catch (RuntimeException e) {
			throw new TaskAbortException("Could not complete the pull operation", e);
		}

	}

	/**
	** {@inheritDoc}
	**
	** This implementation uses the algorithm described in the... DOCUMENT
	**
	** The default generator '''requires''' all keys of the backing map to be
	** present in the input task map, since the default {@link IDGenerator}
	** does not have any other way of knowing what bin IDs were handed out in
	** previous push operations, and it must avoid giving out duplicate IDs.
	**
	** You can bypass this requirement by extending {@link IDGenerator} so that
	** it can work out that information (eg. using an algorithm that generates
	** a UUID), and overriding {@link #generator()}. (ID generation based on
	** bin ''content'' is ''not'' supported, and is not a priority at present.)
	*/
	@Override public void push(Map<K, PushTask<T>> tasks, Object mapmeta) throws TaskAbortException {

		try {
			// read local copy of aggression
			int agg = getAggression();

			IDGenerator gen = generator();
			Scale<K, T> sc = newScale(tasks);
			SortedSet<Bin<K>> bins = new TreeSet<Bin<K>>();

			// initialise the binset based on the aggression setting
			if (agg <= 0) {
				// discard all tasks with null data
				Iterator<PushTask<T>> it = tasks.values().iterator();
				while (it.hasNext()) {
					PushTask<T> task = it.next();
					if (task.data == null) {
						it.remove();
					}
				}
			} else if (agg <= 2) {
				// initialise already-allocated bins from tasks with null data
				initialiseBinSet(bins, tasks, sc, gen);
			} else {
				// pull all tasks with null data
				pullUnloaded(tasks, mapmeta);
			}

			// pack elements into bins
			packBestFitDecreasing(bins, tasks, sc, gen);
			if (agg <= 1) {
				// discard all bins not affected by the pack operation
				discardUnchangedBins(bins, tasks);
			}

			// redistribute weights between bins
			redistributeWeights(bins, sc);
			if (agg <= 2) {
				// discard all bins not affected by the redistribution operation
				discardUnchangedBins(bins, tasks);
				// pull all data that is as yet unloaded
				pullUnloaded(tasks, mapmeta);
			}

			// push all the bins
			List<PushTask<Map<K, T>>> bintasks = new ArrayList<PushTask<Map<K, T>>>(bins.size());
			for (Bin<K> bin: bins) {
				Map<K, T> data = new HashMap<K, T>(bin.size()<<1);
				for (K k: bin) {
					data.put(k, tasks.get(k).data);
				}
				bintasks.add(new PushTask<Map<K, T>>(data, makeBinMeta(mapmeta, bin.id)));
			}
			subsrl.push(bintasks);

			// set the metadata for all the pushed bins
			for (PushTask<Map<K, T>> bintask: bintasks) {
				for (K k: bintask.data.keySet()) {
					tasks.get(k).meta = sc.makeMeta(readBinMetaID(bintask.meta), sc.getWeight(k));
				}
			}

		} catch (RuntimeException e) {
			throw new TaskAbortException("Could not complete the push operation", e);
		}

	}


	/************************************************************************
	** A class that represents a bin with a certain capacity.
	**
	** NOTE: this implementation is incomplete, since we don't override the
	** remove() methods of the sets and iterators returned by the subset and
	** iterator methods, to also recalculate the weight, but these are never
	** used so it's OK.
	*/
	protected static class Bin<K> extends TreeSet<K> implements Comparable<Bin<K>> {

		final protected Object id;
		final protected Scale<K, ?> scale;
		final protected Set<K> orig;
		final protected int capacity;

		int weight = 0;

		public Bin(int cap, Scale<K, ?> sc, Object i, Set<K> o) {
			super(sc);
			capacity = cap;
			scale = sc;
			id = i;
			orig = o;
		}

		public boolean unchanged() {
			// proper implementations of equals() should not throw NullPointerException
			// but check null anyway, just in case...
			return orig != null && equals(orig);
		}

		public int filled() {
			return weight;
		}

		public int remainder() {
			return capacity - weight;
		}

		@Override public boolean add(K c) {
			if (super.add(c)) { weight += scale.getWeight(c); return true; }
			return false;
		}

		@Override public boolean remove(Object c) {
			if (super.remove(c)) { weight -= scale.getWeight((K)c); return true; }
			return false;
		}

		/**
		** Descending comparator for total bin load, ie. ascending comparator
		** for remainding weight capacity. {@link DummyBin}s are treated as the
		** "heaviest", or "least empty" bin for its weight.
		*/
		@Override public int compareTo(Bin<K> bin) {
			if (this == bin) { return 0; }
			int f1 = filled(), f2 = bin.filled();
			if (f1 != f2) { return (f2 > f1)? 1: -1; }
			return (bin instanceof DummyBin)? -1: IdentityComparator.comparator.compare(this, bin);
		}

	}


	/************************************************************************
	** A class that pretends to be a bin with a certain capacity and weight.
	** This is used when we want a such a bin for some purpose (eg. as an
	** argument to a comparator) but we don't want to have to populate a real
	** bin to get the desired weight (which might be massive).
	*/
	public static class DummyBin<K> extends Bin<K> {

		public DummyBin(int cap, int w) {
			super(cap, null, null, null);
			weight = w;
		}

		@Override public boolean add(K k) {
			throw new UnsupportedOperationException("Dummy bins cannot be modified");
		}

		@Override public boolean remove(Object c) {
			throw new UnsupportedOperationException("Dummy bins cannot be modified");
		}

		/**
		** {@inheritDoc}
		*/
		@Override public int compareTo(Bin<K> bin) {
			if (this == bin) { return 0; }
			int f1 = filled(), f2 = bin.filled();
			if (f1 != f2) { return (f2 > f1)? 1: -1; }
			return (bin instanceof DummyBin)? IdentityComparator.comparator.compare(this, bin): 1;
		}
	}


	/************************************************************************
	** A class that provides a "weight" assignment for each key in a given map,
	** by reading either the data or the metadata of its associated task.
	*/
	abstract public static class Scale<K, T> extends IdentityComparator<K> {

		final protected Map<K, Integer> weights = new HashMap<K, Integer>();
		final protected Map<K, ? extends Task<T>> elements;
		final protected int BIN_CAP;

		// TODO maybe ? extends T, or something
		protected Scale(Map<K, ? extends Task<T>> elem, int cap) {
			elements = elem;
			BIN_CAP = cap;
		}

		/**
		** Return the weight of the given element.
		*/
		abstract public int weigh(T element);

		/**
		** Read the bin ID from the given metadata.
		*/
		public Object readMetaID(Object meta) {
			return ((Object[])meta)[0];
		}

		/**
		** Read the bin weight from the given metadata.
		*/
		public int readMetaWeight(Object meta) {
			return (Integer)((Object[])meta)[1];
		}

		/**
		** Construct the metadata for the given bin ID and weight.
		*/
		public Object makeMeta(Object id, int weight) {
			return new Object[]{id, new Integer(weight)};
		}

		/**
		** Get the weight assignment for a given key.
		*/
		public int getWeight(K key) {
			Integer i = weights.get(key);
			if (i == null) {
				Task<T> task = elements.get(key);
				if (task == null) {
					throw new IllegalArgumentException("This scale does not have a weight for " + key);
				} else if (task.data == null) {
					i = readMetaWeight(task.meta);
				} else {
					i = weigh(task.data);
				}
				if (i > BIN_CAP) {
					throw new IllegalArgumentException("Element greater than the capacity allowed: " + key);
				}
				weights.put(key, i);
			}
			return i;
		}

		/**
		** Compare keys by the weights of the element it maps to. The {@code
		** null} key is treated as the "lightest" key for its weight.
		*/
		public int compare(K k1, K k2) {
			if (k1 == k2) { return 0; }
			int a = getWeight(k1), b = getWeight(k2);
			if (a != b) { return (a < b)? -1: 1; }
			// treat the null key as the "least" element for its weight
			return (k1 == null)? -1: (k2 == null)? 1: super.compare(k1, k2);
		}

		/**
		** Set the weight for the {@code null} key. This is useful for when
		** you want to obtain a subset using tailSet() or headSet(), but don't
		** have a key with the desired weight at hand.
		*/
		public K makeDummyObject(int weight) {
			weights.put(null, weight);
			return null;
		}

	}


	/************************************************************************
	** Generates unique IDs for the bins. This generator assigns {@link Long}s
	** in sequence; registering any type of integer object reference will cause
	** future automatically-assigned IDs to be greater than that integer.
	**
	** This implementation cannot ensure uniqueness of IDs generated (with
	** respect to ones generated in a previous session), unless they are
	** explicitly registered using {@link #registerID(Object)}.
	*/
	public static class IDGenerator {

		protected long nextID;

		/**
		** Register an ID that was assigned in a previous session, so the
		** generator doesn't output that ID again.
		*/
		public Object registerID(Object o) {
			long id;
			if (o instanceof Integer) {
				id = (Integer)o;
			} else if (o instanceof Long) {
				id = (Long)o;
			} else if (o instanceof Short) {
				id = (Short)o;
			} else if (o instanceof Byte) {
				id = (Byte)o;
			} else {
				return o;
			}
			if (id > nextID) {
				nextID = id+1;
			}
			return o;
		}

		/**
		** Generate another ID.
		*/
		public Long nextID() {
			return nextID++;
		}

	}

}
