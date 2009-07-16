/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.ArrayList;

/**
** General purpose BTree implementation
**
** DOCUMENT
**
** @author infinity0
*/
public class BTreeMap<K, V> implements Map<K, V>, SortedMap<K, V> {

	/**
	** Minimum number of children of each node.
	*/
	final protected int NODE_MIN;

	/**
	** Maximum number of children of each node. Equal to NODE_MAX.
	*/
	final protected int NODE_MAX;

	/**
	** Minimum number of entries in each node. Equal to NODE_MIN-1.
	*/
	final protected int ENT_MIN;

	/**
	** Maximum number of entries in each node. Equal to NODE_MAX-1.
	*/
	final protected int ENT_MAX;

	/**
	** Comparator for this {@link SortedMap}.
	*/
	final protected Comparator<? super K> comparator;

	/**
	** Root node of the tree. The only node that can have less than ENT_MIN
	** entries.
	*/
	protected Node root = new Node(true);

	/**
	** Number of entries currently in the map.
	*/
	protected int size = 0;;

	/**
	** Creates a new empty map, sorted according to the given comparator, and
	** with each non-root node having the given minimum number of subnodes.
	**
	** @param cmp The comparator for the tree, or {@code null} to use the keys'
	**            {@link Comparable natural} ordering.
	** @param node_min Minimum number of subnodes in each node
	*/
	public BTreeMap(Comparator<? super K> cmp, int node_min) {
		if (node_min < 2) {
			throw new IllegalArgumentException("The minimum number of subnodes must be set to at least 2");
		}
		comparator = cmp;
		NODE_MIN = node_min;
		NODE_MAX = node_min<<1;
		ENT_MIN = NODE_MIN - 1;
		ENT_MAX = NODE_MAX - 1;
	}

	/**
	** Creates a new empty map, sorted according to the keys' {@link Comparable
	** natural} ordering, and with each non-root node having the given minimum
	** number of subnodes.
	**
	** @param node_min Minimum number of subnodes in each node
	**/
	public BTreeMap(int node_min) {
		this(null, node_min);
	}

	/**
	** Creates a new empty map, sorted according to the keys' {@link Comparable
	** natural} ordering, and with each non-root node having at least 256
	** subnodes.
	*/
	public BTreeMap() {
		this(null, 0x100);
	}

	/**
	** DOCUMENT
	**
	** <pre>
	**    ^                        Node                        ^
	**    |                                                    |
	**    |    V1    V2    V3    V4    V5    V6    V7    V8    |
	**    |    |     |     |     |     |     |     |     |     |
	**   lkey  K1    K2    K3    K4    K5    K6    K7    K8  rkey
	**     \  /  \  /  \  /  \  /  \  /  \  /  \  /  \  /  \  /
	**     Node  Node  Node  Node  Node  Node  Node  Node  Node
	**
	** | represents {@link #entries} mappings
	** \ represents {@link #rnodes} mappings (and the subnode's {@link #lkey})
	** / represents {@link #lnodes} mappings (and the subnode's {@link #rkey})
	** </pre>
	**
	** @author infinity0
	*/
	protected class Node {

		/**
		** Whether this node is a leaf.
		*/
		final boolean isLeaf;

		/**
		** Map of entries in this node.
		*/
		final SortedMap<K, V> entries;

		/**
		** Map of entries to their immediate smaller nodes. The greatest node
		** is mapped to by {@link #rkey}.
		*/
		final Map<K, Node> lnodes;

		/**
		** Map of entries to their immediate greater nodes. The smallest node
		** is mapped to by {@code #lkey}.
		*/
		final Map<K, Node> rnodes;

		/**
		** Greatest key smaller than all keys in this node and subnodes. This
		** is {@code null} when the node is at the minimum-key end of the tree.
		*/
		K lkey = null;

		/**
		** Smallest key greater than all keys in this node and subnodes. This
		** is {@code null} when the node is at the maximum-key end of the tree.
		*/
		K rkey = null;

		/**
		** Creates a new node for the BTree.
		**
		** @param leaf Whether to create a leaf node
		*/
		Node(boolean leaf) {
			isLeaf = leaf;
			entries = new TreeMap<K, V>(comparator);
			if (leaf) {
				// we don't use sentinel Nil elements because that wastes memory due to
				// having to maintain dummy rnodes and lnodes maps.
				lnodes = null;
				rnodes = null;
			} else {
				lnodes = new HashMap<K, Node>(NODE_MAX);
				rnodes = new HashMap<K, Node>(NODE_MAX);
			}
		}

		/**
		** Creates a new non-leaf node for the BTree.
		*/
		Node() {
			this(false);
		}

		/**
		** Returns the greatest subnode smaller than the given key, which must
		** be local to the node. If {@code key} is rkey, returns the greatest
		** subnode.
		**
		** Note: This method assumes that the input key is strictly between
		** {@link #lkey} and {@link #rkey}; it is up to the calling code to
		** ensure that this holds.
		**
		** @param key The key to lookup the node for
		** @return The appropriate node, or null if the key is not local to the
		**         node
		** @throws NullPointerException if this is a leaf node
		*/
		Node nodeL(K key) {
			assert(key == null && lkey == null && rkey == null
			    || compare2(lkey, key) < 0 && compare2(key, rkey) <= 0);

			return lnodes.get(key);
		}

		/**
		** Returns the smallest subnode greater than the given key, which must
		** be local to the node. If {@code key} is lkey, returns the smallest
		** subnode.
		**
		** Note: This method assumes that the input key is strictly between
		** {@link #lkey} and {@link #rkey}; it is up to the calling code to
		** ensure that this holds.
		**
		** @param key The key to lookup the node for
		** @return The appropriate node, or null if the key is not local to the
		**         node
		** @throws NullPointerException if this is a leaf node
		*/
		Node nodeR(K key) {
			assert(key == null && lkey == null && rkey == null
			    || compare2(lkey, key) <= 0 && compare2(key, rkey) < 0);

			return rnodes.get(key);
		}

		/**
		** Returns the subnode a that given key belongs in. The key must not be
		** local to the node.
		**
		** Note: This method assumes that the input key is strictly between
		** {@link #lkey} and {@link #rkey}; it is up to the calling code to
		** ensure that this holds.
		**
		** @param key The key to select the node for
		** @return The subnode for the key, or null if the key is local to the
		**         node
		** @throws NullPointerException if this is a leaf node; or if the key
		**         is {@code null} and the entries map uses natural order, or
		**         its comparator does not tolerate {@code null} keys
		*/
		Node selectNode(K key) {
			assert(compare2(lkey, key) < 0);
			assert(compare2(key, rkey) < 0);

			SortedMap<K, V> tailmap = entries.tailMap(key);
			if (tailmap.isEmpty()) {
				return lnodes.get(rkey);
			} else {
				K next = tailmap.firstKey();
				return (compare(key, next) == 0)? null: lnodes.get(next);
			}
		}

		// TODO make this show the values too, and move the keys-only into a
		// separate function
		public String toString() {
			StringBuilder s = new StringBuilder();
			s.append("[").append(lkey).append(")");
			if (isLeaf) {
				for (K k: entries.keySet()) {
					s.append(" ").append(k);
				}
			} else {
				s.append(" ").append(rnodes.get(lkey));
				for (K k: entries.keySet()) {
					s.append(" ").append(k).append(" ").append(nodeR(k));
				}
			}
			s.append(" (").append(rkey).append("]");
			return s.toString();
		}

		public String toPrettyString(String istr) {
			String nistr = istr + "\t";
			StringBuilder s = new StringBuilder();
			s.append(istr).append('(').append(lkey).append(')').append('\n');
			if (isLeaf) {
				for (Map.Entry<K, V> en: entries.entrySet()) {
					s.append(istr).append(en.getKey()).append(" : ").append(en.getValue()).append('\n');
				}
			} else {
				s.append(rnodes.get(lkey).toPrettyString(nistr));
				for (Map.Entry<K, V> en: entries.entrySet()) {
					s.append(istr).append(en.getKey()).append(" : ").append(en.getValue()).append('\n');
					s.append(nodeR(en.getKey()).toPrettyString(nistr));
				}
			}
			s.append(istr).append('(').append(rkey).append(')').append('\n');
			return s.toString();
		}

	}

	public String toString() {
		return root.toString();
	}

	public String toPrettyString() {
		return root.toPrettyString("");
	}

	/**
	** Split a maximal child node into two minimal nodes, using the median key
	** as the separator between these new nodes in the parent. If the parent is
	** {@code null}, creates a new {@link Node} and points {@link #root} to it.
	**
	** Note: This method assumes that the child is an actual subnode of the
	** parent, and that it is actually full. It is up to the calling code to
	** ensure that this holds.
	**
	** The exact implementation of this may change from time to time, so the
	** calling code should regard the input subnode as effectively destroyed,
	** and re-get the desired result subnode from calling the appropriate
	** get methods on the parent node.
	**
	** @param parent The node to (re)attach the split subnodes to.
	** @param child The subnode to split
	*/
	private K split(Node parent, Node child) {
		assert(child.entries.size() == ENT_MAX);
		assert(parent == null? (child.lkey == null && child.rkey == null):
		                       (parent.entries.size() < ENT_MAX
		                     && !parent.isLeaf
		                     && parent.nodeR(child.lkey) == child
		                     && parent.nodeL(child.rkey) == child));

		if (parent == null) {
			parent = root = new Node();
			parent.lnodes.put(null, child);
			parent.rnodes.put(null, child);
		}
		Node lnode = new Node(child.isLeaf);

		if (child.isLeaf) {
			// this is just the same as the code in the else block, but with leaf
			// references to rnodes and lnodes removed (since they are null)
			Iterator<Map.Entry<K, V>> it = child.entries.entrySet().iterator();
			for (int i=0; i<ENT_MIN; ++i) {
				Map.Entry<K, V> entry = it.next();
				K key = entry.getKey();

				lnode.entries.put(key, entry.getValue());
				it.remove();
			}
			Map.Entry<K, V> median = it.next();
			K mkey = median.getKey();

			lnode.lkey = child.lkey;
			lnode.rkey = child.lkey = mkey;

			parent.rnodes.put(lnode.lkey, lnode);
			parent.lnodes.put(child.rkey, child);
			parent.entries.put(mkey, median.getValue());
			parent.rnodes.put(mkey, child);
			parent.lnodes.put(mkey, lnode);

			it.remove();
			return mkey;

		} else {
			lnode.rnodes.put(child.lkey, child.rnodes.remove(child.lkey));
			Iterator<Map.Entry<K, V>> it = child.entries.entrySet().iterator();
			for (int i=0; i<ENT_MIN; ++i) {
				Map.Entry<K, V> entry = it.next();
				K key = entry.getKey();

				lnode.entries.put(key, entry.getValue());
				lnode.lnodes.put(key, child.lnodes.remove(key));
				lnode.rnodes.put(key, child.rnodes.remove(key));
				it.remove();
			}
			Map.Entry<K, V> median = it.next();
			K mkey = median.getKey();
			lnode.lnodes.put(mkey, child.lnodes.remove(mkey));

			lnode.lkey = child.lkey;
			lnode.rkey = child.lkey = mkey;

			parent.rnodes.put(lnode.lkey, lnode);
			parent.lnodes.put(child.rkey, child);
			parent.entries.put(mkey, median.getValue());
			parent.rnodes.put(mkey, child);
			parent.lnodes.put(mkey, lnode);

			it.remove();
			return mkey;
		}
	}

	/**
	** Merge two minimal child nodes into a maximal node, using the key that
	** separates them in the parent as the key that joins the halfnodes in the
	** merged node. If the parent is the {@link #root} and the merge makes it
	** empty, point {@code root} to the new merged node.
	**
	** Note: This method assumes that both childs are an actual subnodes of the
	** parent, that they are adjacent in the parent, and that they are actually
	** minimally full. It is up to the calling code to ensure that this holds.
	**
	** The exact implementation of this may change from time to time, so the
	** calling code should regard both input subnodes as effectively destroyed,
	** and re-get the desired result subnode from calling the appropriate
	** get methods on the parent node.
	**
	** @param parent The node to (re)attach the merge node to.
	** @param lnode The smaller subnode to merge
	** @param rnode The greater subnode to merge
	*/
	private K merge(Node parent, Node lnode, Node rnode) {
		assert(compare(lnode.rkey, rnode.lkey) == 0); // not compare2 since can't be at edges
		assert(lnode.isLeaf && rnode.isLeaf || !lnode.isLeaf && !rnode.isLeaf);
		assert(lnode.entries.size() == ENT_MIN);
		assert(rnode.entries.size() == ENT_MIN);
		assert(parent == root && parent.entries.size() > 0
		                      || parent.entries.size() > ENT_MIN);
		assert(!parent.isLeaf && parent.nodeR(lnode.rkey) == rnode
		                      && parent.nodeL(rnode.lkey) == lnode);

		if (rnode.isLeaf) {
			// this is just the same as the code in the else block, but with leaf
			// references to rnodes and lnodes removed (since they are null)
			rnode.entries.putAll(lnode.entries);

			K mkey = rnode.lkey; // same as lnode.rkey;
			rnode.entries.put(mkey, parent.entries.remove(mkey));

			parent.rnodes.remove(mkey);
			parent.lnodes.remove(mkey);
			parent.rnodes.put(lnode.lkey, rnode);

			if (parent == root && parent.entries.isEmpty()) { root = rnode; }
			return mkey;

		} else {
			rnode.entries.putAll(lnode.entries);
			rnode.lnodes.putAll(lnode.lnodes);
			rnode.rnodes.putAll(lnode.rnodes);

			K mkey = rnode.lkey; // same as lnode.rkey;
			rnode.entries.put(mkey, parent.entries.remove(mkey));

			rnode.lnodes.put(mkey, lnode.lnodes.get(mkey));
			rnode.rnodes.put(lnode.lkey, lnode.rnodes.get(lnode.lkey));

			parent.rnodes.remove(mkey);
			parent.lnodes.remove(mkey);
			parent.rnodes.put(lnode.lkey, rnode);

			if (parent == root && parent.entries.isEmpty()) { root = rnode; }
			return mkey;
		}
	}

	/**
	** Performs a rotate operation towards the smaller node.
	**
	** DOCUMENT and put asserts in
	*/
	private K rotateL(Node parent, Node lnode, Node rnode) {
		assert(compare(lnode.rkey, rnode.lkey) == 0); // not compare2 since can't be at edges
		assert(lnode.isLeaf && rnode.isLeaf || !lnode.isLeaf && !rnode.isLeaf);
		assert(rnode.entries.size() >= lnode.entries.size());
		assert(rnode.entries.size() > ENT_MIN);
		assert(!parent.isLeaf && parent.nodeR(lnode.rkey) == rnode
		                      && parent.nodeL(rnode.lkey) == lnode);

		K mkey = rnode.lkey;
		K skey = rnode.entries.firstKey();

		lnode.entries.put(mkey, parent.entries.remove(mkey));
		parent.entries.put(skey, rnode.entries.remove(skey));
		parent.rnodes.put(skey, parent.rnodes.remove(mkey));
		parent.lnodes.put(skey, parent.lnodes.remove(mkey));

		if (!lnode.isLeaf) {
			lnode.rnodes.put(mkey, rnode.rnodes.remove(mkey));
			lnode.lnodes.put(skey, rnode.lnodes.remove(skey));
		}

		return mkey;
	}

	/**
	** DOCUMENT and put asserts in
	*/
	private K rotateR(Node parent, Node lnode, Node rnode) {
		assert(compare(lnode.rkey, rnode.lkey) == 0); // not compare2 since can't be at edges
		assert(lnode.isLeaf && rnode.isLeaf || !lnode.isLeaf && !rnode.isLeaf);
		assert(lnode.entries.size() >= rnode.entries.size());
		assert(lnode.entries.size() > ENT_MIN);
		assert(!parent.isLeaf && parent.nodeR(lnode.rkey) == rnode
		                      && parent.nodeL(rnode.lkey) == lnode);

		K mkey = lnode.rkey;
		K skey = lnode.entries.lastKey();

		rnode.entries.put(mkey, parent.entries.remove(mkey));
		parent.entries.put(skey, lnode.entries.remove(skey));
		parent.lnodes.put(skey, parent.lnodes.remove(mkey));
		parent.rnodes.put(skey, parent.rnodes.remove(mkey));

		if (!rnode.isLeaf) {
			rnode.lnodes.put(mkey, lnode.lnodes.remove(mkey));
			rnode.rnodes.put(skey, lnode.rnodes.remove(skey));
		}

		return mkey;
	}

	/**
	** Compares two keys using the comparator for this tree, or the keys'
	** {@link Comparable natural} ordering if no comparator was given.
	**
	** @throws ClassCastException if the keys cannot be compared by the given
	**         comparator, or if they cannot be compared naturally (ie. they
	**         don't implement {@link Comparable})
	*/
	public int compare(K key1, K key2) {
		return (comparator != null)? comparator.compare(key1, key2): ((Comparable<K>)key1).compareTo(key2);
	}

	/**
	** A compare method with extend semantics, that takes into account the
	** special meaning of {@code null} for the {@link Node#lkey lkey} and
	** {@link Node#rkey rkey} fields.
	**
	** - If both keys are {@code null}, returns 0. Otherwise:
	** - If {@code key1} is {@code null}, it is treated as an {@link Node#lkey
	**   lkey} value, ie. smaller than all other values (method returns -1).
	** - If {@code key2} is {@code null}, it is treated as an {@link Node#rkey
	**   rkey} value, ie. greater than all other values (method returns -1).
	** - Otherwise, neither keys are {@code null}, and are compared normally.
	*/
	protected int compare2(K key1, K key2) {
		return (key1 == null)? ((key2 == null)? 0: -1):
		                       ((key2 == null)? -1: compare(key1, key2));
	}

	/**
	** Simulates an assertion.
	**
	** @param b The test condition
	** @throws IllegalStateException if the test condition is false
	*/
	final void verify(boolean b) {
		if (!b) { throw new IllegalStateException("Assert failed"); }
	}

	/**
	** Package-private debugging method. This one checks node integrity:
	**
	** - lkey < firstkey, lastkey < rkey
	**
	** For non-leaf nodes:
	**
	** - for each pair of adjacent keys (including two null keys either side of
	**   the list of keys), it is possible to traverse between the keys, and
	**   the single node between them, using the nodeL(), nodeR() methods and
	**   the lkey, rkey pointers.
	** - lnodes' and rnodes' size is 1 greater than that of entries. Ie. there
	**   is nothing else in the node beyond what we visited in the previous
	**   step.
	**
	** It also checks the BTree node constraints:
	**
	** - The node has at most ENT_MAX entries.
	** - The node has at least ENT_MIN entries, except the root.
	** - The root has at least 1 entry.
	**
	** @throws IllegalStateException if the constraints are not satisfied
	*/
	void verifyNodeIntegrity(Node node) {
		verify(compare2(node.lkey, node.entries.firstKey()) < 0);
		verify(compare2(node.entries.lastKey(), node.rkey) < 0);

		if (!node.isLeaf) {
			Iterator<K> it = node.entries.keySet().iterator();
			verify(it.hasNext());

			K prev = node.lkey;
			K curr = it.next();
			while (curr != node.rkey || prev != node.rkey) {
				Node node1 = node.nodeR(prev);
				verify(node1 != null && compare2(curr, node1.rkey) == 0);
				Node node2 = node.nodeL(curr);
				verify(node1 == node2 && compare2(node2.lkey, prev) == 0);
				prev = curr;
				curr = it.hasNext()? it.next(): node.rkey;
			}

			verify(node.entries.size() + 1 == node.rnodes.size());
			verify(node.entries.size() + 1 == node.lnodes.size());
		}

		verify(node.entries.size() <= ENT_MAX);
		verify(node == root && node.entries.size() >= 1
		                    || node.entries.size() >= ENT_MIN);
	}

	/**
	** Package-private debugging method. This one checks BTree constraints for
	** the subtree rooted at a given node:
	**
	** - All nodes follow the node constraints, listed above.
	** - All leaves appear in the same level
	**
	** @return depth of all leaves
	** @throws IllegalStateException if the constraints are not satisfied
	*/
	int verifyTreeIntegrity(Node node) {
		verifyNodeIntegrity(node);
		if (node.isLeaf) {
			return 0;
		} else {
			// breath-first search would take up too much memory for a broad BTree
			int depth = -1;
			for (Node n: node.lnodes.values()) {
				int d = verifyTreeIntegrity(n);
				if (depth < 0) { depth = d; }
				verify(d == depth);
			}
			return depth + 1;
		}
	}

	/**
	** Package-private debugging method. This one checks BTree constraints for
	** the entire tree.
	**
	** @throws IllegalStateException if the constraints are not satisfied
	*/
	void verifyTreeIntegrity() {
		verifyTreeIntegrity(root);
	}

	/*========================================================================
	  public interface Map
	 ========================================================================*/

	@Override public int size() {
		return size;
	}

	@Override public boolean isEmpty() {
		return size == 0;
	}

	@Override public void clear() {
		root = new Node(true);
		size = 0;
	}

	@Override public boolean containsKey(Object key) {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override public boolean containsValue(Object key) {
		throw new UnsupportedOperationException("not implemented");
	}

	/**
	** {@inheritDoc}
	**
	** This implementation just descends the tree, returning the value for a
	** given key if it can be found.
	**
	** @throws ClassCastException key cannot be compared with the keys
	**         currently in the map
	** @throws NullPointerException key is {@code null} and this map uses
	**         natural order, or its comparator does not tolerate {@code null}
	**         keys
	*/
	@Override public V get(Object k) {
		K key = (K) k;
		Node node = root;

		for (;;) {
			if (node.isLeaf) {
				return node.entries.get(key);
			}

			Node nextnode = node.selectNode(key);
			if (nextnode == null) {
				return node.entries.get(key);
			}

			node = nextnode;
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation uses the BTree single-pass insertion algorithm. We
	** descend the tree, restructuring it as needed so that we can insert an
	** entry into a leaf.
	**
	** Start at the root node. At each stage of the algorithm, we restructure
	** the tree so that the next node we reach has less than {@link #ENT_MAX}
	** entries, so the insertion can occur without breaking constraints.
	**
	** (*) If the number of entries is {@link #ENT_MAX}, then perform {@link
	** #split(Node, Node) split} on the node. Both the newly created nodes now
	** have less than {@link #ENT_MAX} entries; pick the appropriate halfnode
	** to for the rest of the operation, depending on the original input key.
	**
	** If the node is a leaf, insert the value and stop. Otherwise, if the key
	** is not already in the node, select the appropriate subnode and repeat
	** from (*).
	**
	** Otherwise, replace the value and stop.
	**
	** @throws ClassCastException key cannot be compared with the keys
	**         currently in the map
	** @throws NullPointerException key is {@code null} and this map uses
	**         natural order, or its comparator does not tolerate {@code null}
	**         keys
	*/
	@Override public V put(K key, V value) {
		Node node = root, parent = null;

		for (;;) {
			if (node.entries.size() == ENT_MAX) {
				K median = split(parent, node);
				if (parent == null) { parent = root; }

				node = parent.selectNode(key);
				if (node == null) { return parent.entries.put(key, value); }
			}
			assert(node.entries.size() < ENT_MAX);

			if (node.isLeaf) {
				int sz = node.entries.size();
				V v = node.entries.put(key, value);
				// update size cache
				if (node.entries.size() != sz) { ++size; }
				return v;
			}

			Node nextnode = node.selectNode(key);
			if (nextnode == null) { // key is already in the node
				return node.entries.put(key, value);
			}

			parent = node;
			node = nextnode;
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation uses the BTree single-pass deletion algorithm. We
	** descend the tree, restructuring it as needed so that we can delete the
	** entry from a leaf.
	**
	** Start at the root node. At each stage of the algorithm, we restructure
	** the tree so that the next node we reach has more than {@link #ENT_MIN}
	** entries, so the deletion can occur withot breaking constraints.
	**
	** (*) If the node is not the root, and if the number of entries is equal
	** to {@link #ENT_MIN}, select its two siblings (L and R). Perform one of
	** the following operations (ties being broken arbitrarily):
	**   - {@link #merge(Node, Node, Node) merge} with X, if X also has {@link
	**     #ENT_MIN} entries
	**   - {@link #rotateL(Node, Node, Node) rotateL} with R, if the R subnode
	**     has more entries than L (or equal)
	**   - {@link #rotateR(Node, Node, Node) rotateR} with L, if the L subnode
	**     has more entries than R (or equal)
	** The selected node now has more than {@link #ENT_MIN} entries.
	**
	** If the node is a leaf, remove the value and stop. Otherwise, if the key
	** is not already in the node, select the appropriate subnode and repeat
	** from (*).
	**
	** Otherwise, select the two subnodes (L and R) that this key separates.
	** Perform one of the following operations (ties being broken arbitrarily):
	**   - {@link #merge(Node, Node, Node) merge}, if both subnodes have {@link
	**     #ENT_MIN} entries.
	**   - {@link #rotateL(Node, Node, Node) rotateL}, if the R subnode has
	**     more entries than L (or equal)
	**   - {@link #rotateR(Node, Node, Node) rotateR}, if the L subnode has
	**     more entries than R (or equal)
	** The node that the key ended up in now has more than {@link #ENT_MIN}
	** entries (and will be selected for the next stage).
	**
	** @throws ClassCastException key cannot be compared with the keys
	**         currently in the map
	** @throws NullPointerException key is {@code null} and this map uses
	**         natural order, or its comparator does not tolerate {@code null}
	**         keys
	*/
	@Override public V remove(Object k) {
		K key = (K) k;
		Node node = root, parent = null;

		for (;;) {
			if (node != root && node.entries.size() == ENT_MIN) {
				Node lnode = parent.nodeL(node.lkey), rnode = parent.nodeR(node.rkey);
				int L = lnode.entries.size(), R = rnode.entries.size();

				K kk = (R > L)? rotateL(parent, node, rnode):
				(L > R)? rotateR(parent, lnode, node):
				// otherwise pick one at "random"
				(size&1) == 1? (R == ENT_MIN? merge(parent, node, rnode):
				                              rotateL(parent, node, rnode)):
				               (L == ENT_MIN? merge(parent, lnode, node):
				                              rotateR(parent, lnode, node));
				node = parent.selectNode(key);
				assert(node != null);
			}
			assert(node.entries.size() > ENT_MIN);

			if (node.isLeaf) { // leaf node
				int sz = node.entries.size();
				V v = node.entries.remove(key);
				// update size cache
				if (node.entries.size() != sz) { --size; }
				return v;
			}

			Node nextnode = node.selectNode(key);
			if (nextnode == null) { // key is already in the node
				Node lnode = node.nodeL(key), rnode = node.nodeR(key);
				int L = lnode.entries.size(), R = rnode.entries.size();

				K kk = (R > L)? rotateL(parent, lnode, rnode):
				(L > R)? rotateR(parent, lnode, rnode):
				// otherwise pick one at "random"
				(size&1) == 1? (R == ENT_MIN? merge(parent, lnode, rnode):
				                              rotateL(parent, lnode, rnode)):
				               (L == ENT_MIN? merge(parent, lnode, rnode):
				                              rotateR(parent, lnode, rnode));
				nextnode = node.selectNode(key);
				assert(nextnode != null);
			}

			parent = node;
			node = nextnode;
		}
	}

	@Override public void putAll(Map<? extends K, ? extends V> map) {
		for (Map.Entry<? extends K, ? extends V> en: map.entrySet()) {
			put(en.getKey(), en.getValue());
		}
	}

	@Override public Set<K> keySet() {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override public Set<Map.Entry<K, V>> entrySet() {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override public Collection<V> values() {
		throw new UnsupportedOperationException("not implemented");
	}

	/*========================================================================
	  public interface Map
	 ========================================================================*/

	@Override public Comparator<? super K> comparator() {
		return comparator;
	}

	@Override public K firstKey() {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override public K lastKey() {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override public SortedMap<K, V> headMap(K rkey) {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override public SortedMap<K, V> tailMap(K lkey) {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override public SortedMap<K, V> subMap(K lkey, K rkey) {
		throw new UnsupportedOperationException("not implemented");
	}

}
