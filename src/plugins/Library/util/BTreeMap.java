/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import plugins.Library.util.CompositeIterable;
import plugins.Library.util.func.Tuples.$2;
import plugins.Library.util.func.Tuples.$3;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.AbstractSet;
import java.util.AbstractMap;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Stack;
import java.util.NoSuchElementException;
import java.util.ConcurrentModificationException;

/**
** General purpose B-tree implementation. '''This class is not a general-use
** {@link SortedMap}'''; for that use {@link TreeMap}.
**
** From [http://en.wikipedia.org/wiki/B-tree wikipedia]: A B-tree is a tree
** data structure that keeps data sorted and allows searches, insertions, and
** deletions in logarithmic amortized time. Unlike self-balancing binary search
** trees, it is optimized for systems that read and write large blocks of data.
** It is most commonly used in databases and filesystems.
**
** In B-trees, internal (non-leaf) nodes can have a variable number of child
** nodes within some pre-defined range. When data is inserted or removed from a
** node, its number of child nodes changes. In order to maintain the
** pre-defined range, internal nodes may be joined or split. Because a range of
** child nodes is permitted, B-trees do not need re-balancing as frequently as
** other self-balancing search trees, but may waste some space, since nodes are
** not entirely full.
**
** A B-tree is kept balanced by requiring that all leaf nodes are at the same
** depth. This depth will increase slowly as elements are added to the tree,
** but an increase in the overall depth is infrequent, and results in all leaf
** nodes being one more node further away from the root.
**
** A B-tree of order m (the maximum number of children for each node) is a tree
** which satisfies the following properties:
**
** * Every node has between m/2-1 and m-1 entries, except for the root node,
**   which has between 1 and m-1 entries.
** * All leaves are the same distance from the root.
** * Every non-leaf node with k entries has k+1 subnodes arranged in sorted
**   order between the entries (for details, see {@link Node}).
**
** B-trees have substantial advantages over alternative implementations when
** node access times far exceed access times within nodes. This usually occurs
** when most nodes are in secondary storage such as hard drives. By maximizing
** the number of child nodes within each internal node, the height of the tree
** decreases, balancing occurs less often, and efficiency increases. Usually
** this value is set such that each node takes up a full disk block or an
** analogous size in secondary storage.
**
** If {@link #NODE_MIN} is {@linkplain #BTreeMap(int) set to} 2, the resulting
** tree is structurally and algorithmically equivalent to a 2-3-4 tree, which
** is itself equivalent to a red-black tree, which is the implementation of
** {@link TreeMap} in the standard Java Class Library. This class has extra
** overheads due to the B-tree generalisation of the structures and algorithms
** involved, and is therefore only meant to be used to represent a B-tree
** stored in secondary storage, as the above paragraph describes.
**
** NOTE: at the moment there is a slight bug in this implementation that causes
** indeterminate behaviour when used with a comparator that admits {@code null}
** keys. This is due to {@code null} having special semantics, meaning "end of
** map", for the {@link Node#lkey} and {@link Node#rkey} fields. This is NOT
** an urgent priority to fix, but might be done in the future. NULLNOTICE marks
** the places in the code which are affected by this logic, as well as all
** occurences of /\.[lr]key == null/.
**
** Note: this implementation, like {@link TreeMap}, is not thread-safe.
**
** @author infinity0
** @see TreeMap
** @see Comparator
** @see Comparable
*/
public class BTreeMap<K, V> extends AbstractMap<K, V>
implements Map<K, V>, SortedMap<K, V>/*, NavigableMap<K, V>, Cloneable, Serializable*/ {

	/*
	** TODO list (most urgent first):
	**
	** # clean up the use of Node._size
	** # use Node.{merge|split} instead of the long code in BTreeMap.{merge|split}
	** # make Node's fields private/immutable
	** # {@link ConcurrentModificationException} for the entrySet iterator
	** # better distribution algorithm for {@link #putAll(Map)}
	**
	***************************************************************************
	** IMPORTANT NOTE FOR MAINTAINERS:
	***************************************************************************
	**
	** When navigating the tree, either Node.isLeaf() or Node.nodeSize() MUST
	** be called at least once **for each node visited**, before accessing or
	** modifying its contents. This is to give SkeletonBTreeMap the chance to
	** throw a DataNotLoadedException in case the node has not been loaded.
	**
	** Additionally, each node has a Node._size field which is a cache for
	** Node.totalSize(), which calculates the number of entries in it and all
	** its children. To invalidate the cache, set Node._size to -1. This must
	** be done whenever an operation has >0 chance of changing the total size.
	**
	*/

	/**
	** Minimum number of children of each node.
	*/
	final public int NODE_MIN;

	/**
	** Maximum number of children of each node. Equal to {@code NODE_MIN * 2}.
	*/
	final public int NODE_MAX;

	/**
	** Minimum number of entries in each node. Equal to {@code NODE_MIN - 1}.
	*/
	final public int ENT_MIN;

	/**
	** Maximum number of entries in each node. Equal to {@code NODE_MAX - 1}.
	*/
	final public int ENT_MAX;

	/**
	** Comparator for this {@link SortedMap}.
	*/
	final protected Comparator<? super K> comparator;

	/**
	** Root node of the tree. The only node that can have less than ENT_MIN
	** entries.
	*/
	protected Node root = newNode(null, null, true);

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
	** DOCUMENT.
	**
	** It is '''assumed''' that neither keys belong to the set; it is up to the
	** caller to ensure that this holds.
	*/
	protected static <K> SortedSet<K> subSet(SortedSet<K> set, K lkey, K rkey) {
		// NULLNOTICE
		return (lkey == null)?
			((rkey == null)? set: set.headSet(rkey)):
			((rkey == null)? set.tailSet(lkey): set.subSet(lkey, rkey));
	}

	/**
	** DOCUMENT.
	*/
	public static class PairIterable<E> implements Iterable<$2<E, E>> {

		final protected E lobj;
		final protected E robj;
		final protected Iterable<E> ib;

		public PairIterable(E lo, Iterable<E> objs, E ro) {
			lobj = lo;
			ib = objs;
			robj = ro;
		}

		public Iterator<$2<E, E>> iterator() {
			return new Iterator<$2<E, E>>() {

				final Iterator<E> it = ib.iterator();
				E prev = lobj;

				public boolean hasNext() {
					return (it.hasNext() || prev != robj);
				}

				public $2<E, E> next() {
					E rk = (!it.hasNext() && prev != robj)? robj: it.next();
					return new $2<E, E>(prev, prev = rk);
				}

				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}
	}

	/**
	** A B-tree node. It has the following structure:
	**
	** * Every entry in a non-leaf node has two adjacent subnodes, holding
	**   entries which are all strictly greater and smaller than the entry.
	** * Conversely, every subnode has two adjacent entries, which are strictly
	**   greater and smaller than every entry in the subnode. Note: edge
	**   subnodes' "adjacent" keys are not part of the node's entries, but
	**   inherited from some ancestor parent.)
	**
	**     ^                        Node                        ^
	**     |                                                    |
	**     |    V1    V2    V3    V4    V5    V6    V7    V8    |
	**     |    |     |     |     |     |     |     |     |     |
	**    lkey  K1    K2    K3    K4    K5    K6    K7    K8  rkey
	**      \  /  \  /  \  /  \  /  \  /  \  /  \  /  \  /  \  /
	**      Node  Node  Node  Node  Node  Node  Node  Node  Node
	**
	**    | - {@link #entries} mappings
	**    \ - {@link #rnodes} mappings
	**    / - {@link #lnodes} mappings
	**
	** Note: before anyone gets any ideas about making this class static
	** (believe me, I tried), note that {@link TreeMap} '''requires''' the
	** comparator to be passed at construction. The neatest way to do this is
	** to pass either the tree or the comparator to the node's constructor.
	**
	** This is fine in the context of {@link BTreeMap}, but serialisation makes
	** it extremely awkard. Passing the tree requires us to extend {@link
	** plugins.Library.io.serial.Translator} to take a context paramater, and
	** passing the comparator requires us to add checks to ensure that the
	** comparators are equal when the node is re-attached to the tree.
	**
	** And even if this is implemented, nodes still need to know their parent
	** tree (to set {@link plugins.Library.io.serial.Serialiser}s etc) and so you
	** would need to code a secondary initialisation scheme to be called after
	** object construction, and after execution returns from {@link
	** plugins.Library.io.serial.Translator#rev(Object)}. All in all, this is more
	** trouble than it's worth.
	**
	** So I've gone with the non-static class, which means a new {@code
	** Translator} needs to be constructed for each new B-tree. This is only
	** two fields above an {@link Object} (if you re-use the same objects for
	** the {@link SkeletonBTreeMap.NodeTranslator#ktr} and {@code
	** SkeletonBTreeMap.NodeTranslator#mtr} fields) and so should be negligible
	** compared to the memory size of the rest of the B-tree.
	**
	** @author infinity0
	*/
	protected class Node implements Comparable<Node> {

		/**
		** Whether this node is a leaf.
		*/
		final protected boolean isLeaf;

		/**
		** Map of entries in this node.
		*/
		final /*private*/ protected SortedMap<K, V> entries;

		/**
		** Map of entries to their immediate smaller nodes. The greatest node
		** is mapped to by {@link #rkey}.
		*/
		final /*private*/ protected Map<K, Node> lnodes;

		/**
		** Map of entries to their immediate greater nodes. The smallest node
		** is mapped to by {@link #lkey}.
		*/
		final /*private*/ protected Map<K, Node> rnodes;

		/**
		** Greatest key smaller than all keys in this node and subnodes. This
		** is {@code null} when the node is at the minimum-key end of the tree.
		*/
		/*final*/ protected K lkey;

		/**
		** Smallest key greater than all keys in this node and subnodes. This
		** is {@code null} when the node is at the maximum-key end of the tree.
		*/
		/*final*/ protected K rkey;

		/**
		** Cache for the total number of entries in this node and all subnodes.
		** A negative value means the cache was invalidated.
		*/
		transient int _size = -1;

		/**
		** Creates a new node for the BTree, with a custom map to store the
		** entries.
		**
		** It is '''assumed''' that the input map is empty; it is up to the
		** caller to ensure that this holds, or if not, then at least put the
		** node into a consistent state before releasing it for use.
		**
		** @param lk The {@code Node#lkey} for the node.
		** @param rk The {@code Node#rkey} for the node.
		** @param lf Whether to create a leaf node
		** @param map A {@link SortedMap} to use to store the entries
		*/
		protected Node(K lk, K rk, boolean lf, SortedMap<K, V> map) {
			lkey = lk;
			rkey = rk;
			isLeaf = lf;
			entries = map;
			// we don't use sentinel Nil elements because that wastes memory due to
			// having to maintain dummy rnodes and lnodes maps.
			lnodes = (lf)? null: new HashMap<K, Node>(NODE_MAX);
			rnodes = (lf)? null: new HashMap<K, Node>(NODE_MAX);
		}

		/**
		** Creates a new node for the BTree
		**
		** @param lk The {@code Node#lkey} for the node.
		** @param rk The {@code Node#rkey} for the node.
		** @param lf Whether to create a leaf node
		*/
		protected Node(K lk, K rk, boolean lf) {
			this(lk, rk, lf, new TreeMap<K, V>(comparator));
		}

		/**
		** Add the given entries and subnodes to this node. The subnodes are
		** added with {@link #addChildNode(BTreeMap.Node)}.
		**
		** It is up to the caller to leave the node in a consistent state.
		**
		** @param ent The entries to add
		** @param nodes The subnodes to add
		*/
		protected void addAll(SortedMap<K, V> ent, Iterable<? extends Node> nodes) {
			assert(ent.comparator() == comparator());
			assert(compareL(lkey, ent.firstKey()) < 0);
			assert(compareR(ent.lastKey(), rkey) < 0);
			entries.putAll(ent);
			if (!isLeaf) {
				for (Node ch: nodes) {
					addChildNode(ch);
				}
			}
		}

		/**
		** @return Number of subnodes
		*/
		public int childCount() {
			return rnodes.size();
		}

		/**
		** @return Number of local entries
		*/
		public int nodeSize() {
			return entries.size();
		}

		/**
		** @return Whether this is a leaf node
		*/
		public boolean isLeaf() {
			return isLeaf;
		}

		/**
		** @return Whether the node is a ghost. This cannot be {@code true} for
		**         normal maps, but is used by {@link SkeletonBTreeMap}.
		*/
		public boolean isGhost() {
			return entries == null;
		}

		/**
		** @return Total number of entries in this node and all subnodes
		*/
		protected int totalSize() {
			if (_size < 0) {
				int s = nodeSize(); // makes any future synchronization easier
				if (!isLeaf()) {
					for (Node n: lnodes.values()) {
						s += n.totalSize();
					}
				}
				_size = s;
			}
			return _size;
		}

		/**
		** Returns the greatest subnode smaller than the given node.
		**
		** It is '''assumed''' that the input is an actual subnode of this
		** node; it is up to the caller to ensure that this holds.
		**
		** @param node The node to lookup its neighbour for
		** @return The neighbour node, or null if the input node is at the edge
		** @throws NullPointerException if this is a leaf node
		*/
		public Node nodeL(Node node) {
			assert(lnodes.get(node.rkey) == node
			    && rnodes.get(node.lkey) == node);

			return (compare0(lkey, node.lkey))? null: lnodes.get(node.lkey);
		}

		/**
		** Returns the smallest subnode greater than the given node.
		**
		** It is '''assumed''' that the input is an actual subnode of this
		** node; it is up to the caller to ensure that this holds.
		**
		** @param node The node to lookup its neighbour for
		** @return The neighbour node, or null if the input node is at the edge
		** @throws NullPointerException if this is a leaf node
		*/
		public Node nodeR(Node node) {
			assert(lnodes.get(node.rkey) == node
			    && rnodes.get(node.lkey) == node);

			return (compare0(node.rkey, rkey))? null: rnodes.get(node.rkey);
		}

		/**
		** Returns the subnode a that given key belongs in. The key must not be
		** local to the node.
		**
		** It is '''assumed''' that the input key is in the correct range; it
		** is up to the caller to ensure that this holds.
		**
		** @param key The key to select the node for
		** @return The subnode for the key, or null if the key is local to the
		**         node
		** @throws NullPointerException if this is a leaf node; or if the key
		**         is {@code null} and the entries map uses natural order, or
		**         its comparator does not tolerate {@code null} keys
		*/
		public Node selectNode(K key) {
			assert(compareL(lkey, key) < 0 && compareR(key, rkey) < 0);

			SortedMap<K, V> tailmap = entries.tailMap(key);
			if (tailmap.isEmpty()) {
				return lnodes.get(rkey);
			} else {
				K next = tailmap.firstKey();
				return (compare(key, next) == 0)? null: lnodes.get(next);
			}
		}

		/**
		** Attaches a child via its {@code lkey}, {@code rkey} fields.
		**
		** This is the same as {@link #addChildNode(BTreeMap.Node)}, except
		** this has {@code assert}s to check that a child with the same range
		** is already present in the node.
		**
		** @param child The child to attach
		*/
		protected void setChildNode(Node child) {
			assert(lnodes.containsKey(child.rkey));
			assert(rnodes.containsKey(child.lkey));
			assert(lnodes.get(child.rkey) == rnodes.get(child.lkey));
			lnodes.put(child.rkey, child);
			rnodes.put(child.lkey, child);
		}

		/**
		** Attaches a child via its {@code lkey}, {@code rkey} fields.
		**
		** @param child The child to attach
		*/
		protected void addChildNode(Node child) {
			assert(rkey == child.rkey || entries.containsKey(child.rkey));
			assert(lkey == child.lkey || entries.containsKey(child.lkey));
			lnodes.put(child.rkey, child);
			rnodes.put(child.lkey, child);
		}

		private transient Iterable<$3<K, Node, K>> _iterNodesK;
		/**
		** A view of all subnodes with their lkey and rkey, as an {@link
		** Iterable}. Iteration occurs in '''sorted''' order.
		**
		** TODO this method does not check that this node actually has subnodes
		** (ie. non-leaf). This may or may not change in the future.
		*/
		public Iterable<$3<K, Node, K>> iterNodesK() {
			if (_iterNodesK == null) {
				_iterNodesK = new CompositeIterable<$2<K, K>, $3<K, Node, K>>(iterKeyPairs(), true) {
					@Override protected $3<K, Node, K> nextFor($2<K, K> kp) {
						return new $3<K, Node, K>(kp._0, rnodes.get(kp._0), kp._1);
					}
				};
			}
			return _iterNodesK;
		}

		private transient Iterable<Node> _iterNodes;
		/**
		** A view of all subnodes as an {@link Iterable}. Iteration occurs in
		** '''no particular order'''.
		**
		** TODO this method does not check that this node actually has subnodes
		** (ie. non-leaf). This may or may not change in the future.
		*/
		public Iterable<Node> iterNodes() {
			if (_iterNodes == null) {
				_iterNodes = new CompositeIterable<Node, Node>(rnodes.values(), true) {
					@Override protected Node nextFor(Node n) { return n; }
				};
			}
			return _iterNodes;
		}

		private transient Iterable<K> _iterKeys;
		/**
		** A view of all local keys as an {@link Iterable}. Iteration occurs
		** in '''sorted''' order.
		*/
		public Iterable<K> iterKeys() {
			if (_iterKeys == null) {
				_iterKeys = new CompositeIterable<K, K>(entries.keySet(), true) {
					@Override protected K nextFor(K k) { return k; }
				};
			}
			return _iterKeys;
		}

		private transient Iterable<K> _iterAllKeys;
		/**
		** A view of all keys (including {@code lkey}, {@code rkey}) as an
		** {@link Iterable}. Iteration occurs in '''sorted''' order.
		*/
		public Iterable<K> iterAllKeys() {
			if (_iterAllKeys == null) {
				_iterAllKeys = new Iterable<K>() {
					public Iterator<K> iterator() {
						return new Iterator<K>() {

							final Iterator<K> it = entries.keySet().iterator();
							byte stage = 0;

							public boolean hasNext() {
								return (stage < 2);
							}

							public K next() {
								switch (stage) {
								case 0:
									stage = 1;
									return lkey;
								case 1:
									if (it.hasNext()) { return it.next(); }
									else { stage = 2; return rkey; }
								case 2:
									return it.next(); // throws NoSuchElementException
								}
								throw new AssertionError();
							}

							public void remove() {
								throw new UnsupportedOperationException();
							}
						};
					}
				};
			}
			return _iterAllKeys;
		}

		private transient Iterable<$2<K, K>> _iterKeyPairs;
		/**
		** A view of adjacent key pairs (including {@code lkey}, {@code rkey})
		** as an {@link Iterable}. Iteration occurs in '''sorted''' order.
		*/
		public Iterable<$2<K, K>> iterKeyPairs() {
			if (_iterKeyPairs == null) {
				// TODO replace this with PairIterable when we make lkey/rkey final
				_iterKeyPairs = new Iterable<$2<K, K>>() {
					public Iterator<$2<K, K>> iterator() {
						return new Iterator<$2<K, K>>() {

							final Iterator<K> it = entries.keySet().iterator();
							K lastkey = lkey;

							public boolean hasNext() {
								return (it.hasNext() || lastkey != rkey);
							}

							public $2<K, K> next() {
								K rk = (!it.hasNext() && lastkey != rkey)? rkey: it.next();
								return new $2<K, K>(lastkey, lastkey = rk);
							}

							public void remove() {
								throw new UnsupportedOperationException();
							}
						};
					}
				};
			}
			return _iterKeyPairs;
		}

		/**
		** A view of child nodes exclusively between {@code lk}, {@code rk}, as
		** an {@link Iterable}. Iteration occurs in '''sorted''' order.
		**
		** It is '''assumed''' that the input keys are all local to the node,
		** and in the correct order; it is up to the caller to ensure that this
		** holds.
		**
		** Returns {@code null} if this is a leaf.
		**
		** @param lk The key on the L-side, exclusive
		** @param rk The key on the R-side, exclusive
		*/
		protected Iterable<Node> iterNodes(K lk, K rk) {
			assert(lk == null || rk == null || compare(lk, rk) < 0);
			return (isLeaf)? null: new CompositeIterable<K, Node>(entries.subMap(lk, rk).keySet()) {
				@Override public Node nextFor(K key) { return rnodes.get(key); }
			};
		}

		/**
		** A view of local entries exclusively between {@code lk}, {@code rk},
		** as a {@link SortedMap}.
		**
		** It is '''assumed''' that the input keys are all local to the node,
		** and in the correct order; it is up to the caller to ensure that this
		** holds.
		**
		** @param lk The key on the L-side, exclusive
		** @param rk The key on the R-side, exclusive
		*/
		protected SortedMap<K, V> subEntries(K lk, K rk) {
			assert(lk == null || rk == null || compare(lk, rk) < 0);
			SortedMap<K, V> tmap = entries.tailMap(lk);
			K k2 = tmap.keySet().iterator().next();
			return entries.subMap(k2, rk);
		}

		/**
		** DOCUMENT
		**
		** It is '''assumed''' that the input keys are all local to the node,
		** and in the correct order; it is up to the caller to ensure that this
		** holds.
		**
		** @param lk The {@code lkey} of the new node
		** @param keys The keys of this node at which to merge
		** @param rk The {@code rkey} of the new node
		*/
		protected void merge(K lk, Iterable<K> keys, K rk) {
			Iterator<Node> it = iterNodes(lk, rk).iterator();
			Node child = it.next();
			assert(child == rnodes.get(lk));

			for (K k: keys) {
				child.entries.put(k, entries.remove(k));
			}

			while (it.hasNext()) {
				Node next = it.next();
				child.addAll(next.entries, (next.isLeaf)? null: next.iterNodes());
			}

			child.rkey = rk;
		}

		/**
		** DOCUMENT
		**
		** It is '''assumed''' that the input keys are all local to the node,
		** and in the correct order; it is up to the caller to ensure that this
		** holds.
		**
		** @param lk The {@code lkey} of the child node
		** @param keys The keys of the child node at which to split
		** @param rk The {@code rkey} of the child node
		*/
		protected void split(K lk, Iterable<K> keys, K rk) {
			Node child = rnodes.get(lk);
			assert(child.rkey == rk);

			for (K k: keys) {
				entries.put(k, child.entries.get(k));
			}

			for ($2<K, K> kp: new PairIterable<K>(lk, keys, rk)) {
				Node newnode = newNode(kp._0, kp._1, child.isLeaf);
				newnode.addAll(child.subEntries(kp._0, kp._1), child.iterNodes(kp._0, kp._1));
				addChildNode(newnode);
			}
		}

		/**
		** Defines a total ordering on the nodes. This is useful for eg. deciding
		** which nodes to visit first in a breadth-first search algorithm that uses
		** a priority queue ({@link java.util.concurrent.PriorityBlockingQueue})
		** instead of a FIFO ({@link java.util.concurrent.LinkedBlockingQueue}).
		**
		** A node is "compare-equal" to another iff:
		**
		** * Both their corresponding endpoints are compare-equal.
		**
		** A node is "smaller" than another iff:
		**
		** * Its right endpoint is strictly smaller than the other's, or
		** * Its right endpoint is compare-equal to the other's, and its left
		**   endpoint is strictly greater than the other's. (Note that this case
		**   would never be reached in a breadth-first search.)
		**
		** Or, equivalently, and perhaps more naturally (but less obvious why it
		** is a total order):
		**
		** * Both its endpoints are strictly smaller than the other's corresponding
		**   endpoints, or
		** * Both of its endpoints are contained within the other's endpoints,
		**   and one of them strictly so.
		**
		** @param n The other node to compare with
		*/
		/*@Override**/ public int compareTo(Node n) {
			int b = compareR(rkey, n.rkey);
			return (b == 0)? compareL(n.lkey, lkey): b;
			// the more "natural" version
			// return (a == 0)? ((b == 0)?  0: (b < 0)? -1:  1)
			//         (a < 0)? ((b == 0)?  1: (b < 0)? -1:  1):
			//                  ((b == 0)? -1: (b < 0)? -1:  1);
		}

		public String toTreeString(String istr) {
			String nistr = istr + "\t";
			StringBuilder s = new StringBuilder();
			s.append(istr).append('(').append(lkey).append(')').append('\n');
			if (isLeaf) {
				for (Map.Entry<K, V> en: entries.entrySet()) {
					s.append(istr).append(en.getKey()).append(" : ").append(en.getValue()).append('\n');
				}
			} else {
				s.append(rnodes.get(lkey).toTreeString(nistr));
				for (Map.Entry<K, V> en: entries.entrySet()) {
					s.append(istr).append(en.getKey()).append(" : ").append(en.getValue()).append('\n');
					s.append(rnodes.get(en.getKey()).toTreeString(nistr));
				}
			}
			s.append(istr).append('(').append(rkey).append(')').append('\n');
			return s.toString();
		}

		public String getName() {
			return "BTreeMap node " + getRange();
		}

		public String getRange() {
			return (lkey == null? "*": lkey) + "-" + (rkey == null? "*": rkey);
		}

	}

	public String toTreeString() {
		return root.toTreeString("");
	}

	/**
	** Compares two keys using the comparator for this tree, or the keys'
	** {@link Comparable natural} ordering if no comparator was given.
	**
	** @throws ClassCastException if the keys cannot be compared by the given
	**         comparator, or if they cannot be compared naturally (ie. they
	**         don't implement {@link Comparable})
	** @throws NullPointerException if either of the keys are {@code null}.
	*/
	public int compare(K key1, K key2) {
		return (comparator != null)? comparator.compare(key1, key2): ((Comparable<K>)key1).compareTo(key2);
	}

	/**
	** Tests for compare-equality. Two values are compare-equal if they are
	** both {@code null} or they {@link #compare(Object, Object)} to {@code 0}.
	** We use this rather than {@link #equals(Object)} to maintain sorting
	** order consistency in case the comparator is inconsistent with equals.
	**
	** @throws ClassCastException if the keys cannot be compared by the given
	**         comparator, or if they cannot be compared naturally (ie. they
	**         don't implement {@link Comparable})
	*/
	protected boolean compare0(K key1, K key2) {
		return key1 == null? key2 == null: key2 == null? false:
			   ((comparator != null)? comparator.compare(key1, key2): ((Comparable<K>)key1).compareTo(key2)) == 0;
	}

	/**
	** Comparison between {@link Node#lkey} values, where {@code null} means
	** "a value smaller than any other". Use this when one of the operands is
	** the {@code lkey} of some node.
	**
	** @throws ClassCastException if the keys cannot be compared by the given
	**         comparator, or if they cannot be compared naturally (ie. they
	**         don't implement {@link Comparable})
	*/
	protected int compareL(K lkey1, K lkey2) {
		return (lkey1 == null)? ((lkey2 == null)? 0: -1):
		                        ((lkey2 == null)? 1: compare(lkey1, lkey2));
	}

	/**
	** Comparison between {@link Node#rkey} values, where {@code null} means
	** "a value greater than any other". Use this when one of the operands is
	** the {@code rkey} of some node.
	**
	** @throws ClassCastException if the keys cannot be compared by the given
	**         comparator, or if they cannot be compared naturally (ie. they
	**         don't implement {@link Comparable})
	*/
	protected int compareR(K rkey1, K rkey2) {
		return (rkey2 == null)? ((rkey1 == null)? 0: -1):
		                        ((rkey1 == null)? 1: compare(rkey1, rkey2));
	}

	/**
	** Simulates an assertion.
	**
	** @param b The test condition
	** @throws IllegalStateException if the test condition is false
	*/
	final static void verify(boolean b) {
		if (!b) { throw new IllegalStateException("Verification failed"); }
	}

	/**
	** Package-private debugging method. This one checks node integrity:
	**
	** * lkey < firstkey, lastkey < rkey
	**
	** For non-leaf nodes:
	**
	** * for each pair of adjacent keys (including two null keys either side of
	**   the list of keys), it is possible to traverse between the keys, and
	**   the single node between them, using the lnodes, rnodes maps and
	**   the lkey, rkey pointers.
	** * lnodes' and rnodes' size is 1 greater than that of entries. Ie. there
	**   is nothing else in the node beyond what we visited in the previous
	**   step.
	**
	** It also checks the BTree node constraints:
	**
	** * The node has at most ENT_MAX entries.
	** * The node has at least ENT_MIN entries, except the root.
	** * The root has at least 1 entry.
	**
	** @throws IllegalStateException if the constraints are not satisfied
	*/
	final void verifyNodeIntegrity(Node node) {
		if (node.entries.isEmpty()) {
			// don't test node == root since it might be a newly-created detached node
			verify(node.lkey == null && node.rkey == null);
			verify(node.isLeaf() && node.rnodes == null && node.lnodes == null);
			return;
		}

		verify(compareL(node.lkey, node.entries.firstKey()) < 0);
		verify(compareR(node.entries.lastKey(), node.rkey) < 0);

		int s = node.nodeSize();
		if (!node.isLeaf()) {
			Iterator<K> it = node.entries.keySet().iterator();
			verify(it.hasNext());

			K prev = node.lkey;
			K curr = it.next();
			while (curr != node.rkey || prev != node.rkey) {
				Node node1 = node.rnodes.get(prev);
				verify(node1 != null);
				s += node1.totalSize();
				verify(compare0(curr, node1.rkey));
				Node node2 = node.lnodes.get(curr);
				verify(node1 == node2 && compare0(node2.lkey, prev));
				prev = curr;
				curr = it.hasNext()? it.next(): node.rkey;
			}

			verify(node.nodeSize() + 1 == node.rnodes.size());
			verify(node.nodeSize() + 1 == node.lnodes.size());
		}
		/* DEBUG if (node._size > 0 && node._size != s) {
			System.out.println(node._size + " vs " + s);
			System.out.println(node.toTreeString("\t"));
		}*/
		verify(node._size < 0 || node._size == s);

		verify(node.nodeSize() <= ENT_MAX);
		// don't test node == root since it might be a newly-created detached node
		verify(node.lkey == null && node.rkey == null && node.nodeSize() >= 1
		                    || node.nodeSize() >= ENT_MIN);
	}

	/**
	** Package-private debugging method. This one checks BTree constraints for
	** the subtree rooted at a given node:
	**
	** * All nodes follow the node constraints, listed above.
	** * All leaves appear in the same level
	**
	** @return depth of all leaves
	** @throws IllegalStateException if the constraints are not satisfied
	*/
	final int verifyTreeIntegrity(Node node) {
		verifyNodeIntegrity(node);
		if (node.isLeaf()) {
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
	final void verifyTreeIntegrity() {
		verifyTreeIntegrity(root);
	}

	/**
	** Creates a new node.
	**
	** @param lk The {@code Node#lkey} for the node.
	** @param rk The {@code Node#rkey} for the node.
	** @param lf Whether the node is a leaf.
	*/
	protected Node newNode(K lk, K rk, boolean lf) {
		return new Node(lk, rk, lf);
	}

	/**
	** Split a maximal child node into two minimal nodes, using the median key
	** as the separator between these new nodes in the parent. If the parent is
	** {@code null}, creates a new {@link Node} and points {@link #root} to it.
	**
	** It is '''assumed''' that the child is an actual subnode of the parent,
	** and that it is full. It is up to the caller to ensure that this holds.
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
		assert(child.nodeSize() == ENT_MAX);
		assert(parent == null? (child.lkey == null && child.rkey == null):
		                       (parent.nodeSize() < ENT_MAX
		                     && !parent.isLeaf()
		                     && parent.rnodes.get(child.lkey) == child
		                     && parent.lnodes.get(child.rkey) == child));

		if (parent == null) {
			parent = root = newNode(null, null, false);
			parent.addChildNode(child);
		}
		parent._size = child._size = -1;

		Node lnode = newNode(null, null, child.isLeaf());
		K mkey;

		if (child.isLeaf()) {
			// this is just the same as the code in the else block, but with leaf
			// references to rnodes and lnodes removed (since they are null)
			Iterator<Map.Entry<K, V>> it = child.entries.entrySet().iterator();
			for (int i=0; i<ENT_MIN; ++i) {
				Map.Entry<K, V> entry = it.next(); it.remove();
				K key = entry.getKey();

				lnode.entries.put(key, entry.getValue());
			}
			Map.Entry<K, V> median = it.next(); it.remove();
			mkey = median.getKey();

			lnode.lkey = child.lkey;
			lnode.rkey = child.lkey = mkey;

			parent.rnodes.put(lnode.lkey, lnode);
			parent.lnodes.put(child.rkey, child);
			parent.entries.put(mkey, median.getValue());
			parent.rnodes.put(mkey, child);
			parent.lnodes.put(mkey, lnode);

		} else {
			lnode.rnodes.put(child.lkey, child.rnodes.remove(child.lkey));
			Iterator<Map.Entry<K, V>> it = child.entries.entrySet().iterator();
			for (int i=0; i<ENT_MIN; ++i) {
				Map.Entry<K, V> entry = it.next(); it.remove();
				K key = entry.getKey();

				lnode.entries.put(key, entry.getValue());
				lnode.lnodes.put(key, child.lnodes.remove(key));
				lnode.rnodes.put(key, child.rnodes.remove(key));
			}
			Map.Entry<K, V> median = it.next(); it.remove();
			mkey = median.getKey();
			lnode.lnodes.put(mkey, child.lnodes.remove(mkey));

			lnode.lkey = child.lkey;
			lnode.rkey = child.lkey = mkey;

			parent.rnodes.put(lnode.lkey, lnode);
			parent.lnodes.put(child.rkey, child);
			parent.entries.put(mkey, median.getValue());
			parent.rnodes.put(mkey, child);
			parent.lnodes.put(mkey, lnode);
		}

		assert(parent.rnodes.get(mkey) == child);
		assert(parent.lnodes.get(mkey) == lnode);
		return mkey;
	}

	/**
	** Merge two minimal child nodes into a maximal node, using the key that
	** separates them in the parent as the key that joins the halfnodes in the
	** merged node. If the parent is the {@link #root} and the merge makes it
	** empty, point {@code root} to the new merged node.
	**
	** It is '''assumed''' that both childs are actual subnodes of the parent,
	** that they are adjacent in the parent, and that they are minimally full.
	** It is up to the caller to ensure that this holds.
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
		assert(compare(lnode.rkey, rnode.lkey) == 0); // not compare0 since can't be at edges
		assert(lnode.isLeaf() && rnode.isLeaf() || !lnode.isLeaf() && !rnode.isLeaf());
		assert(lnode.nodeSize() == ENT_MIN);
		assert(rnode.nodeSize() == ENT_MIN);
		assert(parent == root && parent.nodeSize() > 0
		                      || parent.nodeSize() > ENT_MIN);
		assert(!parent.isLeaf() && parent.rnodes.get(lnode.rkey) == rnode
		                      && parent.lnodes.get(rnode.lkey) == lnode);
		parent._size = lnode._size = rnode._size = -1;

		K mkey = rnode.lkey; // same as lnode.rkey;

		if (rnode.isLeaf()) {
			// this is just the same as the code in the else block, but with leaf
			// references to rnodes and lnodes removed (since they are null)
			rnode.entries.putAll(lnode.entries);

			rnode.entries.put(mkey, parent.entries.remove(mkey));
			rnode.lkey = lnode.lkey;

			parent.rnodes.remove(mkey);
			parent.lnodes.remove(mkey);
			parent.rnodes.put(lnode.lkey, rnode);

		} else {
			rnode.entries.putAll(lnode.entries);
			rnode.lnodes.putAll(lnode.lnodes);
			rnode.rnodes.putAll(lnode.rnodes);

			rnode.entries.put(mkey, parent.entries.remove(mkey));
			rnode.lnodes.put(mkey, lnode.lnodes.get(mkey));
			rnode.rnodes.put(lnode.lkey, lnode.rnodes.get(lnode.lkey));
			rnode.lkey = lnode.lkey;

			parent.rnodes.remove(mkey);
			parent.lnodes.remove(mkey);
			parent.rnodes.put(lnode.lkey, rnode);

		}

		if (parent == root && parent.entries.isEmpty()) {
			assert(parent.lkey == null && parent.rkey == null
			    && rnode.lkey == null && rnode.rkey == null);
			root = rnode;
		}

		assert(parent.rnodes.get(rnode.lkey) == rnode);
		assert(parent.lnodes.get(rnode.rkey) == rnode);
		return mkey;
	}

	/**
	** Performs a rotate operation towards the smaller node. A rotate operation
	** is where:
	**
	** * an entry M from the parent node is moved to a subnode
	** * an entry S from an adjacent subnode is moved to fill gap left by M
	** * a subnode N associated with S is cut from M and attached to S
	**
	** with the operands chosen appropriately to maintain tree constraints.
	**
	** Here, M is the key that separates {@code lnode} and {@code rnode}, S is
	** the smallest key of {@code rnode}, and N its smallest subnode.
	**
	** It is '''assumed''' that both childs are actual subnodes of the parent,
	** that they are adjacent in the parent, and that the child losing an entry
	** has more than {@link #ENT_MIN} entries. It is up to the caller to ensure
	** that this holds.
	**
	** @param parent The parent node of the children.
	** @param lnode The smaller subnode, which accepts an entry from the parent
	** @param rnode The greater subnode, which loses an entry to the parent
	*/
	private K rotateL(Node parent, Node lnode, Node rnode) {
		assert(compare(lnode.rkey, rnode.lkey) == 0); // not compare0 since can't be at edges
		assert(lnode.isLeaf() && rnode.isLeaf() || !lnode.isLeaf() && !rnode.isLeaf());
		assert(rnode.nodeSize() >= lnode.nodeSize());
		assert(rnode.nodeSize() > ENT_MIN);
		assert(!parent.isLeaf() && parent.rnodes.get(lnode.rkey) == rnode
		                      && parent.lnodes.get(rnode.lkey) == lnode);
		parent._size = lnode._size = rnode._size = -1;

		K mkey = rnode.lkey;
		K skey = rnode.entries.firstKey();

		lnode.entries.put(mkey, parent.entries.remove(mkey));
		parent.entries.put(skey, rnode.entries.remove(skey));
		parent.rnodes.put(skey, parent.rnodes.remove(mkey));
		parent.lnodes.put(skey, parent.lnodes.remove(mkey));

		lnode.rkey = rnode.lkey = skey;

		if (!lnode.isLeaf()) {
			lnode.rnodes.put(mkey, rnode.rnodes.remove(mkey));
			lnode.lnodes.put(skey, rnode.lnodes.remove(skey));
		}

		assert(parent.rnodes.get(skey) == rnode);
		assert(parent.lnodes.get(skey) == lnode);
		return mkey;
	}

	/**
	** Performs a rotate operation towards the greater node. A rotate operation
	** is where:
	**
	** * an entry M from the parent node is moved to a subnode
	** * an entry S from an adjacent subnode is moved to fill gap left by M
	** * a subnode N associated with S is cut from M and attached to S
	**
	** with the operands chosen appropriately to maintain tree constraints.
	**
	** Here, M is the key that separates {@code lnode} and {@code rnode}, S is
	** the greatest key of {@code rnode}, and N its greatest subnode.
	**
	** It is '''assumed''' that both childs are actual subnodes of the parent,
	** that they are adjacent in the parent, and that the child losing an entry
	** has more than {@link #ENT_MIN} entries. It is up to the caller to ensure
	** that this holds.
	**
	** @param parent The parent node of the children.
	** @param lnode The smaller subnode, which loses an entry to the parent
	** @param rnode The greater subnode, which accepts an entry from the parent
	*/
	private K rotateR(Node parent, Node lnode, Node rnode) {
		assert(compare(lnode.rkey, rnode.lkey) == 0); // not compare0 since can't be at edges
		assert(lnode.isLeaf() && rnode.isLeaf() || !lnode.isLeaf() && !rnode.isLeaf());
		assert(lnode.nodeSize() >= rnode.nodeSize());
		assert(lnode.nodeSize() > ENT_MIN);
		assert(!parent.isLeaf() && parent.rnodes.get(lnode.rkey) == rnode
		                      && parent.lnodes.get(rnode.lkey) == lnode);
		parent._size = lnode._size = rnode._size = -1;

		K mkey = lnode.rkey;
		K skey = lnode.entries.lastKey();

		rnode.entries.put(mkey, parent.entries.remove(mkey));
		parent.entries.put(skey, lnode.entries.remove(skey));
		parent.lnodes.put(skey, parent.lnodes.remove(mkey));
		parent.rnodes.put(skey, parent.rnodes.remove(mkey));

		lnode.rkey = rnode.lkey = skey;

		if (!rnode.isLeaf()) {
			rnode.lnodes.put(mkey, lnode.lnodes.remove(mkey));
			rnode.rnodes.put(skey, lnode.rnodes.remove(skey));
		}

		assert(parent.rnodes.get(skey) == rnode);
		assert(parent.lnodes.get(skey) == lnode);
		return mkey;
	}

	/**
	** Returns the number of entries contained in the root node.
	*/
	public int sizeRoot() {
		return root.nodeSize();
	}

	public int nodeMin() {
		return NODE_MIN;
	}

	public int entMax() {
		return ENT_MAX;
	}

	/**
	** Gives a quick estimate of the height of the tree. This method will tend
	** to over-estimate rather than underestimate.
	**
	** The exact formula is {@code 1 + floor(log(size)/log(NODE_MIN))}. I
	** couldn't be bothered trying to prove that this is a firm upper bound;
	** feel free.
	*/
	public int heightEstimate() {
		// round(log(0)/x) is -infinity
		return 1 + size == 0? 0: (int)Math.floor(Math.log(size) / Math.log(NODE_MIN));
	}

	/**
	** Returns the minimum number of nodes (at a single level) needed for the
	** given number of entries.
	*/
	public int minNodesFor(int entries) {
		// find the smallest k such that k * ENT_MAX + (k-1) >= n
		// ie. k * NODE_MAX > n :: k = floor( n / NODE_MAX ) + 1
		return entries / NODE_MAX + 1;
	}

	/**
	** Returns the minimum number of separator keys (at a single level) needed
	** for the given number of entries.
	*/
	public int minKeysFor(int entries) {
		// 1 less than minNodesFor()
		return entries / NODE_MAX;
	}

	/**
	** Returns the entry at a particular (zero-based) index.
	*/
	public Map.Entry<K, V> getEntry(int index) {
		if (index < 0 || index >= size) {
			throw new IndexOutOfBoundsException("Index outside of range [0," + size + ")");
		}

		int current = 0;
		Node node = root;

		for (;;) {
			if (node.isLeaf()) {
				for (Map.Entry<K, V> en: node.entries.entrySet()) {
					if (current == index) { return en; }
					++current;
				}
				throw new IllegalStateException("BTreeMap getKey method is buggy, please report.");
			}

			// OPT LOW better way to do this than linear iteration through the entries.
			// this performs badly when large nodes are accessed repeatedly.
			//
			// one way would be to insert the last sum-key pair we calculate at each node, into
			// a cache of SoftReference<TreeSet<Integer, K>> for that node. then, next time we visit
			// the same node, we can use this cache to generate a submap - ie.
			//   node.entries.tailMap(cache.get(cache.tailMap(index).firstKey())) // firstEntry().getValue() is Java 6 only :(
			// - and iterate only over this submap (don't forget to set "current" to the right value)
			//
			Node nextnode = node.rnodes.get(node.lkey);
			int next = current + nextnode.totalSize();
			if (index < next) { node = nextnode; continue; }
			current = next;

			for (Map.Entry<K, V> en: node.entries.entrySet()) {
				if (current == index) { return en; }
				++current;

				nextnode = node.rnodes.get(en.getKey());
				next = current + nextnode.totalSize();
				if (index < next) { node = nextnode; break; }
				current = next;
			}

			assert(current <= index);
		}
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
		root = newNode(null, null, true);
		size = 0;
	}

	/**
	** {@inheritDoc}
	**
	** This implementation just descends the tree, returning {@code true} if it
	** finds the given key.
	**
	** @throws ClassCastException key cannot be compared with the keys
	**         currently in the map
	** @throws NullPointerException key is {@code null} and this map uses
	**         natural order, or its comparator does not tolerate {@code null}
	**         keys
	*/
	@Override public boolean containsKey(Object k) {
		K key = (K) k;
		Node node = root;

		for (;;) {
			if (node.isLeaf()) {
				return node.entries.containsKey(key);
			}

			Node nextnode = node.selectNode(key);
			if (nextnode == null) {
				return true;
			}

			node = nextnode;
		}
	}

	/* provided by AbstractMap
	@Override public boolean containsValue(Object key) {
		throw new UnsupportedOperationException("not implemented");
	}*/

	/**
	** {@inheritDoc}
	**
	** This implementation just descends the tree, returning the value for the
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
			if (node.isLeaf()) {
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
	** entries, and the insertion can occur without breaking constraints.
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
		if (key == null) { throw new UnsupportedOperationException("Sorry, this BTreeMap implementation can't handle null keys, even if the comparator supports it."); }
		Node node = root, parent = null;

		for (;;) {
			node._size = -1; // pre-emptively invalidate node size cache

			if (node.nodeSize() == ENT_MAX) {
				K median = split(parent, node);
				if (parent == null) { parent = root; }

				node = parent.selectNode(key);
				if (node == null) { return parent.entries.put(key, value); }
			}
			assert(node.nodeSize() < ENT_MAX);

			if (node.isLeaf()) {
				int sz = node.nodeSize();
				V v = node.entries.put(key, value);
				if (node.nodeSize() != sz) { ++size; } // update tree size counter
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
	** entries, and the deletion can occur without breaking constraints.
	**
	** (*) If the node is not the root, and if the number of entries is equal
	** to {@link #ENT_MIN}, select its two siblings (L and R). Perform one of
	** the following operations (ties being broken arbitrarily):
	**
	** * {@link #merge(Node, Node, Node) merge} with X, if X also has {@link
	**   #ENT_MIN} entries
	** * {@link #rotateL(Node, Node, Node) rotateL} with R, if the R subnode
	**   has more entries than L (or equal)
	** * {@link #rotateR(Node, Node, Node) rotateR} with L, if the L subnode
	**   has more entries than R (or equal)
	**
	** The selected node now has more than {@link #ENT_MIN} entries.
	**
	** If the node is a leaf, remove the value and stop. Otherwise, if the key
	** is not already in the node, select the appropriate subnode and repeat
	** from (*).
	**
	** Otherwise, select the two subnodes (L and R) that this key separates.
	** Perform one of the following operations (ties being broken arbitrarily):
	**
	** * {@link #merge(Node, Node, Node) merge}, if both subnodes have {@link
	**   #ENT_MIN} entries.
	** * {@link #rotateL(Node, Node, Node) rotateL}, if the R subnode has more
	**   entries than L (or equal)
	** * {@link #rotateR(Node, Node, Node) rotateR}, if the L subnode has more
	**   entries than R (or equal)
	**
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
			node._size = -1; // pre-emptively invalidate node size cache

			if (node != root && node.nodeSize() == ENT_MIN) {
				Node lnode = parent.nodeL(node), rnode = parent.nodeR(node);
				int L = (lnode == null)? -1: lnode.nodeSize();
				int R = (rnode == null)? -1: rnode.nodeSize();

				K kk = // in java, ?: must be used in a statement :|
				// lnode doesn't exist
				(L < 0)? ((R == ENT_MIN)? merge(parent, node, rnode):
				                          rotateL(parent, node, rnode)):
				// rnode doesn't exist
				(R < 0)? ((L == ENT_MIN)? merge(parent, lnode, node):
				                          rotateR(parent, lnode, node)):
				// pick the node with more entries
				(R > L)? rotateL(parent, node, rnode):
				(L > R)? rotateR(parent, lnode, node):
				// otherwise pick one at "random"
				(size&1) == 1? (R == ENT_MIN? merge(parent, node, rnode):
				                              rotateL(parent, node, rnode)):
				               (L == ENT_MIN? merge(parent, lnode, node):
				                              rotateR(parent, lnode, node));
				node = parent.selectNode(key);
				assert(node != null);
			}
			assert(node == root || node.nodeSize() >= ENT_MIN);

			if (node.isLeaf()) { // leaf node
				int sz = node.nodeSize();
				V v = node.entries.remove(key);
				if (node.nodeSize() != sz) { --size; } // update tree size counter
				return v;
			}

			Node nextnode = node.selectNode(key);
			if (nextnode == null) { // key is already in the node
				Node lnode = node.lnodes.get(key), rnode = node.rnodes.get(key);
				int L = lnode.nodeSize(), R = rnode.nodeSize();

				K kk =
				// both lnode and rnode must exist, so
				// pick the one with more entries
				(R > L)? rotateL(node, lnode, rnode):
				(L > R)? rotateR(node, lnode, rnode):
				// otherwise pick one at "random"
				(size&1) == 1? (R == ENT_MIN? merge(node, lnode, rnode):
				                              rotateL(node, lnode, rnode)):
				               (L == ENT_MIN? merge(node, lnode, rnode):
				                              rotateR(node, lnode, rnode));
				nextnode = node.selectNode(key);
				assert(nextnode != null);
			}

			parent = node;
			node = nextnode;
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation iterates over the given map's {@code entrySet},
	** adding each mapping in turn, except for when {@code this} map is empty,
	** and the input map is a non-empty {@link SortedMap}. In this case, it
	** uses the BTree bulk-loading algorithm:
	**
	** * distribute all the entries of the map across the least number of nodes
	**   possible, excluding the entries that will act as separators between
	**   these nodes
	** * repeat for the separator entries, and use them to join the nodes from
	**   the previous level appropriately to form a node at this level
	** * repeat until there are no more entries on a level, at which point use
	**   the (single) node from the previous level as the root
	**
	** (The optimisation is also used if the input map is {@code this}.)
	**
	** @param t mappings to be stored in this map
	*/
	@Override public void putAll(Map<? extends K, ? extends V> t) {
		// t == this to support restructure()
		if (t.isEmpty()) {
			return;
		} else if (t == this || isEmpty() && t instanceof SortedMap) {
			SortedMap<K, V> map = (SortedMap<K, V>)t, nextmap;
			Map<K, Node> lnodes = null, nextlnodes;

			if (!(comparator == null && map.comparator() == null || comparator.equals(map.comparator()))) {
				super.putAll(map);
				return;
			}

			while (map.size() > 0) {
				int k = minNodesFor(map.size());

				nextlnodes = new HashMap<K, Node>(k<<1);
				nextmap = new TreeMap<K, V>();

				Iterator<Map.Entry<K, V>> it = map.entrySet().iterator();
				K prevkey = null; // NULLNOTICE

				// allocate all entries into these k nodes, except for k-1 parents
				for (Integer n: Integers.allocateEvenly(map.size()-k+1, k)) {
					// put n entries into a new leaf
					Map.Entry<K, V> en = makeNode(it, n, prevkey, lnodes, nextlnodes);
					if (en != null) { nextmap.put(prevkey = en.getKey(), en.getValue()); }
				}

				lnodes = nextlnodes;
				map = nextmap;
			}

			assert(lnodes.size() == 1);
			root = lnodes.get(null);
			size = map.size();

		} else {
			super.putAll(t);
		}
	}

	/**
	** Helper method for the bulk-loading algorithm.
	**
	** @param it Iterator over the entries at the current level
	** @param n Number of entries to add to the map
	** @param prevkey Last key encountered before calling this method
	** @param lnodes Complete map of keys to their lnodes at this level
	** @param nextlnodes In-construction lnodes map for the next level
	*/
	private Map.Entry<K, V> makeNode(Iterator<Map.Entry<K, V>> it, int n, K prevkey, Map<K, Node> lnodes, Map<K, Node> nextlnodes) {
		Node node;
		if (lnodes == null) { // create leaf nodes
			node = newNode(prevkey, null, true);
			for (int i=0; i<n; ++i) {
				Map.Entry<K, V> en = it.next();
				K key = en.getKey();
				node.entries.put(key, en.getValue());
				prevkey = key;
			}
		} else {
			node = newNode(prevkey, null, false);
			for (int i=0; i<n; ++i) {
				Map.Entry<K, V> en = it.next();
				K key = en.getKey();
				node.entries.put(key, en.getValue());
				Node subnode = lnodes.get(key);
				node.rnodes.put(prevkey, subnode);
				node.lnodes.put(key, subnode);
				prevkey = key;
			}
		}

		Map.Entry<K, V> next;
		K key;
		if (it.hasNext()) {
			next = it.next();
			key = next.getKey();
		} else {
			next = null;
			key = null;
		}

		if (lnodes != null) {
			Node subnode = lnodes.get(key);
			node.rnodes.put(prevkey, subnode);
			node.lnodes.put(key, subnode);
		}
		node.rkey = key;
		nextlnodes.put(key, node);

		return next;
	}

	/**
	** Restructures the tree, distributing the entries evenly between the leaf
	** nodes. This method merely calls {@link #putAll(Map)} with {@code this}.
	*/
	public void restructure() {
		putAll(this);
	}

	// TODO maybe make this a WeakReference?
	private Set<Map.Entry<K, V>> entrySet = null;
	@Override public Set<Map.Entry<K, V>> entrySet() {
		if (entrySet == null) {
			entrySet = new AbstractSet<Map.Entry<K, V>>() {

				@Override public int size() { return BTreeMap.this.size(); }

				@Override public Iterator<Map.Entry<K, V>> iterator() {
					// URGENT - this does NOT yet throw ConcurrentModificationException
					// use a modCount counter
					return new Iterator<Map.Entry<K, V>>() {

						Stack<Node> nodestack = new Stack<Node>();
						Stack<Iterator<Map.Entry<K, V>>> itstack = new Stack<Iterator<Map.Entry<K, V>>>();

						Node cnode = BTreeMap.this.root;
						Iterator<Map.Entry<K, V>> centit = cnode.entries.entrySet().iterator();

						K lastkey = null;
						boolean removeok = false;

						// DEBUG ONLY, remove when unneeded
						/*public String toString() {
							StringBuilder s = new StringBuilder();
							for (Node n: nodestack) {
								s.append(n.getRange()).append(", ");
							}
							return "nodestack: [" + s + "]; cnode: " + cnode.getRange() + "; lastkey: " + lastkey;
						}*/

						/*@Override**/ public boolean hasNext() {
							// TODO ideally iterate in the reverse order
							for (Iterator<Map.Entry<K, V>> it: itstack) {
								if (it.hasNext()) { return true; }
							}
							if (centit.hasNext()) { return true; }
							if (!cnode.isLeaf()) { return true; }
							return false;
						}

						/*@Override**/ public Map.Entry<K, V> next() {
							if (cnode.isLeaf()) {
								while (!centit.hasNext()) {
									if (nodestack.empty()) {
										assert(itstack.empty());
										throw new NoSuchElementException();
									}
									cnode = nodestack.pop();
									centit = itstack.pop();
								}
							} else {
								while (!cnode.isLeaf()) {
									// NULLNOTICE lastkey initialised to null, so this will get the right node
									// even at the smaller edge of the map
									Node testnode = cnode.rnodes.get(lastkey);
									testnode.isLeaf(); // trigger DataNotLoadedException if this is a GhostNode
									// node OK, proceed
									nodestack.push(cnode);
									itstack.push(centit);
									cnode = testnode;
									centit = cnode.entries.entrySet().iterator();
								}
							}
							Map.Entry<K, V> next = centit.next(); lastkey = next.getKey();
							removeok = true;
							return next;
						}

						/*@Override**/ public void remove() {
							if (!removeok) {
								throw new IllegalStateException("Iteration has not yet begun, or the element has already been removed.");
							}
							// OPTIMISE this could probably be a *lot* more efficient...
							BTreeMap.this.remove(lastkey);

							// we need to find our position in the tree again, since there may have
							// been structural modifications to it.
							// OPTIMISE have a "structual modifications counter" that is incremented by
							// split, merge, rotateL, rotateR, and do the below only if it changes
							nodestack.clear();
							itstack.clear();
							cnode = BTreeMap.this.root;
							centit = cnode.entries.entrySet().iterator();

							K stopkey = null;
							while(!cnode.isLeaf()) {
								stopkey = findstopkey(stopkey);
								nodestack.push(cnode);
								itstack.push(centit);
								// NULLNOTICE stopkey initialised to null, so this will get the right node
								// even at the smaller edge of the map
								cnode = cnode.rnodes.get(stopkey);
								centit = cnode.entries.entrySet().iterator();
							}

							lastkey = findstopkey(stopkey);
							removeok = false;
						}

						private K findstopkey(K stopkey) {
							SortedMap<K, V> headmap = cnode.entries.headMap(lastkey);
							if (!headmap.isEmpty()) {
								stopkey = headmap.lastKey();
								while (compare(centit.next().getKey(), stopkey) < 0);
							}
							return stopkey;
						}

					};
				}

				@Override public void clear() {
					BTreeMap.this.clear();
				}

				@Override public boolean contains(Object o) {
					if (!(o instanceof Map.Entry)) { return false; }
					Map.Entry e = (Map.Entry)o;
					Object value = BTreeMap.this.get(e.getKey());
					return value != null && value.equals(e.getValue());
				}

				@Override public boolean remove(Object o) {
					if (contains(o)) {
						Map.Entry e = (Map.Entry)o;
						BTreeMap.this.remove(e.getKey());
						return true;
					}
					return false;
				}

			};
		}
		return entrySet;
	}

	/* provided by AbstractMap
	** OPTIMISE - the default clear() method is inefficient
	@Override public Set<K> keySet() {
		throw new UnsupportedOperationException("not implemented");
	}*/

	/* provided by AbstractMap
	** OPTIMISE - the default clear() method is inefficient
	@Override public Collection<V> values() {
		throw new UnsupportedOperationException("not implemented");
	}*/

	/*========================================================================
	  public interface SortedMap
	 ========================================================================*/

	/*@Override**/ public Comparator<? super K> comparator() {
		return comparator;
	}

	/*@Override**/ public K firstKey() {
		Node node = root;
		for (;;) {
			K fkey = node.entries.firstKey();
			if (node.isLeaf()) { return fkey; }
			node = node.lnodes.get(fkey);
		}
	}

	/*@Override**/ public K lastKey() {
		Node node = root;
		for (;;) {
			K lkey = node.entries.lastKey();
			if (node.isLeaf()) { return lkey; }
			node = node.rnodes.get(lkey);
		}
	}

	/**
	** {@inheritDoc}
	**
	** Not yet implemented - throws {@link UnsupportedOperationException}
	*/
	/*@Override**/ public SortedMap<K, V> headMap(K rkey) {
		throw new UnsupportedOperationException("not implemented");
	}

	/**
	** {@inheritDoc}
	**
	** Not yet implemented - throws {@link UnsupportedOperationException}
	*/
	/*@Override**/ public SortedMap<K, V> tailMap(K lkey) {
		throw new UnsupportedOperationException("not implemented");
	}

	/**
	** {@inheritDoc}
	**
	** Not yet implemented - throws {@link UnsupportedOperationException}
	*/
	/*@Override**/ public SortedMap<K, V> subMap(K lkey, K rkey) {
		throw new UnsupportedOperationException("not implemented");
	}

}
