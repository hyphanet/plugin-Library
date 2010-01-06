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

// FIXME tidy this
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

import java.util.Collections;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.HashMap;
import plugins.Library.util.Sorted;
import plugins.Library.util.concurrent.ObjectProcessor;
import plugins.Library.util.concurrent.Executors;
import plugins.Library.util.event.TrackingSweeper;
import plugins.Library.util.event.CountingSweeper;
import plugins.Library.util.func.Closure;
import plugins.Library.util.func.SafeClosure;
import plugins.Library.util.func.Tuples.$2;
import static plugins.Library.util.Maps.$K;

/**
** {@link Skeleton} of a {@link BTreeMap}. DOCUMENT
**
** TODO get rid of uses of rnode.get(K). All other uses of rnode, as well as
** lnode and entries, have already been removed. This will allow us to
** re-implement the Node class.
**
** To the maintainer: this class is very very unstructured and a lot of the
** functionality should be split off elsewhere. Feel free to move stuff around.
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

	final public Comparator<PullTask<SkeletonNode>> CMP_PULL = new Comparator<PullTask<SkeletonNode>>() {
		/*@Override**/ public int compare(PullTask<SkeletonNode> t1, PullTask<SkeletonNode> t2) {
			return ((GhostNode)t1.meta).compareTo((GhostNode)t2.meta);
		}
	};

	final public Comparator<PushTask<SkeletonNode>> CMP_PUSH = new Comparator<PushTask<SkeletonNode>>() {
		/*@Override**/ public int compare(PushTask<SkeletonNode> t1, PushTask<SkeletonNode> t2) {
			return t1.data.compareTo(t2.data);
		}
	};

	final public Comparator<Map.Entry<K, V>> CMP_ENTRY = new Comparator<Map.Entry<K, V>>() {
		/*@Override**/ public int compare(Map.Entry<K, V> t1, Map.Entry<K, V> t2) {
			return SkeletonBTreeMap.this.compare(t1.getKey(), t2.getKey());
		}
	};

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
				postPullTask(task, this);
				if (auto) { task.data.inflate(); }

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

		/**
		** Points to the parent {@link SkeletonNode}.
		**
		** Maintaining this field's value is a bitch, so I've tried to remove uses
		** of this from the code. Currently, it is only used for the parent field
		** a {@link DataFormatException}, which lets us retrieve the serialiser,
		** progress, etc etc etc. TODO somehow find another way of doing that so
		** we can get rid of it completely.
		*/
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

	/**
	** Post-processes a {@link PullTask} and returns the {@link SkeletonNode}
	** pulled.
	**
	** The tree will be in a consistent state after the operation, if it was
	** in a consistent state before it.
	*/
	protected SkeletonNode postPullTask(PullTask<SkeletonNode> task, SkeletonNode parent) throws DataFormatException {
		SkeletonNode node = task.data;
		GhostNode ghost = (GhostNode)task.meta;
		if (!compare0(ghost.lkey, node.lkey) || !compare0(ghost.rkey, node.rkey)) {
			throw new DataFormatException("BTreeMap Node lkey/rkey does not match", null, node);
		}

		parent.attachSkeleton(node);
		return node;
	}

	/**
	** Post-processes a {@link PushTask} and returns the {@link GhostNode}
	** pushed.
	**
	** The tree will be in a consistent state after the operation, if it was
	** in a consistent state before it.
	*/
	protected GhostNode postPushTask(PushTask<SkeletonNode> task, SkeletonNode parent) {
		// TODO getting rid of the "parent" parameter would be so much cleaner...
		GhostNode ghost = (GhostNode)task.meta;
		parent.attachGhost(ghost);
		return ghost;
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

	// FIXME tidy this; this should proboably go in a serialiser
	// and then we will access the Progress of a submap with a task whose
	// metadata is (lkey, rkey), or something..(PROGRESS)
	BaseCompositeProgress pr_inf = new BaseCompositeProgress();
	public BaseCompositeProgress getProgressInflate() { return pr_inf; } // REMOVE ME
	/**
	** Parallel bulk-inflate. FIXME not yet thread safe, but ideally it should
	** be. See source for details.
	*/
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

		final Queue<SkeletonNode> nodequeue = new PriorityQueue<SkeletonNode>();

		Map<PullTask<SkeletonNode>, ProgressTracker<SkeletonNode, ?>> ids = null;
		ProgressTracker<SkeletonNode, ?> ntracker = null;;

		if (nsrl instanceof Serialiser.Trackable) {
			ids = new LinkedHashMap<PullTask<SkeletonNode>, ProgressTracker<SkeletonNode, ?>>();
			ntracker = ((Serialiser.Trackable<SkeletonNode>)nsrl).getTracker();
			// PROGRESS make a ProgressTracker track this instead of "pr_inf".
			pr_inf.setSubProgress(ProgressTracker.makePullProgressIterable(ids));
			pr_inf.setSubject("Pulling all entries in B-tree");
		}

		final ObjectProcessor<PullTask<SkeletonNode>, SkeletonNode, TaskAbortException> proc_pull
		= ((ScheduledSerialiser<SkeletonNode>)nsrl).pullSchedule(
			new PriorityBlockingQueue<PullTask<SkeletonNode>>(0x10, CMP_PULL),
			new LinkedBlockingQueue<$2<PullTask<SkeletonNode>, TaskAbortException>>(0x10),
			new HashMap<PullTask<SkeletonNode>, SkeletonNode>()
		);
		//System.out.println("Using scheduler");
		//int DEBUG_pushed = 0, DEBUG_popped = 0;

		try {
			nodequeue.add((SkeletonNode)root);

			// FIXME make a copy of the deflated root so that we can restore it if the
			// operation fails

			do {
				//System.out.println("pushed: " + DEBUG_pushed + "; popped: " + DEBUG_popped);

				// handle the inflated tasks and attach them to the tree.
				// THREAD progress tracker should prevent this from being run twice for the
				// same node, but what if we didn't use a progress tracker? hmm...
				while (proc_pull.hasCompleted()) {
					$3<PullTask<SkeletonNode>, SkeletonNode, TaskAbortException> res = proc_pull.accept();
					PullTask<SkeletonNode> task = res._0;
					SkeletonNode parent = res._1;
					TaskAbortException ex = res._2;
					if (ex != null) {
						assert(!(ex instanceof plugins.Library.util.exec.TaskInProgressException)); // by contract of ScheduledSerialiser
						if (!(ex instanceof TaskCompleteException)) {
							// TODO maybe dump it somewhere else and throw it at the end...
							throw ex;
						}
						// retrieve the inflated SkeletonNode and add it to the queue...
						GhostNode ghost = (GhostNode)task.meta;
						// THREAD race condition here... if another thread has inflated the task
						// but not yet attached the inflated node to the tree, the assertion fails.
						// could check to see if the Progress for the Task still exists, but the
						// performance of this depends on the GC freeing weak referents quickly...
						assert(!parent.rnodes.get(ghost.lkey).isGhost());
						nodequeue.add((SkeletonNode)parent.rnodes.get(ghost.lkey));
					} else {
						SkeletonNode node = postPullTask(task, parent);
						nodequeue.add(node);
					}
					//++DEBUG_popped;
				}

				// go through the nodequeue and add any child ghost nodes to the tasks queue
				while (!nodequeue.isEmpty()) {
					SkeletonNode node = nodequeue.remove();
					((SkeletonTreeMap<K, V>)node.entries).inflate(); // SUBMAP here

					if (node.isLeaf()) { continue; }
					for (Node next: node.iterNodes()) { // SUBMAP here
						if (!next.isGhost()) {
							SkeletonNode skel = (SkeletonNode)next;
							if (!skel.isLive()) { nodequeue.add(skel); }
							continue;
						}
						PullTask<SkeletonNode> task = new PullTask<SkeletonNode>((GhostNode)next);
						if (ids != null) { ids.put(task, ntracker); }
						ObjectProcessor.submitSafe(proc_pull, task, node);
						//++DEBUG_pushed;
					}
				}

				Thread.sleep(1000);
			} while (proc_pull.hasPending());

			pr_inf.setEstimate(ProgressParts.TOTAL_FINALIZED);

		} catch (DataFormatException e) {
			throw new TaskAbortException("Bad data format", e);
		} catch (InterruptedException e) {
			throw new TaskAbortException("interrupted", e);
		} finally {
			proc_pull.close();
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
	** Asynchronously updates a remote B-tree. This uses two-pass merge/split
	** algorithms (as opposed to the one-pass algorithms of the standard {@link
	** BTreeMap}) since it assumes a copy-on-write backing data store, where
	** the advantages (concurrency, etc) of one-pass algorithms disappear, and
	** the disadvantages (unneeded merges/splits) remain.
	**
	** Unlike the (ideal design goal of the) inflate/deflate methods, this is
	** designed to support accept only one concurrent update. It is up to the
	** caller to ensure that this holds. TODO maybe make this synchronized?
	**
	** TODO currently, this method assumes that the root.isBare().
	**
	** Note: {@code remkey} is not implemented yet.
	**
	** @param putkey The keys to insert into the map
	** @param remkey The keys to remove from the map
	** @param value_handler DOCUMENT
	** @throws UnsupportedOperationException if {@code remkey} is not empty
	*/
	public <X extends Exception> void update(
		SortedSet<K> putkey, SortedSet<K> remkey, Closure<Map.Entry<K, V>, X> value_handler
	) throws TaskAbortException {

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
		**   - subnodes get pushed into proc_pull
		**   - (recurse for each subnode)
		**     - subnode gets popped from proc_pull
		**     - etc
		**     - split-subnodes get pushed into proc_push
		** - wait for all: split-subnodes get popped from proc_push
		** - enter SplitNode
		**   - for each item in the original node's DeflateNode
		**     - release the item and acquire it on
		**       - the parent's DeflateNode if the item is a separator
		**       - a new DeflateNode if the item is now in a split-node
		** - for each split-node:
		**   - wait for all: values get popped from proc_val; SplitNode to close()
		**   - enter DeflateNode
		**     - split-node gets pushed into push_queue (except for root)
		**
		*/

		// TODO HIGH need a way of bypassing value_handler so that we
		// can update the values synchronously (ie. when we don't need to retrieve
		// network data).

		final ObjectProcessor<PullTask<SkeletonNode>, SafeClosure<SkeletonNode>, TaskAbortException> proc_pull
		= ((ScheduledSerialiser<SkeletonNode>)nsrl).pullSchedule(
			new PriorityBlockingQueue<PullTask<SkeletonNode>>(0x10, CMP_PULL),
			new LinkedBlockingQueue<$2<PullTask<SkeletonNode>, TaskAbortException>>(0x10),
			new HashMap<PullTask<SkeletonNode>, SafeClosure<SkeletonNode>>()
		);

		final ObjectProcessor<PushTask<SkeletonNode>, CountingSweeper<SkeletonNode>, TaskAbortException> proc_push
		= ((ScheduledSerialiser<SkeletonNode>)nsrl).pushSchedule(
			new PriorityBlockingQueue<PushTask<SkeletonNode>>(0x10, CMP_PUSH),
			new LinkedBlockingQueue<$2<PushTask<SkeletonNode>, TaskAbortException>>(0x10),
			new HashMap<PushTask<SkeletonNode>, CountingSweeper<SkeletonNode>>()
		);

		/**
		** Deposit for a value-retrieval operation
		*/
		class DeflateNode extends TrackingSweeper<K, SortedSet<K>> implements Runnable, SafeClosure<Map.Entry<K, V>> {

			final SkeletonNode node;
			final CountingSweeper<SkeletonNode> parNClo;

			protected DeflateNode(SkeletonNode n, CountingSweeper<SkeletonNode> pnc) {
				super(true, true, new TreeSet<K>(), null);
				parNClo = pnc;
				node = n;
			}

			/**
			** Push this node. This is run when this sweeper is cleared, which
			** happens after all the node's local values have been obtained,
			** '''and''' the node has passed through SplitNode (which closes
			** this sweeper).
			*/
			public void run() {
				if (parNClo == null) {
					// do not deflate the root
					assert(node == root);
					return;
				}
				ObjectProcessor.submitSafe(proc_push, new PushTask<SkeletonNode>(node), parNClo);
			}

			/**
			** Update the key's value in the node. Runs whenever an entry is
			** popped from proc_val.
			*/
			public void invoke(Map.Entry<K, V> en) {
				assert(node.entries.containsKey(en.getKey()));
				node.entries.put(en.getKey(), en.getValue());
			}

		}

		// must be located after DeflateNode's class definition
		final ObjectProcessor<Map.Entry<K, V>, DeflateNode, X> proc_val
		= new ObjectProcessor<Map.Entry<K, V>, DeflateNode, X>(
			new PriorityBlockingQueue<Map.Entry<K, V>>(0x10, CMP_ENTRY),
			new LinkedBlockingQueue<$2<Map.Entry<K, V>, X>>(),
			new HashMap<Map.Entry<K, V>, DeflateNode>(),
			value_handler, Executors.DEFAULT_EXECUTOR, true
		);

		// Dummy constant for SplitNode
		final SortedMap<K, V> EMPTY_SORTEDMAP = new TreeMap<K, V>();

		/**
		** Deposit for a PushTask
		*/
		class SplitNode extends CountingSweeper<SkeletonNode> implements Runnable {

			final SkeletonNode node;
			/*final*/ SkeletonNode parent;
			final DeflateNode nodeVClo;
			/*final*/ SplitNode parNClo;
			/*final*/ DeflateNode parVClo;

			protected SplitNode(SkeletonNode n, SkeletonNode p, DeflateNode vc, SplitNode pnc, DeflateNode pvc) {
				super(true, false);
				node = n;
				parent = p;
				nodeVClo = vc;
				parNClo = pnc;
				parVClo = pvc;
			}

			/**
			** Closes the node's DeflateNode, and (if appropriate) splits the
			** node and updates the deposits for each key moved. This is run
			** after all its children have been deflated.
			*/
			public void run() {

				// All subnodes have been deflated, so nothing else can possibly add keys
				// to this node.
				nodeVClo.close();

				int sk = minKeysFor(node.nodeSize());
				// No need to split
				if (sk == 0) {
					if (nodeVClo.isCleared()) { nodeVClo.run(); }
					return;
				}

				if (parent == null) {
					assert(parNClo == null && parVClo == null);
					// create a new parent, parNClo, parVClo
					// similar stuff as for InflateChildNodes but no merging
					parent = new SkeletonNode(null, null, false);
					parent.addAll(EMPTY_SORTEDMAP, Collections.singleton(node));
					parVClo = new DeflateNode(parent, null);
					parNClo = new SplitNode(parent, null, parVClo, null, null);
					parNClo.acquire(node);
					parNClo.close();
					root = parent;
					size = root.totalSize();
				}

				Collection<K> keys = Sorted.select(Sorted.keySet(node.entries), sk);
				parent.split(node.lkey, keys, node.rkey);
				Iterable<Node> nodes = parent.iterNodes(node.lkey, node.rkey);

				// reassign appropriate keys to parent sweeper
				for (K key: keys) {
					reassignKeyToSweeper(key, parVClo);
				}

				parNClo.open();

				// for each split-node, create a sweeper that will run when all its (k,v)
				// pairs have been popped from value_complete
				for (Node nn: nodes) {
					SkeletonNode n = (SkeletonNode)nn;
					DeflateNode vClo = new DeflateNode(n, parNClo);

					// reassign appropriate keys to the split-node's sweeper
					assert(compareL(n.lkey, nodeVClo.view().subSet(n.lkey, n.rkey).first()) < 0);
					for (K key: nodeVClo.view().subSet(n.lkey, n.rkey)) {
						reassignKeyToSweeper(key, vClo);
					}
					vClo.close();
					if (vClo.isCleared()) { vClo.run(); } // if no keys were added

					parNClo.acquire(n);
				}

				// original (unsplit) node had a ticket on the parNClo sweeper, release it
				parNClo.release(node);

				parNClo.close();
				assert(!parNClo.isCleared()); // we always have at least one node to deflate
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
			*/
			private void reassignKeyToSweeper(K key, DeflateNode clo) {
				clo.acquire(key);
				//assert(((UpdateValue)value_closures.get(key)).node == node);
				proc_val.update($K(key, (V)null), clo);
				// nodeVClo.release(key);
				// this is unnecessary since nodeVClo() will only be used if we did not
				// split its node (and never called this method)
			}

		}

		/**
		** Deposit for a PullTask
		*/
		class InflateChildNodes implements SafeClosure<SkeletonNode> {

			final SkeletonNode parent;
			final SortedSet<K> putkey;
			final SplitNode parNClo;
			final DeflateNode parVClo;

			protected InflateChildNodes(SkeletonNode p, SortedSet<K> ki, SplitNode pnc, DeflateNode pvc) {
				parent = p;
				putkey = ki;
				parNClo = pnc;
				parVClo = pvc;
			}

			protected InflateChildNodes(SortedSet<K> ki) {
				this(null, ki, null, null);
			}

			/**
			** Merge the relevant parts of the map into the node, and inflate its
			** children. Runs whenever a node is popped from proc_pull.
			*/
			public void invoke(SkeletonNode node) {
				assert(compareL(node.lkey, putkey.first()) < 0);
				assert(compareR(putkey.last(), node.rkey) < 0);

				// closure to be called when all local values have been obtained
				DeflateNode vClo = new DeflateNode(node, parNClo);

				// closure to be called when all subnodes have been handled
				SplitNode nClo = new SplitNode(node, parent, vClo, parNClo, parVClo);

				// invalidate every totalSize cache directly after we inflate it
				node._size = -1;

				// each key in putkey is either added to the local entries, or delegated to
				// the the relevant child node.
				if (node.isLeaf()) {
					// add all keys into the node, since there are no children.
					//
					// OPTIMISE: could use a splice-merge here. for TreeMap, there is not an
					// easy way of doing this, nor will it likely make a lot of a difference.
					// however, if we re-implement SkeletonNode, this might become relevant.
					//
					for (K key: putkey) {
						handleLocalPut(node, key, vClo);
					}

				} else {
					// only add keys that already exist locally in the node. other keys
					// are delegated to the relevant child node.

					SortedSet<K> fkey = new TreeSet<K>();
					Iterable<$2<K, K>> pairs = Sorted.split(putkey, Sorted.keySet(node.entries), fkey);

					for (K key: fkey) {
						handleLocalPut(node, key, vClo);
					}

					for ($2<K, K> kp: pairs) {
						SkeletonNode n = (SkeletonNode)node.lnodes.get(kp._1);
						assert(n.isGhost());
						PullTask<SkeletonNode> task = new PullTask<SkeletonNode>(n);

						nClo.acquire(n);
						ObjectProcessor.submitSafe(proc_pull, task, new InflateChildNodes(node, putkey.subSet(kp._0, kp._1), nClo, vClo));
					}
				}

				nClo.close();
				if (nClo.isCleared()) { nClo.run(); } // eg. if no child nodes need to be modified
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
			*/
			private void handleLocalPut(SkeletonNode n, K key, DeflateNode vClo) {
				V oldval = n.entries.put(key, null);
				vClo.acquire(key);
				ObjectProcessor.submitSafe(proc_val, $K(key, oldval), vClo);
			}

			private void handleLocalRemove(SkeletonNode n, K key, TrackingSweeper<K, SortedSet<K>> vClo) {
				throw new UnsupportedOperationException("not implemented");
			}

		}

		try {

			(new InflateChildNodes(putkey)).invoke((SkeletonNode)root);

			// FIXME make a copy of the deflated root so that we can restore it if the
			// operation fails

			do {

				while (proc_pull.hasCompleted()) {
					$3<PullTask<SkeletonNode>, SafeClosure<SkeletonNode>, TaskAbortException> res = proc_pull.accept();
					PullTask<SkeletonNode> task = res._0;
					SafeClosure<SkeletonNode> clo = res._1;
					TaskAbortException ex = res._2;
					if (ex != null) {
						// FIXME HIGH
						throw new UnsupportedOperationException("SkeletonBTreeMap.update(): PullTask aborted; handler not implemented yet", ex);
					}

					SkeletonNode node = postPullTask(task, ((InflateChildNodes)clo).parent);
					clo.invoke(node);
				}

				while (proc_push.hasCompleted()) {
					$3<PushTask<SkeletonNode>, CountingSweeper<SkeletonNode>, TaskAbortException> res = proc_push.accept();
					PushTask<SkeletonNode> task = res._0;
					CountingSweeper<SkeletonNode> sw = res._1;
					TaskAbortException ex = res._2;
					if (ex != null) {
						// FIXME HIGH
						throw new UnsupportedOperationException("SkeletonBTreeMap.update(): PushTask aborted; handler not implemented yet", ex);
					}

					postPushTask(task, ((SplitNode)sw).parent);
					sw.release(task.data);
					if (sw.isCleared()) { ((Runnable)sw).run(); }
				}

				while (proc_val.hasCompleted()) {
					$3<Map.Entry<K, V>, DeflateNode, X> res = proc_val.accept();
					Map.Entry<K, V> en = res._0;
					DeflateNode sw = res._1;
					X ex = res._2;
					if (ex != null) {
						// FIXME HIGH
						throw new UnsupportedOperationException("SkeletonBTreeMap.update(): value-retrieval aborted; handler not implemented yet", ex);
					}

					sw.invoke(en);
					sw.release(en.getKey());
					if (sw.isCleared()) { ((Runnable)sw).run(); }
				}

				Thread.sleep(1000);
			} while (proc_pull.hasPending() || proc_push.hasPending() || proc_val.hasPending());

		} catch (DataFormatException e) {
			throw new TaskAbortException("Bad data format", e);
		} catch (InterruptedException e) {
			throw new TaskAbortException("interrupted", e);
		} finally {
			proc_pull.close();
			proc_push.close();
			proc_val.close();
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
			Map<String, Object> rmap = tree.makeNodeTranslator(ktr, mtr).app((SkeletonBTreeMap.SkeletonNode)tree.root);
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
