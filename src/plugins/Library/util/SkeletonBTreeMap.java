/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import plugins.Library.serial.Serialiser.*;
import plugins.Library.serial.IterableSerialiser;
import plugins.Library.serial.ScheduledSerialiser;
import plugins.Library.serial.MapSerialiser;
import plugins.Library.serial.Translator;
import plugins.Library.serial.DataFormatException;
import plugins.Library.serial.TaskAbortException;
import plugins.Library.serial.TaskCompleteException;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;

// URGENT tidy this
import java.util.Queue;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import plugins.Library.serial.TaskCompleteException;
import plugins.Library.serial.BaseCompositeProgress;
import plugins.Library.serial.Serialiser;
import plugins.Library.serial.Progress;
import plugins.Library.serial.ProgressTracker;
import plugins.Library.util.concurrent.Scheduler;

/**
** {@link Skeleton} of a {@link BTreeMap}. DOCUMENT
**
** @author infinity0
*/
public class SkeletonBTreeMap<K, V> extends BTreeMap<K, V> implements SkeletonMap<K, V> {

	/*
	** Whether entries are "internal to" or "contained within" nodes, ie.
	** are the entries for a node completely stored (including values) with
	** that node in the serialised representation, or do they refer to other
	** serialised data that is external to the node?
	**
	** This determines whether a {@link TreeMap} or a {@link SkeletonTreeMap}
	** is used to back the entries in a node.
	**
	** Eg. a {@code BTreeMap<String, BTreeSet<TermEntry>>} would have this
	** {@code true} for the map, and {@code false} for the map backing the set.

	//final protected boolean internal_entries;

	TODO disable for now, since I can't think of a good way to implement
	** this tidily.
	**
	** three options:
	**
	** 0 have SkeletonNode use TreeMap when int_ent is true rather than
	**   SkeletonTreeMap but this will either break the Skeleton contract of
	**   deflate(), which expects isBare() to be true afterwards, or it will
	**   break the contract of isBare(), if we modifiy that method to return
	**   true for TreeMaps instead.
	**
	**   - pros: uses an existing class, efficient
	**   - cons: breaks contracts (no foreseeable way to avoid), complicated
	**     to implement
	**
	** 1 have another class that extends SkeletonTreeMap which has one single
	**   boolean value isDeflated, alias *flate(K) to *flate(), and all those
	**   functions do is set that boolean. then override get() etc to throw
	**   DNLEx depending on the value of that boolean; and have SkeletonNode
	**   use this class when int_ent is true.
	**
	**   - pros: simple, efficient OPTIMISE PRIORITY
	**   - cons: requires YetAnotherClass
	**
	** 2 don't have the internal_entries, and just use a dummy serialiser that
	**   copies task.data to task.meta for push tasks, and vice versa for pull
	**   tasks.
	**
	**   - pros: simple to implement
	**   - cons: a hack, inefficient
	**
	** for now using option 2, will probably implement option 1 at some point..
	*/

	/**
	** Serialiser for the node objects.
	*/
	protected IterableSerialiser<SkeletonNode> nsrl;

	/**
	** Serialiser for the value objects.
	*/
	protected MapSerialiser<K, V> vsrl;

	public void setSerialiser(IterableSerialiser<SkeletonNode> n, MapSerialiser<K, V> v) {
		if ((nsrl != null || vsrl != null) && !isLive()) {
			throw new IllegalStateException("Cannot change the serialiser when the structure is not live.");
		}
		nsrl = n;
		vsrl = v;
		((SkeletonNode)root).setSerialiser();
	}

	public class SkeletonNode extends Node implements Skeleton<K, IterableSerialiser<SkeletonNode>> {

		int ghosts = 0;

		SkeletonNode(boolean leaf, SkeletonTreeMap<K, V> map) {
			super(leaf, map);
			setSerialiser();
		}

		SkeletonNode(boolean leaf) {
			this(leaf, new SkeletonTreeMap<K, V>(comparator));
		}

		/**
		** Set the value-serialiser for this node and all subnodes to match
		** the one assigned for the entire tree.
		*/
		public void setSerialiser() {
			((SkeletonTreeMap<K, V>)entries).setSerialiser(vsrl);
			if (!isLeaf()) {
				for (Node n: rnodes.values()) {
					if (n.entries != null) {
						((SkeletonNode)n).setSerialiser();
					}
				}
			}
		}

		/**
		** Create a {@link GhostNode} object that represents this node.
		*/
		public GhostNode makeGhost(Object meta) {
			GhostNode ghost = new GhostNode(lkey, rkey, totalSize());
			ghost.setMeta(meta);
			return ghost;
		}

		@Override public Object getMeta() { return null; }

		@Override public void setMeta(Object m) { }

		@Override public IterableSerialiser<SkeletonNode> getSerialiser() { return nsrl; }

		@Override public boolean isLive() {
			if (ghosts > 0 || !((SkeletonTreeMap<K, V>)entries).isLive()) { return false; }
			if (!isLeaf()) {
				for (Node n: rnodes.values()) {
					SkeletonNode skel = (SkeletonNode)n;
					if (!skel.isLive()) { return false; }
				}
			}
			return true;
		}

		@Override public boolean isBare() {
			if (!isLeaf()) {
				if (ghosts < rnodes.size()) {
					return false;
				}
			}
			return ((SkeletonTreeMap<K, V>)entries).isBare();
		}

		@Override public void deflate() throws TaskAbortException {
			if (!isLeaf()) {
				List<PushTask<SkeletonNode>> tasks = new ArrayList<PushTask<SkeletonNode>>(rnodes.size() - ghosts);
				for (Map.Entry<K, Node> en: rnodes.entrySet()) {
					Node node = en.getValue();
					if (node.entries == null) { continue; } // ghost node
					if (!((SkeletonNode)node).isBare()) {
						((SkeletonNode)node).deflate();
					}
					tasks.add(new PushTask<SkeletonNode>((SkeletonNode)node));
				}

				nsrl.push(tasks);
				for (PushTask<SkeletonNode> task: tasks) {
					try {
						GhostNode ghost = (GhostNode)task.meta;
						ghost.parent = this;

						lnodes.put(ghost.rkey, ghost);
						rnodes.put(ghost.lkey, ghost);
						++ghosts;

					} catch (RuntimeException e) {
						throw new TaskAbortException("Could not deflate BTreeMap Node " + getRange(), e);
					}
				}
			}
			((SkeletonTreeMap<K, V>)entries).deflate();
			assert(isBare());
		}

		// OPTIMISE make this parallel
		@Override public void inflate() throws TaskAbortException {
			((SkeletonTreeMap<K, V>)entries).inflate();
			if (!isLeaf()) {
				for (K k: rnodes.keySet()) {
					inflate(k, true);
				}
			}
			assert(isLive());
		}

		@Override public void inflate(K key) throws TaskAbortException {
			inflate(key, false);
		}

		/**
		** Deflates the node to the immediate right of the given key.
		**
		** Expects metadata to be of type {@link GhostNode}.
		**
		** @param key The key
		** @param auto Whether to recursively deflate the node's subnodes.
		*/
		@Override public void deflate(K key) throws TaskAbortException {
			if (isLeaf()) { return; }
			Node node = rnodes.get(key);
			if (node.entries == null) { return; } // ghost node

			if (!((SkeletonNode)node).isBare()) {
				throw new IllegalStateException("Cannot deflate non-bare BTreeMap node");
			}

			PushTask<SkeletonNode> task = new PushTask<SkeletonNode>((SkeletonNode)node);
			try {
				nsrl.push(task);

				GhostNode ghost = (GhostNode)task.meta;
				ghost.parent = this;

				lnodes.put(ghost.rkey, ghost);
				rnodes.put(ghost.lkey, ghost);
				++ghosts;

			// TODO maybe just ignore all non-error abortions
			} catch (TaskCompleteException e) {
				assert(node.entries == null);
			} catch (RuntimeException e) {
				throw new TaskAbortException("Could not deflate BTreeMap Node " + node.getRange(), e);
			}
		}

		/**
		** Inflates the node to the immediate right of the given key.
		**
		** Passes metadata of type {@link GhostNode}.
		**
		** @param key The key
		** @param auto Whether to recursively inflate the node's subnodes.
		*/
		public void inflate(K key, boolean auto) throws TaskAbortException {
			if (isLeaf()) { return; }
			Node node = rnodes.get(key);
			if (node.entries != null) { return; } // skeleton node

			PullTask<SkeletonNode> task = new PullTask<SkeletonNode>(node);
			try {
				nsrl.pull(task);

				if (!compare0(node.lkey, task.data.lkey) || !compare0(node.rkey, task.data.rkey)) {
					throw new DataFormatException("BTreeMap Node lkey/rkey does not match", null, task.data);
				}

				lnodes.put(task.data.rkey, task.data);
				rnodes.put(task.data.lkey, task.data);
				--ghosts;

				if (auto) {
					task.data.inflate();
				}

			} catch (TaskCompleteException e) {
				assert(rnodes.get(key).entries != null);
			} catch (RuntimeException e) {
				throw new TaskAbortException("Could not inflate BTreeMap Node " + node.getRange(), e);
			}
		}

	}

	public class GhostNode extends Node {

		SkeletonNode parent;
		Object meta;

		GhostNode(SkeletonNode p, K l, K r, int s) {
			super(false, null);
			parent = p;
			lkey = l;
			rkey = r;
			_size = s;
		}

		GhostNode(K l, K r, int s) {
			this(null, l, r, s);
		}

		public Object getMeta() {
			return meta;
		}

		public void setMeta(Object m) {
			meta = m;
		}

		@Override int nodeSize() {
			throw new DataNotLoadedException("BTreeMap Node not loaded: " + getRange(), parent, lkey, this);
		}

		@Override boolean isLeaf() {
			throw new DataNotLoadedException("BTreeMap Node not loaded: " + getRange(), parent, lkey, this);
		}

		@Override Node nodeL(Node n) {
			// this method-call should never be reached in the B-tree algorithm
			assert(false);
			throw new IllegalStateException("This method call should never be reached");
		}

		@Override Node nodeR(Node n) {
			// this method-call should never be reached in the B-tree algorithm
			assert(false);
			throw new IllegalStateException("This method call should never be reached");
		}

		@Override Node selectNode(K key) {
			// this method-call should never be reached in the B-tree algorithm
			assert(false);
			throw new IllegalStateException("This method call should never be reached");
		}

	}

	public SkeletonBTreeMap(Comparator<? super K> cmp, int node_min) {
		super(cmp, node_min);
	}

	public SkeletonBTreeMap(int node_min) {
		super(node_min);
	}

	@Override protected Node newNode(boolean leaf) {
		return new SkeletonNode(leaf);
	}

	/*========================================================================
	  public interface SkeletonMap
	 ========================================================================*/

	@Override public Object getMeta() { return null; }

	@Override public void setMeta(Object m) { }

	@Override public MapSerialiser<K, V> getSerialiser() { return vsrl; }

	@Override public boolean isLive() {
		return ((SkeletonNode)root).isLive();
	}

	@Override public boolean isBare() {
		return ((SkeletonNode)root).isBare();
	}

	@Override public void deflate() throws TaskAbortException {
		((SkeletonNode)root).deflate();
	}

	// URGENT tidy this; this should proboably go in a serialiser
	// and then we will access the Progress of a submap with a task whose
	// metadata is (lkey, rkey), or something..(PROGRESS)
	BaseCompositeProgress ppp = new BaseCompositeProgress();
	public BaseCompositeProgress getPPP() { return ppp; } // REMOVE ME
	@Override public void inflate() throws TaskAbortException {

		// TODO adapt the algorithm to track partial loads of submaps (SUBMAP)
		// TODO if we do that, we'll also need to make it thread-safe. (THREAD)
		// TODO and do the PROGRESS stuff whilst we're at it

		if (!(nsrl instanceof ScheduledSerialiser)) {
			// TODO could ideally use the below code, and since the Scheduler would be
			// unavailable, just execute the tasks in the current thread. the priority
			// queue's comparator would turn it into depth-first search automatically.
			((SkeletonNode)root).inflate();
			return;
		}

		Queue<SkeletonNode> nodequeue = new PriorityQueue<SkeletonNode>();
		BlockingQueue<PullTask<SkeletonNode>> tasks = new LinkedBlockingQueue<PullTask<SkeletonNode>>(0x10);
		BlockingQueue<PullTask<SkeletonNode>> inflated = new PriorityBlockingQueue<PullTask<SkeletonNode>>(0x10,
			new Comparator<PullTask<SkeletonNode>>() {
				@Override public int compare(PullTask<SkeletonNode> t1, PullTask<SkeletonNode> t2) {
					return t1.data.compareTo(t2.data);
				}
			}
		);
		ConcurrentMap<PullTask<SkeletonNode>, TaskAbortException> error = new ConcurrentHashMap<PullTask<SkeletonNode>, TaskAbortException>();

		Map<PullTask<SkeletonNode>, ProgressTracker<SkeletonNode, ?>> ids = null;
		ProgressTracker<SkeletonNode, ?> ntracker = null;;

		if (nsrl instanceof Serialiser.Trackable) {
			ids = new LinkedHashMap<PullTask<SkeletonNode>, ProgressTracker<SkeletonNode, ?>>();
			ntracker = ((Serialiser.Trackable)nsrl).getTracker();
			// PROGRESS make a ProgressTracker track this instead of "ppp".
			ppp.setSubProgress(ProgressTracker.makePullProgressIterable(ids));
			ppp.setSubject("Pulling all entries in B-tree");
		}

		Scheduler pool = ((ScheduledSerialiser)nsrl).pullSchedule(tasks, inflated, error);
		//System.out.println("Using scheduler");
		//int DEBUG_pushed = 0, DEBUG_popped = 0;

		try {
			nodequeue.add((SkeletonNode)root);

			do {

				for (Map.Entry<PullTask<SkeletonNode>, TaskAbortException> en: error.entrySet()) {
					assert(!(en.getValue() instanceof plugins.Library.serial.TaskInProgressException)); // by contract of ScheduledSerialiser
					if (!(en.getValue() instanceof TaskCompleteException)) {
						// TODO maybe dump it somewhere else and throw it at the end...
						throw en.getValue();
					} else {
						// retrieve the inflated SkeletonNode and add it to the queue...
						GhostNode ghost = (GhostNode)en.getKey().meta;
						SkeletonNode parent = ghost.parent;
						// THREAD race condition here... if another thread has inflated the task
						// but not yet attached the inflated node to the tree, the assertion fails.
						// could check to see if the Progress for the Task still exists, but the
						// performance of this depends on the GC freeing weak referents quickly...
						assert(parent.rnodes.get(ghost.lkey).entries != null);
						nodequeue.add((SkeletonNode)parent.rnodes.get(ghost.lkey));
						//++DEBUG_popped; // not actually popped off the map, but we've "taken care" of it
					}
				}

				// handle the inflated tasks and attach them to the tree.
				while (!inflated.isEmpty()) {
					// THREAD progress tracker should prevent this from being run twice for the
					// same node, but what if we didn't use a progress tracker? hmm...

					// try until one pops, or we are done
					final PullTask<SkeletonNode> task = inflated.poll(1, TimeUnit.SECONDS);
					if (task == null) { continue; }
					//++DEBUG_popped;

					SkeletonNode node = task.data;
					GhostNode ghost = (GhostNode)task.meta;
					SkeletonNode parent = ghost.parent;

					// attach task data into the parent
					if (!compare0(ghost.lkey, node.lkey) || !compare0(ghost.rkey, node.rkey)) {
						throw new DataFormatException("BTreeMap Node lkey/rkey does not match", null, task.data);
					}
					parent.lnodes.put(node.rkey, node);
					parent.rnodes.put(node.lkey, node);
					--parent.ghosts;

					nodequeue.add(node);
				}

				// go through the nodequeue and add any child ghost nodes to the tasks queue
				while (!nodequeue.isEmpty()) {
					SkeletonNode node = nodequeue.remove();
					((SkeletonTreeMap<K, V>)node.entries).inflate(); // SUBMAP here

					if (node.isLeaf()) { continue; }
					// add any ghost nodes to the task queue
					for (Map.Entry<K, Node> en: node.rnodes.entrySet()) { // SUBMAP here
						Node next = en.getValue();
						if (next.entries != null) {
							SkeletonNode skel = (SkeletonNode)next;
							if (!skel.isLive()) { nodequeue.add(skel); }
							continue;
						}
						PullTask<SkeletonNode> task = new PullTask<SkeletonNode>((GhostNode)next);
						if (ids != null) { ids.put(task, ntracker); }
						tasks.put(task);
						//++DEBUG_pushed;
					}
				}

				// nodequeue is empty, but tasks may have inflated in the meantime

			// URGENT there maybe is a race condition here... see BIndexTest.fullInflate() for details
			} while (pool.isActive() || !tasks.isEmpty() || !inflated.isEmpty() || !error.isEmpty());

		} catch (InterruptedException e) {
			throw new TaskAbortException("interrupted", e);
		} finally {
			pool.close();
			//System.out.println("pushed: " + DEBUG_pushed + "; popped: " + DEBUG_popped);
			//assert(DEBUG_pushed == DEBUG_popped);
		}
	}

	@Override public void deflate(K key) throws TaskAbortException {
		// TODO code this
		throw new UnsupportedOperationException("not implemented");
	}

	@Override public void inflate(K key) throws TaskAbortException {
		// TODO tidy up
		// OPTIMISE could write a more efficient version by keeping track of the
		// already-inflated nodes so get() doesn't keep traversing down the tree
		// - would only improve performance from O(log(n)^2) to O(log(n)) so not
		// that big a priority
		for (;;) {
			try {
				get(key);
				break;
			} catch (DataNotLoadedException e) {
				e.getParent().inflate(e.getKey());
			}
		}
	}


	/**
	** Creates a translator for the nodes of the B-tree. This method is
	** necessary because {@link NodeTranslator} is a non-static class.
	**
	** For an in-depth discussion on why that class is not static, see the
	** class description for {@link BTreeMap.Node}.
	**
	** TODO maybe store these in a WeakHashSet or something... will need to
	** code equals() and hashCode() for that
	**
	** @param ktr Translator for the keys
	** @param mtr Translator for each node's local entries map
	*/
	public <Q, R> NodeTranslator<Q, R> makeNodeTranslator(Translator<K, Q> ktr, Translator<SkeletonTreeMap<K, V>, R> mtr) {
		return new NodeTranslator<Q, R>(ktr, mtr);
	}

	/************************************************************************
	** DOCUMENT.
	**
	** For an in-depth discussion on why this class is not static, see the
	** class description for {@link BTreeMap.Node}.
	**
	** @param <Q> Target type of key-translator
	** @param <R> Target type of map-translater
	** @author infinity0
	*/
	public class NodeTranslator<Q, R> implements Translator<SkeletonNode, Map<String, Object>> {

		/**
		** An optional translator for the keys.
		*/
		final Translator<K, Q> ktr;

		/**
		** An optional translator for each node's local entries map.
		*/
		final Translator<SkeletonTreeMap<K, V>, R> mtr;

		public NodeTranslator(Translator<K, Q> k, Translator<SkeletonTreeMap<K, V>, R> m) {
			ktr = k;
			mtr = m;
		}

		@Override public Map<String, Object> app(SkeletonNode node) {
			if (!node.isBare()) {
				throw new IllegalStateException("Cannot translate non-bare node " + node.getRange());
			}
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			map.put("lkey", (ktr == null)? node.lkey: ktr.app(node.lkey));
			map.put("rkey", (ktr == null)? node.rkey: ktr.app(node.rkey));
			map.put("entries", (mtr == null)? node.entries: mtr.app((SkeletonTreeMap<K, V>)node.entries));

			if (!node.isLeaf()) {
				Map<Object, Integer> subnodes = new LinkedHashMap<Object, Integer>();
				for (K k: node.entries.keySet()) {
					GhostNode gh = (GhostNode)node.lnodes.get(k);
					subnodes.put(gh.getMeta(), gh.totalSize());
				}
				GhostNode gh = (GhostNode)node.lnodes.get(node.rkey);
				subnodes.put(gh.getMeta(), gh.totalSize());
				map.put("subnodes", subnodes);
			}
			return map;
		}

		@Override public SkeletonNode rev(Map<String, Object> map) {
			try {
				SkeletonNode node = new SkeletonNode(!map.containsKey("subnodes"),
				                                     (mtr == null)? (SkeletonTreeMap<K, V>)map.get("entries")
				                                                  : mtr.rev((R)map.get("entries")));
				node.lkey = (ktr == null)? (K)map.get("lkey"): ktr.rev((Q)map.get("lkey"));
				node.rkey = (ktr == null)? (K)map.get("rkey"): ktr.rev((Q)map.get("rkey"));
				node._size = node.entries.size();
				if (!node.isLeaf()) {
					Map<Object, Integer> subnodes = (Map<Object, Integer>)map.get("subnodes");
					K lastkey = node.lkey;
					Iterator<K> keys = node.entries.keySet().iterator();
					node.ghosts = subnodes.size();
					for (Map.Entry<Object, Integer> en: subnodes.entrySet()) {
						K thiskey = keys.hasNext()? keys.next(): node.rkey;
						GhostNode ghost = new GhostNode(node, lastkey, thiskey, en.getValue());
						ghost.setMeta(en.getKey());
						node.rnodes.put(lastkey, ghost);
						node.lnodes.put(thiskey, ghost);
						lastkey = thiskey;
						node._size += en.getValue();
					}
				}
				verifyNodeIntegrity(node);
				return node;
			} catch (ClassCastException e) {
				throw new DataFormatException("Could not build SkeletonNode from data", e, map, null, null);
			} catch (IllegalStateException e) {
				throw new DataFormatException("Could not build SkeletonNode from data", e, map, null, null);
			}
		}

	}


	/************************************************************************
	** {@link Translator} with access to the members of {@link BTreeMap}.
	** DOCUMENT.
	**
	** @author infinity0
	*/
	public static class TreeTranslator<K, V> implements Translator<SkeletonBTreeMap<K, V>, Map<String, Object>> {

		final Translator<K, ?> ktr;
		final Translator<SkeletonTreeMap<K, V>, ?> mtr;

		public TreeTranslator(Translator<K, ?> k, Translator<SkeletonTreeMap<K, V>, ?> m) {
			ktr = k;
			mtr = m;
		}

		@Override public Map<String, Object> app(SkeletonBTreeMap<K, V> tree) {
			if (tree.comparator() != null) {
				throw new UnsupportedOperationException("Sorry, this translator does not (yet) support comparators");
			}
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			map.put("node_min", tree.NODE_MIN);
			map.put("size", tree.size);
			Map<String, Object> rmap = tree.makeNodeTranslator(ktr, mtr).app((SkeletonBTreeMap.SkeletonNode)tree.root);
			map.put("entries", rmap.get("entries"));
			if (!tree.root.isLeaf()) {
				map.put("subnodes", rmap.get("subnodes"));
			}
			return map;
		}

		@Override public SkeletonBTreeMap<K, V> rev(Map<String, Object> map) {
			try {
				SkeletonBTreeMap<K, V> tree = new SkeletonBTreeMap<K, V>((Integer)map.get("node_min"));
				tree.size = (Integer)map.get("size");
				// map.put("lkey", null); // NULLNOTICE: get() gives null which matches
				// map.put("rkey", null); // NULLNOTICE: get() gives null which matches
				tree.root = tree.makeNodeTranslator(ktr, mtr).rev(map);
				if (tree.size != tree.root.totalSize()) {
					throw new DataFormatException("Mismatched sizes - tree: " + tree.size + "; root: " + tree.root.totalSize(), null, null);
				}
				return tree;
			} catch (ClassCastException e) {
				throw new DataFormatException("Could not build SkeletonBTreeMap from data", e, map, null, null);
			}
		}

	}

}
