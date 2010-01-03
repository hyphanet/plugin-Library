/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import plugins.Library.io.serial.Serialiser.*;
import plugins.Library.io.serial.IterableSerialiser;
import plugins.Library.io.serial.ScheduledSerialiser;
import plugins.Library.io.serial.MapSerialiser;
import plugins.Library.io.serial.Translator;
import plugins.Library.io.DataFormatException;
import plugins.Library.util.exec.TaskAbortException;
import plugins.Library.util.exec.TaskCompleteException;
import plugins.Library.util.func.Tuples.$2;
import plugins.Library.util.func.Tuples.$3;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Collection;
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
import plugins.Library.util.exec.Progress;
import plugins.Library.util.exec.ProgressParts;
import plugins.Library.util.exec.BaseCompositeProgress;
import plugins.Library.io.serial.Serialiser;
import plugins.Library.io.serial.ProgressTracker;
import plugins.Library.util.exec.TaskCompleteException;
import plugins.Library.util.concurrent.Scheduler;

import java.util.SortedSet;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.HashMap;
import plugins.Library.util.Maps;
import plugins.Library.util.Sorted;
import plugins.Library.util.event.TrackingSweeper;
import plugins.Library.util.event.CountingSweeper;
import plugins.Library.util.func.Closure;
import plugins.Library.util.func.SafeClosure;
import plugins.Library.util.func.Tuples.$2;


/**
** {@link Skeleton} of a {@link BTreeMap}. DOCUMENT
**
** TODO get rid of uses of rnode.get(K). All other uses of rnode, as well as
** lnode and entries, have already been removed. This will allow us to
** re-implement the Node class.
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
	*/
	//final protected boolean internal_entries;
	/*
	** TODO disable for now, since I can't think of a good way to implement
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

		protected int ghosts = 0;

		protected SkeletonNode(K lk, K rk, boolean lf, SkeletonTreeMap<K, V> map) {
			super(lk, rk, lf, map);
			setSerialiser();
		}

		protected SkeletonNode(K lk, K rk, boolean lf) {
			this(lk, rk, lf, new SkeletonTreeMap<K, V>(comparator));
		}

		protected SkeletonNode(K lk, K rk, boolean lf, SkeletonTreeMap<K, V> map, Collection<GhostNode> gh) {
			this(lk, rk, lf, map);
			_size = map.size();
			if (!lf) {
				if (map.size()+1 != gh.size()) {
					throw new IllegalArgumentException("SkeletonNode: in constructing " + getName() + ", got size mismatch: map:" + map.size() + "; gh:" + gh.size());
				}
				Iterator<GhostNode> it = gh.iterator();
				for ($2<K, K> kp: iterKeyPairs()) {
					GhostNode ghost = it.next();
					ghost.lkey = kp._0;
					ghost.rkey = kp._1;
					ghost.parent = this;
					_size += ghost._size;
					addChildNode(ghost);
				}
				ghosts = gh.size();
			}
		}

		/**
		** Set the value-serialiser for this node and all subnodes to match
		** the one assigned for the entire tree.
		*/
		public void setSerialiser() {
			((SkeletonTreeMap<K, V>)entries).setSerialiser(vsrl);
			if (!isLeaf()) {
				for (Node n: iterNodes()) {
					if (!n.isGhost()) {
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

		/*@Override**/ public Object getMeta() { return null; }

		/*@Override**/ public void setMeta(Object m) { }

		/*@Override**/ public IterableSerialiser<SkeletonNode> getSerialiser() { return nsrl; }

		/*@Override**/ public boolean isLive() {
			if (ghosts > 0 || !((SkeletonTreeMap<K, V>)entries).isLive()) { return false; }
			if (!isLeaf()) {
				for (Node n: iterNodes()) {
					SkeletonNode skel = (SkeletonNode)n;
					if (!skel.isLive()) { return false; }
				}
			}
			return true;
		}

		/*@Override**/ public boolean isBare() {
			if (!isLeaf()) {
				if (ghosts < childCount()) {
					return false;
				}
			}
			return ((SkeletonTreeMap<K, V>)entries).isBare();
		}

		/**
		** Attaches a child {@link GhostNode}.
		**
		** It is '''assumed''' that there is already a {@link SkeletonNode} in
		** its place; the ghost will replace it. It is up to the caller to
		** ensure that this holds.
		**
		** @param ghost The GhostNode to attach
		*/
		protected void attachGhost(GhostNode ghost) {
			assert(!rnodes.get(ghost.lkey).isGhost());
			ghost.parent = this;
			setChildNode(ghost);
			++ghosts;
		}

		/**
		** Attaches a child {@link SkeletonNode}.
		**
		** It is '''assumed''' that there is already a {@link GhostNode} in its
		** place; the skeleton will replace it. It is up to the caller to
		** ensure that this holds.
		**
		** @param skel The SkeletonNode to attach
		*/
		protected void attachSkeleton(SkeletonNode skel) {
			assert(rnodes.get(skel.lkey).isGhost());
			setChildNode(skel);
			--ghosts;
		}

		/*@Override**/ public void deflate() throws TaskAbortException {
			if (!isLeaf()) {
				List<PushTask<SkeletonNode>> tasks = new ArrayList<PushTask<SkeletonNode>>(childCount() - ghosts);
				for (Node node: iterNodes()) {
					if (node.isGhost()) { continue; }
					if (!((SkeletonNode)node).isBare()) {
						((SkeletonNode)node).deflate();
					}
					tasks.add(new PushTask<SkeletonNode>((SkeletonNode)node));
				}

				nsrl.push(tasks);
				for (PushTask<SkeletonNode> task: tasks) {
					try {
						attachGhost((GhostNode)task.meta);
					} catch (RuntimeException e) {
						throw new TaskAbortException("Could not deflate BTreeMap Node " + getRange(), e);
					}
				}
			}
			((SkeletonTreeMap<K, V>)entries).deflate();
			assert(isBare());
		}

		// OPTIMISE make this parallel
		/*@Override**/ public void inflate() throws TaskAbortException {
			((SkeletonTreeMap<K, V>)entries).inflate();
			if (!isLeaf()) {
				for (Node node: iterNodes()) {
					inflate(node.lkey, true);
				}
			}
			assert(isLive());
		}

		/*@Override**/ public void inflate(K key) throws TaskAbortException {
			inflate(key, false);
		}

		/**
		** Deflates the node to the immediate right of the given key.
		**
		** Expects metadata to be of type {@link GhostNode}.
		**
		** @param key The key
		*/
		/*@Override**/ public void deflate(K key) throws TaskAbortException {
			if (isLeaf()) { return; }
			Node node = rnodes.get(key);
			if (node.isGhost()) { return; }

			if (!((SkeletonNode)node).isBare()) {
				throw new IllegalStateException("Cannot deflate non-bare BTreeMap node");
			}

			PushTask<SkeletonNode> task = new PushTask<SkeletonNode>((SkeletonNode)node);
			try {
				nsrl.push(task);
				attachGhost((GhostNode)task.meta);

			// TODO maybe just ignore all non-error abortions
			} catch (TaskCompleteException e) {
				assert(node.isGhost());
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
			if (!node.isGhost()) { return; }

			PullTask<SkeletonNode> task = new PullTask<SkeletonNode>(node);
			try {
				nsrl.pull(task);

				if (!compare0(node.lkey, task.data.lkey) || !compare0(node.rkey, task.data.rkey)) {
					throw new DataFormatException("BTreeMap Node lkey/rkey does not match", null, task.data);
				}

				attachSkeleton(task.data);

				if (auto) {
					task.data.inflate();
				}

			} catch (TaskCompleteException e) {
				assert(!node.isGhost());
			} catch (DataFormatException e) {
				throw new TaskAbortException("Could not inflate BTreeMap Node " + node.getRange(), e);
			} catch (RuntimeException e) {
				throw new TaskAbortException("Could not inflate BTreeMap Node " + node.getRange(), e);
			}
		}

	}

	public class GhostNode extends Node {

		protected SkeletonNode parent;
		protected Object meta;

		protected GhostNode(K lk, K rk, SkeletonNode p, int s) {
			super(lk, rk, false, null);
			parent = p;
			_size = s;
		}

		protected GhostNode(K lk, K rk, int s) {
			this(lk, rk, null, s);
		}

		public Object getMeta() {
			return meta;
		}

		public void setMeta(Object m) {
			meta = m;
		}

		@Override public int nodeSize() {
			throw new DataNotLoadedException("BTreeMap Node not loaded: " + getRange(), parent, lkey, this);
		}

		@Override public int childCount() {
			throw new DataNotLoadedException("BTreeMap Node not loaded: " + getRange(), parent, lkey, this);
		}

		@Override public boolean isLeaf() {
			throw new DataNotLoadedException("BTreeMap Node not loaded: " + getRange(), parent, lkey, this);
		}

		@Override public Node nodeL(Node n) {
			// this method-call should never be reached in the B-tree algorithm
			throw new AssertionError("GhostNode: called nodeL()");
		}

		@Override public Node nodeR(Node n) {
			// this method-call should never be reached in the B-tree algorithm
			throw new AssertionError("GhostNode: called nodeR()");
		}

		@Override public Node selectNode(K key) {
			// this method-call should never be reached in the B-tree algorithm
			throw new AssertionError("GhostNode: called selectNode()");
		}

	}

	public SkeletonBTreeMap(Comparator<? super K> cmp, int node_min) {
		super(cmp, node_min);
	}

	public SkeletonBTreeMap(int node_min) {
		super(node_min);
	}

	@Override protected Node newNode(K lk, K rk, boolean lf) {
		return new SkeletonNode(lk, rk, lf);
	}

	/*========================================================================
	  public interface SkeletonMap
	 ========================================================================*/

	/*@Override**/ public Object getMeta() { return null; }

	/*@Override**/ public void setMeta(Object m) { }

	/*@Override**/ public MapSerialiser<K, V> getSerialiser() { return vsrl; }

	/*@Override**/ public boolean isLive() {
		return ((SkeletonNode)root).isLive();
	}

	/*@Override**/ public boolean isBare() {
		return ((SkeletonNode)root).isBare();
	}

	/*@Override**/ public void deflate() throws TaskAbortException {
		((SkeletonNode)root).deflate();
	}

	// URGENT tidy this; this should proboably go in a serialiser
	// and then we will access the Progress of a submap with a task whose
	// metadata is (lkey, rkey), or something..(PROGRESS)
	BaseCompositeProgress pr_inf = new BaseCompositeProgress();
	public BaseCompositeProgress getProgressInflate() { return pr_inf; } // REMOVE ME
	/*@Override**/ public void inflate() throws TaskAbortException {

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
				/*@Override**/ public int compare(PullTask<SkeletonNode> t1, PullTask<SkeletonNode> t2) {
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
			// PROGRESS make a ProgressTracker track this instead of "pr_inf".
			pr_inf.setSubProgress(ProgressTracker.makePullProgressIterable(ids));
			pr_inf.setSubject("Pulling all entries in B-tree");
		}

		Scheduler pool = ((ScheduledSerialiser)nsrl).pullSchedule(tasks, inflated, error);
		//System.out.println("Using scheduler");
		//int DEBUG_pushed = 0, DEBUG_popped = 0;

		try {
			nodequeue.add((SkeletonNode)root);

			do {

				for (Map.Entry<PullTask<SkeletonNode>, TaskAbortException> en: error.entrySet()) {
					assert(!(en.getValue() instanceof plugins.Library.util.exec.TaskInProgressException)); // by contract of ScheduledSerialiser
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
						assert(!parent.rnodes.get(ghost.lkey).isGhost());
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
					parent.attachSkeleton(node);
					nodequeue.add(node);
				}

				// go through the nodequeue and add any child ghost nodes to the tasks queue
				while (!nodequeue.isEmpty()) {
					SkeletonNode node = nodequeue.remove();
					((SkeletonTreeMap<K, V>)node.entries).inflate(); // SUBMAP here

					if (node.isLeaf()) { continue; }
					// add any ghost nodes to the task queue
					for (Node next: node.iterNodes()) { // SUBMAP here
						if (!next.isGhost()) {
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

			pr_inf.setEstimate(ProgressParts.TOTAL_FINALIZED);

		} catch (DataFormatException e) {
			throw new TaskAbortException("Bad data format", e);
		} catch (InterruptedException e) {
			throw new TaskAbortException("interrupted", e);
		} finally {
			pool.close();
			//System.out.println("pushed: " + DEBUG_pushed + "; popped: " + DEBUG_popped);
			//assert(DEBUG_pushed == DEBUG_popped);
		}
	}

	/*@Override**/ public void deflate(K key) throws TaskAbortException {
		// TODO code this
		throw new UnsupportedOperationException("not implemented");
	}

	/*@Override**/ public void inflate(K key) throws TaskAbortException {
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
	** Asynchronously updates a remote B-tree.
	**
	** Note: {@code remkey} is not implemented yet.
	**
	** @param putkey The keys to insert into the map
	** @param remkey The keys to remove from the map
	** @throws UnsupportedOperationException if {@code remkey} is not empty
	*/
	public void update(SortedSet<K> putkey, SortedSet<K> remkey) throws TaskAbortException {

		if (!remkey.isEmpty()) {
			throw new UnsupportedOperationException("SkeletonBTreeMap: update() currently only supports merge operations");
		}

		/*
		** The code below might seem confusing at first, because the action of
		** the algorithm on a single node is split up into several asynchronous
		** parts, which are not visually adjacent. Here is a more contiguous
		** description of what happens to a single node between being inflated
		** and then eventually deflated.
		**
		** Life cycle of a node:
		**
		** - node gets popped from inflated
		** - enter InflateChildNodes
		**   - subnodes get pushed into pull_queue
		**   - (recurse for each subnode)
		**     - subnode gets popped from inflated
		**     - etc
		**     - split-subnodes get pushed into push_queue
		** - wait for all:
		**   - split-subnodes get popped from deflated
		** - enter SplitNode
		**   - for each item in the original node's value_clo
		**     - release the item and acquire it on
		**       - the parent's value_clo if the item is a separator
		**       - a new value_clo if the item is now in a split-node
		** - for each split-node:
		**   - wait for all:
		**     - values get popped from values_complete
		**   - enter DeflateNode
		**     - split-node gets pushed into push_queue (except for root)
		**
		*/

		// TODO these queues need proper comparators. lowest key first.

		// TODO add a value-getter parameter

		// TODO URGENT OPTIMISE need a way of bypassing the value-getter so that we
		// can update the values synchronously (ie. when we don't need to retrieve
		// network data).

		// input queue for pull-scheduler
		final PriorityBlockingQueue<PullTask<Node>> pull_queue
		= new PriorityBlockingQueue<PullTask<Node>>();
		// output queue for pull-scheduler
		final PriorityBlockingQueue<PullTask<Node>> inflated
		= new PriorityBlockingQueue<PullTask<Node>>();
		// FIXME error maps

		// input queue for push-scheduler
		final PriorityBlockingQueue<PushTask<Node>> push_queue
		= new PriorityBlockingQueue<PushTask<Node>>();
		// output queue for push-scheduler
		final PriorityBlockingQueue<PushTask<Node>> deflated
		= new PriorityBlockingQueue<PushTask<Node>>();
		// FIXME error maps

		// input queue for value-getter
		final PriorityBlockingQueue<Map.Entry<K, V>> value_pending
		= new PriorityBlockingQueue<Map.Entry<K, V>>();
		// output queue for value-getter
		final PriorityBlockingQueue<Map.Entry<K, V>> value_complete
		= new PriorityBlockingQueue<Map.Entry<K, V>>();
		// FIXME error maps

		final Map<PullTask<Node>, SafeClosure<Node>>
		inflate_closures = new HashMap<PullTask<Node>, SafeClosure<Node>>();

		final Map<PushTask<Node>, CountingSweeper<Node>>
		split_closures = new HashMap<PushTask<Node>, CountingSweeper<Node>>();

		final Map<K, TrackingSweeper<K>>
		deflate_closures = new HashMap<K, TrackingSweeper<K>>();

		final Map<K, SafeClosure<Map.Entry<K, V>>>
		value_closures = new HashMap<K, SafeClosure<Map.Entry<K, V>>>();

		/**
		** Deflates a node whose values have all been obtained.
		**
		** To be called after everything else on it has been taken care of.
		*/
		class DeflateNode extends TrackingSweeper implements Runnable {

			final PushTask<Node> task;

			protected DeflateNode(PushTask<Node> t) {
				super(true);
				task = t;
			}

			public void run() {
				push_queue.put(task);
			}

		}

		/**
		** Updates a value that has been obtained.
		**
		** To be called after the value is popped from value_complete.
		*/
		class UpdateValue implements SafeClosure<Map.Entry<K, V>> {

			final Node node;

			public UpdateValue(Node n) {
				node = n;
			}

			public void invoke(Map.Entry<K, V> en) {
				assert(node.entries.containsKey(en.getKey()));
				node.entries.put(en.getKey(), en.getValue());
			}

		}

		/**
		** Splits a node.
		**
		** To be called after all its children have been taken care of.
		*/
		class SplitNode extends CountingSweeper<Node> implements Runnable {

			final Node node;
			final Node parent;
			final TrackingSweeper<K> nodeVClo;
			final CountingSweeper<Node> parNClo;
			final TrackingSweeper<K> parVClo;

			protected SplitNode(Node n, Node p, TrackingSweeper<K> vc, CountingSweeper<Node> pnc, TrackingSweeper<K> pvc) {
				super(true);
				node = n;
				parent = p;
				nodeVClo = vc;
				parNClo = pnc;
				parVClo = pvc;
			}

			public void run() {

				// All subnodes have been deflated, so nothing else can possibly add keys
				// to this node.
				nodeVClo.close();

				// TODO splitting crap, etc

				int sk = minKeysFor(node.nodeSize());
				// OPTIMISE if we don't need to split, then it would be better for nodeVClo
				// to be a DeflateNode rather than a dummy sweeper, so we don't have to
				// switch stuff for no reason.
				//
				// If we need to split at all, then we might as well not bother releasing
				// any keys from nodeVClo, and just let it get garbage collected. Currently
				// we release all the keys, but nodeVClo is a dummy with no use after this
				// method returns, so this is only useful to check that we got the code
				// right.
				Collection<K> keys = Sorted.select((SortedSet<K>)node.entries.keySet(), sk);
				assert(parent.lnodes.get(node.rkey) == node);
				assert(parent.rnodes.get(node.lkey) == node);
				parent.split(node.lkey, keys, node.rkey);
				Iterable<Node> nodes = parent.iterNodes(node.lkey, node.rkey);
				assert(parent.lnodes.get(node.rkey) != node);
				assert(parent.rnodes.get(node.lkey) != node);

				if (parent == null && sk > 0) {
					assert(parNClo == null && parVClo == null);
					// TODO create parent, parNClo, parVClo, etc
					// similar stuff as for InflateChildNodes but without the merging
				}

				// reassign appropriate keys to parent sweeper
				SafeClosure<Map.Entry<K, V>> kvClo = new UpdateValue(parent);
				for (K key: keys) {
					reassignKeyToSweeper(key, parVClo, kvClo);
				}

				// for each split-node, create a sweeper that will run when all its (k, v)
				// pairs have been popped from value_complete
				for (Node n: nodes) {
					PushTask<Node> task = new PushTask<Node>(n);
					DeflateNode vClo = new DeflateNode(task);
					kvClo = new UpdateValue(n);

					// reassign appropriate keys to the split-node's sweeper
					for (K k2 = null;k2 != null;/*TODO*/) {
						reassignKeyToSweeper(k2, vClo, kvClo);
					}
					vClo.close();

					parNClo.acquire(n);
					split_closures.put(task, parNClo);
				}

				assert(nodeVClo.isCleared() && !(nodeVClo instanceof Runnable));

				// original (unsplit) node had a ticket on the parNClo sweeper, release it
				parNClo.release(node);
			}

			/**
			** When we move a key to another node (eg. to the parent, or to a new node
			** resulting from the split), we must deassign it from the original node's
			** sweeper and reassign it to the sweeper for the new node.
			**
			** NOTE: if the overall co-ordinator algorithm is ever made concurrent,
			** this section MUST be made atomic
			**
			** @param key The key
			** @param clo The sweeper to reassign the key to
			** @param kvClo The (new) closure to run when the value is obtained
			*/
			private void reassignKeyToSweeper(K key, TrackingSweeper<K> clo, SafeClosure<Map.Entry<K, V>> kvClo) {
				clo.acquire(key);
				nodeVClo.release(key);

				assert(deflate_closures.get(key) == nodeVClo);
				assert(((UpdateValue)value_closures.get(key)).node == node);
				value_closures.put(key, kvClo);
				deflate_closures.put(key, clo);
			}

		}

		/**
		** Merge the relevant parts of the map into the node, and then inflate
		** its children.
		**
		** To be called after a node is itself inflated.
		*/
		class InflateChildNodes implements SafeClosure<Node> {

			final Node parent;
			final SortedSet<K> putkey;
			final CountingSweeper<Node> parNClo;
			final TrackingSweeper<K> parVClo;

			protected InflateChildNodes(Node p, SortedSet<K> ki, CountingSweeper<Node> pnc, TrackingSweeper<K> pvc) {
				parent = p;
				putkey = ki;
				parNClo = pnc;
				parVClo = pvc;
			}

			public void invoke(Node node) {
				assert(compareL(node.lkey, putkey.first()) < 0);
				assert(compareR(putkey.last(), node.rkey) < 0);

				// a jobless sweeper whose only purpose is to track get-value operations
				// that have still not completed by the time we get to SplitNode
				TrackingSweeper<K> vClo = new TrackingSweeper<K>(true);

				// closure to be called when all subnodes have been handled
				SplitNode nClo = new SplitNode(node, parent, vClo, parNClo, parVClo);

				SafeClosure kvClo = new UpdateValue(node);

				// invalidate every totalSize cache directly after we inflate it
				node._size = -1;

				// each key in putkey is either added to the local entries, or delegated to
				// the the relevant child node.
				if (node.isLeaf()) {
					// add all keys into the node, since there are no children.
					//
					// OPTIMISE: could use a splice-merge here. for TreeMap, there is not an
					// easy way of doing this, nor will it likely make a lot of a difference.
					// however, if we re-implement Node, this might become relevant.
					//
					for (K key: putkey) {
						handleLocalPut(node, key, vClo, kvClo);
					}

				} else {
					// only add keys that already exist locally in the node. other keys
					// are delegated to the relevant child node.

					SortedSet<K> fkey = new TreeSet<K>();
					Iterable<$2<K, K>> pairs = Sorted.split(putkey, (SortedSet<K>)node.entries.keySet(), fkey);

					for (K key: fkey) {
						handleLocalPut(node, key, vClo, kvClo);
					}

					for ($2<K, K> kp: pairs) {
						Node n = node.lnodes.get(kp._1);
						assert(n.isGhost());
						PullTask<Node> task = new PullTask<Node>(n);

						nClo.acquire(n);
						inflate_closures.put(task, new InflateChildNodes(node, putkey.subSet(kp._0, kp._1), nClo, vClo));
						pull_queue.put(task);
					}
				}

				nClo.close();
				if (nClo.isCleared()) { nClo.run(); }
			}

			/**
			** Handle a planned local put to the node.
			**
			** Keys added locally have their values set to null. These will be updated
			** with the correct values via UpdateValue, when the value-getter completes
			** its operation on the key. We add the keys now, **before** the value is
			** obtained, so that SplitNode can work out how to split the node as early
			** as possible.
			**
			** @param n The node to put the key into
			** @param key The key
			** @param vClo The sweeper that tracks if the key's value has been obtained
			** @param kvClo The closure to run when the key's value has been obtained
			*/
			public void handleLocalPut(Node n, K key, TrackingSweeper<K> vClo, SafeClosure<Map.Entry<K, V>> kvClo) {
				V oldval = n.entries.put(key, null);
				vClo.acquire(key);
				deflate_closures.put(key, vClo);
				value_closures.put(key, kvClo);
				value_pending.put(Maps.$(key, oldval));
			}

			public void handleLocalRemove(Node n, K key, TrackingSweeper vClo) {
				throw new UnsupportedOperationException("not implemented");
			}

		}

		try {

			PullTask<Node> rtask = new PullTask<Node>(null);
			rtask.data = root;
			inflate_closures.put(rtask, new InflateChildNodes(null, putkey, null, null));
			inflated.put(rtask);

			do {

				while (!inflated.isEmpty()) {
					PullTask<Node> task = inflated.take();

					inflate_closures.remove(task).invoke(task.data);
				}

				while (!deflated.isEmpty()) {
					PushTask<Node> task = deflated.take();

					CountingSweeper<Node> sw = split_closures.remove(task);
					assert(sw instanceof Runnable);
					sw.release(task.data);
					if (sw.isCleared()) {
						((Runnable)sw).run();
					}
				}

				while (!value_complete.isEmpty()) {
					Map.Entry<K, V> en = value_complete.take();
					K k = en.getKey();
					SafeClosure<Map.Entry<K, V>> updv = value_closures.remove(k);
					updv.invoke(en);

					TrackingSweeper<K> sw = deflate_closures.remove(k);
					assert(sw instanceof Runnable);
					sw.release(k);
					if (sw.isCleared()) {
						((Runnable)sw).run();
					}
				}

				Thread.sleep(1000);

			} while(
				!inflated.isEmpty() ||
				!deflated.isEmpty() ||
				!value_complete.isEmpty() ||
				!inflate_closures.isEmpty() ||
				!deflate_closures.isEmpty() ||
				!split_closures.isEmpty() ||
				!value_closures.isEmpty()
			);

		} catch (InterruptedException e) {
			// TODO throw TAbEx
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

		/*@Override**/ public Map<String, Object> app(SkeletonNode node) {
			if (!node.isBare()) {
				throw new IllegalStateException("Cannot translate non-bare node " + node.getRange());
			}
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			map.put("lkey", (ktr == null)? node.lkey: ktr.app(node.lkey));
			map.put("rkey", (ktr == null)? node.rkey: ktr.app(node.rkey));
			map.put("entries", (mtr == null)? node.entries: mtr.app((SkeletonTreeMap<K, V>)node.entries));

			if (!node.isLeaf()) {
				Map<Object, Integer> subnodes = new LinkedHashMap<Object, Integer>();
				for ($3<K, Node, K> next: node.iterNodesK()) {
					GhostNode gh = (GhostNode)(next._1);
					subnodes.put(gh.getMeta(), gh.totalSize());
				}
				map.put("subnodes", subnodes);
			}
			return map;
		}

		/*@Override**/ public SkeletonNode rev(Map<String, Object> map) throws DataFormatException {
			try {
				boolean notleaf = map.containsKey("subnodes");
				List<GhostNode> gh = null;
				if (notleaf) {
					Map<Object, Integer> subnodes = (Map<Object, Integer>)map.get("subnodes");
					gh = new ArrayList<GhostNode>(subnodes.size());
					for (Map.Entry<Object, Integer> en: subnodes.entrySet()) {
						GhostNode ghost = new GhostNode(null, null, null, en.getValue());
						ghost.setMeta(en.getKey());
						gh.add(ghost);
					}
				}
				SkeletonNode node = new SkeletonNode(
					(ktr == null)? (K)map.get("lkey"): ktr.rev((Q)map.get("lkey")),
					(ktr == null)? (K)map.get("rkey"): ktr.rev((Q)map.get("rkey")),
					!notleaf,
					(mtr == null)? (SkeletonTreeMap<K, V>)map.get("entries")
					             : mtr.rev((R)map.get("entries")),
					gh
				);

				verifyNodeIntegrity(node);
				return node;
			} catch (ClassCastException e) {
				throw new DataFormatException("Could not build SkeletonNode from data", e, map, null, null);
			} catch (IllegalArgumentException e) {
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

		/*@Override**/ public Map<String, Object> app(SkeletonBTreeMap<K, V> tree) {
			if (tree.comparator() != null) {
				throw new UnsupportedOperationException("Sorry, this translator does not (yet) support comparators");
			}
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			map.put("node_min", tree.NODE_MIN);
			map.put("size", tree.size);
			Map<String, Object> rmap = tree.makeNodeTranslator(ktr, mtr).app((SkeletonBTreeMap.SkeletonNode)(SkeletonBTreeMap.Node)tree.root);
			map.put("entries", rmap.get("entries"));
			if (!tree.root.isLeaf()) {
				map.put("subnodes", rmap.get("subnodes"));
			}
			return map;
		}

		/*@Override**/ public SkeletonBTreeMap<K, V> rev(Map<String, Object> map) throws DataFormatException {
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
