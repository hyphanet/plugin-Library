/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import java.util.Set;

/**
** Trie-like map or map-like structure (eg. multimap) with each node mapping
** keys that match a given prefix. Each node in the tree will admit a certain
** maximum number of mappings before it starts creating subtrees.
**
** @author infinity0
*/
abstract public class PrefixTree<K extends PrefixTree.PrefixKey, V> {

	/**
	** The prefix that all keys in this tree must match.
	*/
	final K prefix;

	/**
	** The length of the prefix for this tree.
	*/
	final int preflen;

	/**
	** Pointer to the parent tree.
	*/
	final PrefixTree<K, V> parent;

	/**
	** Array holding the child trees. There is one cell in the array for each
	** symbol in the alphabet of the key. For all i: child[i] != null, or
	** child[i].lastIndex() == i.
	*/
	final PrefixTree<K, V>[] child;

	/**
	** The size of the child array.
	*/
	final int subtreesMax;

	/**
	** Maximum size of (localmap + child) before we start to create subtrees.
	*/
	final int sizeMax;

	/**
	** Number of subtrees. At all times, this should equal the number of
	** non-null members of the child array.
	*/
	protected int subtrees = 0;

	/**
	** Number of elements contained in the tree. At all times, it should equal
	** the sum of the size of the subtrees, plus the size of the localmap.
	*/
	protected int size = 0;

	/**
	** Counts the number of mappings in each prefix group, meaning the set of
	** all mappings whose keys give the same key.get(preflen). At all times,
	** the sum the array elements should equal the size field. Additionally,
	** for all i: (child[i] != null) implies (sizePrefix[i] == child[i].size())
	** and sizeLocal() == sum{ sizePrefix[j] : child[j] == null }
	*/
	final protected int sizePrefix[];

	/**
	** Cache for the smallestChild. At all times, this should either be null,
	** or point to the subtree with the smallest size. If null, then either
	** there are no subtrees (check subtrees == 0) or the cache has been
	** invalidated.
	*/
	transient protected PrefixTree<K, V> smallestChild_ = null;

	protected PrefixTree(K p, int len, int maxsz, PrefixTree<K, V>[] chd, PrefixTree<K, V> par) {
		if (chd.length != p.symbols()) {
			throw new IllegalArgumentException("The child array must be able to exactly hold all its potential children, of which there are " + p.symbols());
		}
		if (maxsz < p.symbols()) {
			throw new IllegalArgumentException("This tree must be able to hold all its potential children, of which there are " + p.symbols());
		}
		if (len > p.size()) {
			throw new IllegalArgumentException("The key is shorter than the length specified.");
		}
		if (len < 0) {
			throw new IllegalArgumentException("Length cannot be negative.");
		}
		if ((par == null && len != 0) || (par != null && (par.preflen != len-1 || !par.prefix.match(p, len-1)))) {
			throw new IllegalArgumentException("Not a valid parent for this node.");
		}

		for (PrefixTree<K, V> c: chd) {
			if (c != null) { ++subtrees; }
		}

		prefix = p;
		prefix.clearFrom(len);
		preflen = len;

		child = chd;
		parent = par;

		subtreesMax = p.symbols();
		sizePrefix = new int[child.length];
		sizeMax = maxsz;
	}

	/**
	** Return the size of the data structure.
	*/
	public int size() {
		return size;
	}

	/**
	** Returns the prefix in string form.
	*/
	public String prefixString() {
		String ps = prefix.toString();
		return ps.substring(0, ps.length() * preflen / prefix.size());
	}

	/**
	** Returns the value at the last significant index of the prefix
	*/
	public int lastIndex() {
		return prefix.get(preflen-1);
	}

	/**
	** Returns the smallest child.
	*/
	protected PrefixTree<K, V> smallestChild() {
		if (smallestChild_ == null && subtrees > 0) {
			for (PrefixTree<K, V> ch: child) {
				if (ch != null && (smallestChild_ == null || ch.size() < smallestChild_.size())) {
					smallestChild_ = ch;
				}
			}
		}
		return smallestChild_;
	}

	/**
	** Build a subtree from the largest subset of the local map consisting of
	** (keys that share a common prefix of length one greater than that of this
	** tree).
	**
	** For efficiency, this method *assumes* that such a set exists and is
	** non-empty; it is up to the calling code to make sure this is true.
	**
	** @return The index of the new subtree.
	*/
	protected int bindSubTree() {
		assert(sizeLocal() > 0);
		assert(subtrees < subtreesMax);

		int mcount = -1;
		int msym = 0;
		for (int i=0; i<sizePrefix.length; ++i) {
			if (child[i] != null) { continue; }
			if (sizePrefix[i] > mcount) {
				mcount = sizePrefix[i];
				msym = i;
			}
		}

		Object[] mkeys = new Object[mcount];

		// TODO make this use Sorted methods after this class implements Sorted
		int i = 0;
		for (K ikey: keySetLocal()) {
			int isym = ikey.get(preflen);
			if (isym < msym) {
				continue;
			} else if (isym == msym) {
				mkeys[i] = ikey;
				++i;
			} else if (isym > msym) {
				break;
			}
		}
		assert(i == mkeys.length);
		assert(child[msym] == null);

		child[msym] = makeSubTree(msym);
		++subtrees;

		for (i=0; i<mkeys.length; ++i) {
			transferLocalToSubtree(msym, (K)mkeys[i]);
		}

		// update cache
		if (subtrees == 1 || smallestChild_ != null && child[msym].size() < smallestChild_.size()) {
			smallestChild_ = child[msym];
		}

		return msym;
	}

	/**
	** Put all entries of a subtree into this node, if there is enough space.
	** (If there is enough space, then this implies that the subtree itself has
	** no child trees, due to the size constraints in the constructor.)
	**
	** @param ch Subtree to free
	** @return Whether there was enough space, and the operation completed.
	*/
	private boolean freeSubTree(PrefixTree<K, V> ch) {
		if (sizeLocal() + subtrees + ch.size() - 1 <= sizeMax) {

			// cache invalidated due to child removal
			if (ch == smallestChild_) { smallestChild_ = null; }

			transferSubtreeToLocal(ch);
			assert(ch == child[ch.lastIndex()]);
			child[ch.lastIndex()] = null;
			--subtrees;
			return true;
		}
		return false;
	}

	/**
	** After a put operation, move some entries into new subtrees, with big
	** subtrees taking priority, until there is enough space to fit all the
	** remaining entries. That is, ensure that there exists sz such that:
	** smallestChild.size() == sz and sizeLocal() + subtrees + sz > sizeMax
	** and that for all i:
	** (child[i] == null) implies sizePrefix[i] <= sz
	** (child[i] != null) implies sizePrefix[i] >= sz
	*/
	protected void reshuffleAfterPut(int i) {
		if (child[i] != null) {
			// cache possibly invalidated due to put operation on child
			if (child[i] == smallestChild_) { smallestChild_ = null; }

		} else {

			if (sizeLocal() + subtrees > sizeMax) {
				bindSubTree();
				// smallestChild() is not null due to bindSubTree
				freeSubTree(smallestChild());

			} else {
				PrefixTree<K, V> sm = smallestChild();
				if (sm != null && sm.size() < sizePrefix[i]) {
					// TODO: make a bindSubTree(i) method
					int j = bindSubTree();
					assert(i == j);
					freeSubTree(sm);
				}
			}
		}
	}

	/**
	** After a remove operation, merge some subtrees into this node, with small
	** subtrees taking priority, until there is no more space to fit any more
	** subtrees. That is, ensure that there exists sz such that:
	** smallestChild.size() == sz and sizeLocal() + subtrees + sz > sizeMax
	** and that for all i:
	** (child[i] == null) implies sizePrefix[i] <= sz
	** (child[i] != null) implies sizePrefix[i] >= sz
	*/
	protected void reshuffleAfterRemove(int i) {
		if (child[i] != null) {

			if (smallestChild_ == null || child[i] == smallestChild_) {
				// smallestChild() is not null due to previous if block
				freeSubTree(smallestChild());

			} else if (sizePrefix[i] < smallestChild_.size()) {
				// we potentially have a new (real) smallestChild, but wait and see...

				if (!freeSubTree(child[i])) {
					// if the tree wasn't freed then we have a new smallestChild
					smallestChild_ = child[i];
				}
				// on the other hand, if the tree was freed, then freeSubTree()
				// would not have reset smallestChild_, since it still pointed
				// to the old value (which was incorrect before the method call
				// but now correct, so nothing needs to be done).
			}
			// else, the smallestChild hasn't changed, so no more trees can be
			// freed.

		} else {
			PrefixTree<K, V> t = smallestChild();
			if (t != null) { freeSubTree(t); }
		}
	}

	/**
	** Make a subtree with the appropriate prefix.
	**
	** @param msym The last symbol in the prefix for the subtree.
	** @return The subtree.
	*/
	abstract protected PrefixTree<K, V> makeSubTree(int msym);

	/**
	** Returns a set of keys from the local map.
	*/
	abstract protected Set<K> keySetLocal();

	/**
	** Returns the size of the local map.
	*/
	abstract protected int sizeLocal();

	/**
	** Transfer the value for a key to the appropriate subtree. For efficiency,
	** this method assumes that child[i] already exists and that its prefix
	** matches key; it is up to the calling code to ensure that this holds.
	*/
	abstract protected void transferLocalToSubtree(int i, K key);

	/**
	** Transfer all mappings of a subtree to the local map. For efficiency, the
	** subtree is assumed to be an actual direct subtree of this map; it is up
	** to the calling code to ensure that this holds.
	*/
	abstract protected void transferSubtreeToLocal(PrefixTree<K, V> ch);

	/**
	** Select the object which handles keys with i as the next prefix element.
	** Should return child[i] if and only if child[i] != null, and a pointer
	** to the local map otherwise.
	*/
	abstract protected Object selectNode(int i);


	/**
	** Defines an interface that provides prefix-related operations, such as
	** returning the next component of a key,
	**
	** @author infinity0
	*/
	public interface PrefixKey extends Cloneable, Comparable {

		public Object clone();

		/**
		** Returns the number of possible symbols at each cell of the key.
		*/
		public int symbols();

		/**
		** Returns the size of the key.
		*/
		public int size();

		/**
		** Gets one cell of the key.
		*/
		public int get(int i);

		/**
		** Sets one cell of the key.
		*/
		public void set(int i, int v);

		/**
		** Clears one cell of the key.
		*/
		public void clear(int i);

		/**
		** Returns a new key with a new value set for one of the cells.
		*/
		public PrefixKey spawn(int i, int v);

		/**
		** Clears all cells from a given index.
		*/
		public void clearFrom(int len);

		/**
		** Whether two keys have matching prefixes.
		**
		** @param p The key to match against
		** @param len Length of prefix to match
		*/
		public boolean match(PrefixKey p, int len);

	}

	/**
	** Provides implementations of various higher-level functionalities of
	** PrefixKey in terms of lower-level ones.
	**
	** @author infinity0
	*/
	abstract public static class AbstractPrefixKey implements PrefixKey {

		abstract public Object clone();

		public PrefixKey spawn(int i, int v) {
			PrefixKey p = (PrefixKey)clone();
			p.set(i, v);
			return p;
		}

		public void clearFrom(int len) {
			for (int i=len; i<size(); ++i) { clear(i); }
		}

		public boolean match(PrefixKey p, int len) {
			for (int i=0; i<len; ++i) {
				if (get(i) != p.get(i)) { return false; }
			}
			return true;
		}

		public int compareTo(Object o) throws ClassCastException {
			PrefixKey p = (PrefixKey) o;
			int a = size();
			int b = p.size();
			int c = (a < b)? a: b;
			for (int i=0; i<c; ++i) {
				int x = get(i);
				int y = p.get(i);
				if (x != y) { return x-y; }
			}
			return a-b;
		}

	}

}
