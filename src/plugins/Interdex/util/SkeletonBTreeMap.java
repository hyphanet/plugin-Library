/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.serl.Serialiser.*;
import plugins.Interdex.serl.Archiver;
import plugins.Interdex.serl.MapSerialiser;
import plugins.Interdex.serl.Translator;
import plugins.Interdex.serl.DataFormatException;
import plugins.Interdex.serl.TaskAbortException;
import plugins.Interdex.serl.TaskCompleteException;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;

/**
** Skeleton of a BTreeMap. DOCUMENT
**
** @author infinity0
*/
public class SkeletonBTreeMap<K, V> extends BTreeMap<K, V> implements SkeletonMap<K, V> {

	protected Archiver<SkeletonNode> nsrl;
	protected MapSerialiser<K, V> vsrl;

	// TODO maybe make this write-once
	public void setSerialiser(Archiver<SkeletonNode> n, MapSerialiser<K, V> v) {
		nsrl = n;
		vsrl = v;
		((SkeletonNode)root).setSerialiser();
	}

	public class SkeletonNode extends Node implements Skeleton<K> {

		int ghosts = 0;

		SkeletonNode(boolean leaf, SkeletonTreeMap<K, V> map) {
			super(leaf, map);
			setSerialiser();
		}

		SkeletonNode(boolean leaf) {
			this(leaf, new SkeletonTreeMap<K, V>(comparator));
		}

		public void setSerialiser() {
			((SkeletonTreeMap<K, V>)entries).setSerialiser(vsrl);
			if (!isLeaf()) {
				for (Node n: lnodes.values()) {
					if (n.entries != null) {
						((SkeletonNode)n).setSerialiser();
					}
				}
			}
		}

		public GhostNode makeGhost(Object meta) {
			GhostNode ghost = new GhostNode(lkey, rkey);
			ghost.setMeta(meta);
			return ghost;
		}

		@Override public Object getMeta() { return null; }
		@Override public void setMeta(Object m) { }

		@Override public boolean isLive() {
			if (ghosts > 0 || !((SkeletonTreeMap<K, V>)entries).isLive()) { return false; }
			if (!isLeaf()) {
				for (Node n: lnodes.values()) {
					SkeletonNode skel = (SkeletonNode)n;
					if (!skel.isLive()) { return false; }
				}
			}
			return true;
		}

		@Override public boolean isBare() {
			if (!isLeaf()) {
				if (ghosts < lnodes.size()) {
					return false;
				}
			}
			return ((SkeletonTreeMap<K, V>)entries).isBare();
		}

		// TODO make this parallel
		@Override public void deflate() throws TaskAbortException {
			if (!isLeaf()) {
				for (K k: lnodes.keySet()) {
					((SkeletonNode)lnodes.get(k)).deflate();
					deflate(k);
				}
			}
			((SkeletonTreeMap<K, V>)entries).deflate();
			assert(isBare());
		}

		@Override public void inflate() throws TaskAbortException {
			((SkeletonTreeMap<K, V>)entries).inflate();
			if (!isLeaf()) {
				for (K k: lnodes.keySet()) {
					inflate(k);
					((SkeletonNode)lnodes.get(k)).inflate();
				}
			}
			assert(isLive());
		}

		/**
		** Expects metadata to be of type {@link GhostNode}.
		*/
		@Override public void deflate(K key) throws TaskAbortException {
			if (isLeaf()) { return; }
			Node node = lnodes.get(key);
			if (node.entries == null) { return; } // ghost node

			if (!((SkeletonNode)node).isBare()) {
				throw new IllegalStateException("Cannot deflate non-bare BTreeMap node");
			}

			PushTask<SkeletonNode> task = new PushTask<SkeletonNode>((SkeletonNode)node);
			try {
				nsrl.push(task);

				GhostNode ghost = (GhostNode)task.meta;// new GhostNode(this, node.lkey, node.rkey);
				ghost.parent = this;

				lnodes.put(ghost.rkey, ghost);
				rnodes.put(ghost.lkey, ghost);
				++ghosts;

			// TODO maybe just ignore all non-error abortions
			} catch (TaskCompleteException e) {
				assert(node.entries == null);
			} catch (RuntimeException e) {
				throw new TaskAbortException("Could not deflate BTreeMap Node " + node.lkey + "-" + node.rkey, e);
			}
		}

		@Override public void inflate(K key) throws TaskAbortException {
			if (isLeaf()) { return; }
			Node node = lnodes.get(key);
			if (node.entries != null) { return; } // skeleton node

			PullTask<SkeletonNode> task = new PullTask<SkeletonNode>(node);
			try {
				nsrl.pull(task);

				if (compare2(node.lkey, task.data.lkey) != 0 || compare2(node.rkey, task.data.rkey) != 0) {
					throw new DataFormatException("BTreeMap Node lkey/rkey does not match", task.data);
				}

				lnodes.put(task.data.rkey, task.data);
				rnodes.put(task.data.lkey, task.data);
				--ghosts;

			} catch (TaskCompleteException e) {
				assert(lnodes.get(key).entries != null);
			} catch (RuntimeException e) {
				throw new TaskAbortException("Could not inflate BTreeMap Node " + node.lkey + "-" + node.rkey, e);
			}
		}

	}

	public class GhostNode extends Node {

		SkeletonNode parent;
		Object meta;

		GhostNode(SkeletonNode p, K l, K r) {
			super(false, null);
			parent = p;
			lkey = l;
			rkey = r;
		}

		GhostNode(K l, K r) {
			this(null, l, r);
		}

		public Object getMeta() {
			return meta;
		}

		public void setMeta(Object m) {
			meta = m;
		}

		@Override int size() {
			throw new DataNotLoadedException("BTreeMap Node not loaded", parent, rkey, this);
		}

		@Override boolean isLeaf() {
			throw new DataNotLoadedException("BTreeMap Node not loaded", parent, rkey, this);
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

	public SkeletonBTreeMap() {
		super();
	}


	@Override protected Node newNode(boolean leaf) {
		return new SkeletonNode(leaf);
	}




	@Override public Object getMeta() { return null; }
	@Override public void setMeta(Object m) { }

	@Override public boolean isLive() {
		return ((SkeletonNode)root).isLive();
	}

	@Override public boolean isBare() {
		return ((SkeletonNode)root).isBare();
	}

	@Override public void deflate() throws TaskAbortException {
		((SkeletonNode)root).deflate();
	}

	@Override public void inflate() throws TaskAbortException {
		((SkeletonNode)root).inflate();
	}

	@Override public void deflate(K key) throws TaskAbortException {
		// TODO
	}

	@Override public void inflate(K key) throws TaskAbortException {
		try {
			get(key);
		} catch (DataNotLoadedException e) {
			e.getParent().deflate(e.getKey());
		}
	}


	/**
	** This is necessary because Node is a non-static class.
	*/
	public NodeTranslator makeNodeTranslator(Translator<K, String> ktr, Translator<SkeletonTreeMap<K, V>, Map<String, Object>> mtr) {
		return new NodeTranslator(ktr, mtr);
	}

	public class NodeTranslator implements Translator<SkeletonNode, Map<String, Object>> {

		final Translator<K, String> ktr;
		final Translator<SkeletonTreeMap<K, V>, Map<String, Object>> mtr;

		public NodeTranslator(Translator<K, String> k, Translator<SkeletonTreeMap<K, V>, Map<String, Object>> m) {
			ktr = k;
			mtr = m;
		}

		@Override public Map<String, Object> app(SkeletonNode node) {
			if (!node.isBare()) {
				throw new IllegalStateException("Cannot translate non-bare node");
			}
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			map.put("lkey", (ktr == null)? node.lkey: ktr.app(node.lkey));
			map.put("rkey", (ktr == null)? node.rkey: ktr.app(node.rkey));
			map.put("entries", mtr.app((SkeletonTreeMap<K, V>)node.entries));

			if (!node.isLeaf()) {
				List<Object> subnodes = new ArrayList<Object>();
				for (K k: node.entries.keySet()) {
					GhostNode gh = (GhostNode)node.lnodes.get(k);
					subnodes.add(gh.getMeta());
				}
				GhostNode gh = (GhostNode)node.lnodes.get(node.rkey);
				subnodes.add(gh.getMeta());
				map.put("subnodes", subnodes);
			}
			return map;
		}

		@Override public SkeletonNode rev(Map<String, Object> map) {
			try {
				SkeletonNode node = new SkeletonNode(!map.containsKey("subnodes"), mtr.rev((Map<String, Object>)map.get("entries")));
				node.lkey = (ktr == null)? (K)map.get("lkey"): ktr.rev((String)map.get("lkey"));
				node.rkey = (ktr == null)? (K)map.get("rkey"): ktr.rev((String)map.get("rkey"));
				if (!node.isLeaf()) {
					List<Object> subnodes = (List<Object>)map.get("subnodes");
					K lastkey = node.lkey;
					Iterator<K> keys = node.entries.keySet().iterator();
					for (Object meta: subnodes) {
						K thiskey = keys.hasNext()? keys.next(): node.rkey;
						GhostNode ghost = new GhostNode(node, lastkey, thiskey);
						ghost.setMeta(meta);
						node.rnodes.put(lastkey, ghost);
						node.lnodes.put(thiskey, ghost);
						lastkey = thiskey;
					}
				}
				verifyNodeIntegrity(node);
				return node;
			} catch (ClassCastException e) {
				throw new DataFormatException("Could not build SkeletonNode from data", e, null, null, null);
			} catch (IllegalStateException e) {
				throw new DataFormatException("Could not build SkeletonNode from data", e, null, null, null);
			}
		}

	}

	public static class TreeTranslator<K, V> implements Translator<SkeletonBTreeMap<K, V>, Map<String, Object>> {

		final Translator<K, String> ktr;
		final Translator<SkeletonTreeMap<K, V>, Map<String, Object>> mtr;

		public TreeTranslator(Translator<K, String> k, Translator<SkeletonTreeMap<K, V>, Map<String, Object>> m) {
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
			map.put("root", tree.makeNodeTranslator(ktr, mtr).app((SkeletonBTreeMap.SkeletonNode)tree.root));
			return map;
		}

		@Override public SkeletonBTreeMap<K, V> rev(Map<String, Object> map) {
			try {
				SkeletonBTreeMap<K, V> tree = new SkeletonBTreeMap<K, V>((Integer)map.get("node_min"));
				tree.size = (Integer)map.get("size"); // TODO have some way of verifying this
				// make this not do the "size" check for root
				tree.root = tree.makeNodeTranslator(ktr, mtr).rev((Map<String, Object>)map.get("root"));
				return tree;
			} catch (ClassCastException e) {
				throw new DataFormatException("Could not build SkeletonBTreeMap from data", e, null, null, null);
			}
		}

	}

}
