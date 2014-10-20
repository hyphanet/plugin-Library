/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */


package plugins.Library.util;

import java.util.Set;
import java.util.Iterator;

/**
 * Trie-like map or map-like structure (eg. multimap) with each node mapping
 * keys that match a given prefix. Each node in the tree will admit a certain
 * maximum number of mappings before it starts creating subtrees.
 *
 * @deprecated
 * @author infinity0
 */
abstract public class PrefixTree<K extends PrefixTree.PrefixKey, V> {

    /**
     * The prefix that all keys in this tree must match.
     */
    final protected K prefix;

    /**
     * The length of the prefix for this tree.
     */
    final protected int preflen;

    /**
     * Array holding the child trees. There is one cell in the array for each
     * symbol in the alphabet of the key. For all i: {@link #child}[i] != null,
     * or {@link #child}[i].{@link #lastIndex()} == i.
     */
    final protected PrefixTree<K, V>[] child;

    /**
     * The size of the {@link #child} array.
     */
    final public transient int subtreesMax;

    /**
     * Maximum size of ({@link #sizeLocal()} + {@link #child}) before we start
     * to create subtrees.
     */
    final public int capacityLocal;

    /**
     * Number of subtrees. At all times, this should equal the number of
     * non-null members of {@link #child}.
     */
    protected int subtrees = 0;

    /**
     * Number of elements contained in the tree. At all times, it should equal
     * the sum of the size of the subtrees, plus the {@link #sizeLocal()}.
     */
    protected int size = 0;

    /**
     * Counts the number of mappings in each prefix group, meaning the set of
     * all mappings whose keys give the same key.get(preflen). At all times,
     * the sum the array elements should equal the size field. Additionally,
     * for all i: ({@link #child}[i] != null) implies ({@link #sizePrefix}[i]
     * == {@link #child}[i].size()); and {@link #sizeLocal()} == sum{ {@link
     * #sizePrefix}[j] : {@link #child}[j] == null }
     */
    protected int sizePrefix[];

    /**
     * Cache for {@link #smallestChild()}. At all times, this should either be
     * -1, or be the index of the subtree with the smallest size. If -1, then
     * either there are no subtrees (check {@link #subtrees} == 0) or the cache
     * has been invalidated.
     */
    protected transient int smch_ = -1;

    protected PrefixTree(K p, int len, int maxsz, PrefixTree<K, V>[] chd) {
        if (chd != null) {

            // if the child array is null, we assume the caller knows what they're
            // doing... this is needed for SkeletonPrefixTreeMap.DummyChild
            if (chd.length != p.symbols()) {
                throw new IllegalArgumentException(
                    "The child array must be able to exactly hold all its potential children," +
                    " of which there are " + p.symbols());
            }

            for (PrefixTree<K, V> c : chd) {
                if (c != null) {
                    throw new IllegalArgumentException("Child array to attach must be empty.");
                }
            }
        }

        if (maxsz < p.symbols()) {
            throw new IllegalArgumentException(
                "This tree must be able to hold all its potential children, of which there are " +
                p.symbols());
        }

        if (len > p.size()) {
            throw new IllegalArgumentException("The key is shorter than the length specified.");
        }

        if (len < 0) {
            throw new IllegalArgumentException("Length cannot be negative.");
        }

        prefix = p;
        prefix.clearFrom(len);
        preflen       = len;
        child         = chd;
        sizePrefix    = (child == null) ? null : new int[child.length];
        subtreesMax   = p.symbols();
        capacityLocal = maxsz;
    }

    /**
     * Return the space left in the local map.
     */
    public int sizeLeft() {
        return capacityLocal - (sizeLocal() + subtrees);
    }

    /**
     * Returns the prefix in string form.
     */
    public String prefixString() {

        // OPTIMISE maybe have prefixString save into a cache? or too bothersome...
        String ps = prefix.toString();

        return ps.substring(0, ps.length() * preflen / prefix.size());
    }

    /**
     * Returns the value at the last significant index of the prefix
     */
    public int lastIndex() {
        return prefix.get(preflen - 1);
    }

    /**
     * Returns the index of the smallest child.
     */
    protected int smallestChild() {
        if ((smch_ < 0) && (subtrees > 0)) {
            for (int i = 0; i < subtreesMax; ++i) {
                if ((child[i] != null) && ((smch_ < 0) || (sizePrefix[i] < sizePrefix[smch_]))) {
                    smch_ = i;
                }
            }
        }

        return smch_;
    }

    /**
     * Build a subtree from the largest subset of the local map consisting of
     * (keys that share a common prefix of length one greater than that of this
     * tree).
     *
     * For efficiency, this method *assumes* that such a set exists and is
     * non-empty; it is up to the calling code to make sure this is true.
     *
     * TODO URGENT consider, in the case of Multimap, when the size of local
     * map goes above capacityLocal and cannot be converted into a subtree (eg.
     * when there are capacityLocal+1 values for the same key).
     *
     * @return The index of the new subtree.
     */
    private int bindSubTree() {
        assert(sizeLocal() > 0);
        assert(subtrees < subtreesMax);

        int mcount = -1;
        int msym   = 0;

        for (int i = 0; i < sizePrefix.length; ++i) {
            if (child[i] != null) {
                continue;
            }

            if (sizePrefix[i] > mcount) {
                mcount = sizePrefix[i];
                msym   = i;
            }
        }

        Object[] mkeys = new Object[mcount];

        // TODO make this use Sorted methods after this class implements Sorted
        int i = 0;

        for (K ikey : keySetLocal()) {
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

        // assert(i == mkeys.length);
        assert(i <= mkeys.length);    // Multimaps should be <=, Maps should be ==
        assert(child[msym] == null);
        child[msym] = makeSubTree(msym);
        ++subtrees;

        for (i = 0; i < mkeys.length; ++i) {
            transferLocalToSubtree(msym, (K) mkeys[i]);
        }

        // update cache
        if ((subtrees == 1) || ((smch_ > -1) && (sizePrefix[msym] < sizePrefix[smch_]))) {
            smch_ = msym;
        }

        return msym;
    }

    /**
     * Put all entries of a subtree into this node, if there is enough space.
     * (If there is enough space, then this implies that the subtree itself has
     * no child trees, due to the size constraints in the constructor.)
     *
     * @param ch Subtree to free
     * @return Whether there was enough space, and the operation completed.
     */
    private boolean freeSubTree(int ch) {
        if (sizePrefix[ch] <= sizeLeft() + 1) {

            // cache invalidated due to child removal
            if (ch == smch_) {
                smch_ = -1;
            }

            transferSubtreeToLocal(child[ch]);
            child[ch] = null;
            --subtrees;

            return true;
        }

        return false;
    }

    /**
     * After a put operation, move some entries into new subtrees, with big
     * subtrees taking priority, until there is enough space to fit all the
     * remaining entries. That is, ensure that there exists sz such that:
     * - {@link #sizePrefix}[{@link #smallestChild()}] == sz
     * - sz > {@link #sizeLeft()}
     * - and that for all i:
     *   - ({@link #child}[i] == null) implies {@link #sizePrefix}[i] <= sz
     *   - ({@link #child}[i] != null) implies {@link #sizePrefix}[i] >= sz
     *
     * @param i Subgroup of element that was inserted
     */
    protected void reshuffleAfterPut(int i) {
        ++sizePrefix[i];
        ++size;

        if (child[i] != null) {

            // cache possibly invalidated due to put operation on child
            if (i == smch_) {
                smch_ = -1;
            }
        } else {
            if (sizeLeft() < 0) {
                bindSubTree();

                // clearly, a maximum of one subtree can be freed here
                freeSubTree(smallestChild());    // smallestChild() exists due to bindSubTree
            } else {
                int sm = smallestChild();

                if ((sm > -1) && (sizePrefix[sm] < sizePrefix[i])) {

                    // Let sz be the size of the smallest child, which is (*) greater or
                    // equal to the size of the largest non-child subgroup. Then:
                    // - before the put, the maximum space left in the data structure was sz-1
                    // since we have sz > sizeLeft()
                    // - before the put, the maximum size of subgroup i was sz, due to (*)
                    // - so the maximum space left after binding this subgroup is 2sz-1
                    // (any elements added due to the put are irrelevant to this)
                    // - now, the minimum size of two subtrees is 2sz > 2sz-1
                    // - so a maximum of one subtree can be freed after binding the subgroup
                    int j = bindSubTree();

                    assert(i == j);
                    freeSubTree(sm);
                }
            }
        }
    }

    /**
     * Reshuffle after a put operation of many elements, keeping the same
     * constraints. The elements must belong to the same subgroup.
     *
     * @param i Subgroup of elements
     * @param n Number of elements
     */
    protected void reshuffleAfterPut(int i, int n) {

        // logic of reshuffleAfterPut is independent of how many elements are
        // actually added during the put operation
        sizePrefix[i] += (n - 1);
        size          += (n - 1);
        reshuffleAfterPut(i);
    }

    /**
     * After a remove operation, merge some subtrees into this node, with small
     * subtrees taking priority, until there is no more space to fit any more
     * subtrees. That is, ensure that there exists sz such that:
     * - {@link #sizePrefix}[{@link #smallestChild()}] == sz
     * - sz > {@link #sizeLeft()}
     * - and that for all i:
     *   - ({@link #child}[i] == null) implies {@link #sizePrefix}[i] <= sz
     *   - ({@link #child}[i] != null) implies {@link #sizePrefix}[i] >= sz
     *
     * @param i Subgroup of element that was removed
     */
    protected void reshuffleAfterRemove(int i) {
        --sizePrefix[i];
        --size;

        if (child[i] != null) {
            if ((smch_ < 0) || (i == smch_)) {
                freeSubTree(smallestChild());    // smallestChild() exists since child[i] != null
            } else if (sizePrefix[i] < sizePrefix[smch_]) {

                // we potentially have a new (real) smallestChild, but wait and see...
                if ( !freeSubTree(i)) {

                    // if the tree wasn't freed then we have a new smallestChild
                    smch_ = i;
                }

                // else, if the tree was freed, then freeSubTree() would not have reset
                // smch_, since it still pointed to the old value (which was
                // incorrect before the method call, but now correct, so nothing needs to
                // be done).
            }

            // else, the smallestChild hasn't changed, so no more trees can be freed.
        } else {
            int t = smallestChild();

            if (t > -1) {
                freeSubTree(t);
            }
        }
    }

    /**
     * Reshuffle after a remove operation of many elements, keeping the same
     * constraints. The elements must belong to the same subgroup.
     *
     * @param i Subgroup of elements
     * @param n Number of elements
     */
    protected void reshuffleAfterRemove(int i, int n) {

        // Let sz be the size of the smallest child, which is (*) greater or equal
        // to the size of the largest non-child subgroup. Then, in the case where
        // child[i] == null:
        // - the maximum space left in the data structure is sz-1, since we have
        // sz > sizeLeft()
        // - the maximum space freed by a remove operation is sz, due to (*)
        // - so the maximum space left after a remove operation is 2sz-1
        // - now, the minimum size of two subtrees is 2sz > 2sz-1
        // - so a maximum of one subtree can be freed after a remove operation
        // and in the case where child[i] != null, the same logic applies as for
        // one-element removal
        sizePrefix[i] -= (n - 1);
        size          -= (n - 1);
        reshuffleAfterRemove(i);
    }

    /**
     * Make a subtree with the appropriate prefix. For effeciency, this method
     * assumes that {@link #child}[i] does not already have a tree attached; it
     * is up to the calling code to ensure that this holds.
     *
     * @param msym The last symbol in the prefix for the subtree.
     * @return The subtree.
     */
    abstract protected PrefixTree<K, V> makeSubTree(int msym);

    /**
     * Transfer the value for a key to the appropriate subtree. For efficiency,
     * this method assumes that {@link #child}[i] already exists and that its
     * prefix matches the key; it is up to the calling code to ensure that this
     * holds.
     */
    abstract protected void transferLocalToSubtree(int i, K key);

    /**
     * Transfer all mappings of a subtree to the local map. For efficiency, the
     * subtree is assumed to be an actual direct subtree of this map, ie. the
     * same object as {@link #child}[i] for some i; it is up to the calling
     * code to ensure that this holds.
     */
    abstract protected void transferSubtreeToLocal(PrefixTree<K, V> ch);

    /**
     * Select the object which handles keys with i as the next prefix element.
     * Should return {@link #child}[i] if and only if {@link #child}[i] != null
     * and a pointer to the local map otherwise.
     */
    abstract protected Object selectNode(int i);

    /**
     * Returns the local map.
     */
    abstract protected Object getLocalMap();

    /**
     * Clears the local map.
     */
    abstract protected void clearLocal();

    /**
     * Returns the set of keys of the local map.
     */
    abstract protected Set<K> keySetLocal();

    /**
     * Returns the size of the local map.
     */
    abstract public int sizeLocal();

    /**
     * TODO Returns the set of keys of the whole node.
     */
    abstract protected Set<K> keySet();

    /*
     * ========================================================================
     * public interface Map, Multimap
     * ========================================================================
     */
    public void clear() {
        size = 0;
        clearLocal();
        smch_ = -1;

        for (int i = 0; i < subtreesMax; ++i) {
            child[i]      = null;
            sizePrefix[i] = 0;
        }

        subtrees = 0;
    }

    public boolean isEmpty() {
        return (size == 0);
    }

    public int size() {
        return size;
    }

    /*
     * ========================================================================
     * public class Object
     * ========================================================================
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if ( !(o instanceof PrefixTree)) {
            return false;
        }

        PrefixTree tr = (PrefixTree) o;

        if (size() != tr.size()) {
            return false;
        }

        // TODO research... java API says about Map.equals():
        // "Returns true if the given object is also a map and the two maps
        // represent the same mappings."
        // (Nothing further is said for SortedMap.equals().)
        // However, for a PrefixTree, this can occur even if the first three
        // clauses of the below or-expression are true.
        if ( !prefix.equals(tr.prefix) || (preflen != tr.preflen) ||
                (capacityLocal != tr.capacityLocal) || !getLocalMap().equals(tr.getLocalMap())) {
            return false;
        }

        for (int i = 0; i < subtreesMax; ++i) {
            if (((child[i] == null) && (tr.child[i] == null)) || !child[i].equals(tr.child[i])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        int sum = getLocalMap().hashCode();

        for (PrefixTree<K, V> ch : child) {
            if (ch == null) {
                continue;
            }

            sum += ch.hashCode();
        }

        return sum;
    }

    /**
     * Defines an interface that provides prefix-related operations, such as
     * returning the next component of a key,
     *
     * @author infinity0
     */
    public interface PrefixKey<K extends PrefixKey<K>> extends Cloneable, Comparable<K> {
        public PrefixKey<K> clone();

        /**
         * Returns the number of possible symbols at each cell of the key. This
         * should return the same value for any instance.
         */
        public int symbols();

        /**
         * Returns the size of the key. This should return the same value for
         * any instance.
         */
        public int size();

        /**
         * Gets one cell of the key.
         */
        public int get(int i);

        /**
         * Sets one cell of the key.
         */
        public void set(int i, int v);

        /**
         * Clears one cell of the key.
         */
        public void clear(int i);

        /**
         * Returns a new key with a new value set for one of the cells.
         */
        public K spawn(int i, int v);

        /**
         * Clears all cells from a given index.
         */
        public void clearFrom(int len);

        /**
         * Whether two keys have matching prefixes.
         *
         * @param p The key to match against
         * @param len Length of prefix to match
         */
        public boolean match(K p, int len);
    }


    /**
     * Provides implementations of various higher-level functionalities of
     * PrefixKey in terms of lower-level ones.
     *
     * @author infinity0
     */
    abstract public static class AbstractPrefixKey<K extends AbstractPrefixKey<K>>
            implements PrefixKey<K> {
        @Override
        abstract public AbstractPrefixKey<K> clone();

        public K spawn(int i, int v) {
            K p = (K) clone();

            p.set(i, v);

            return p;
        }

        public void clearFrom(int len) {
            for (int i = len; i < size(); ++i) {
                clear(i);
            }
        }

        public boolean match(K p, int len) {
            for (int i = 0; i < len; ++i) {
                if (get(i) != p.get(i)) {
                    return false;
                }
            }

            return true;
        }

        public int compareTo(K p) {
            for (int i = 0; i < size(); ++i) {
                int x = get(i);
                int y = p.get(i);

                if (x != y) {
                    return (x > y) ? 1 : -1;
                }
            }

            return 0;
        }
    }


    /**
     * TODO Provides an iterator over the PrefixTree. incomplete
     */
    abstract public class PrefixTreeKeyIterator implements Iterator<K> {
        final Iterator<K> iterLocal;
        Iterator<K>       iterChild;
        protected int     index     = 0;
        protected K       nextLocal = null;

        protected PrefixTreeKeyIterator() {
            iterLocal = keySetLocal().iterator();
            while ((index < subtreesMax) && (child[index++] == null));

            if (index < subtreesMax) {
                iterChild = child[index].keySet().iterator();
            }

            if (iterLocal.hasNext()) {
                nextLocal = iterLocal.next();
            }
        }

        public boolean hasNext() {
            return (nextLocal != null) || (iterChild != null);
        }

        public K next() {
            while ((index < subtreesMax) || (nextLocal != null)) {
                if ((nextLocal != null) && (nextLocal.get(preflen) < index)) {
                    K k = nextLocal;

                    nextLocal = (iterLocal.hasNext()) ? iterLocal.next() : null;

                    return k;
                } else if (iterChild.hasNext()) {
                    return iterChild.next();
                } else {
                    do {
                        ++index;
                    } while ((index < subtreesMax) && (child[index] == null));

                    iterChild = (index < subtreesMax) ? child[index].keySet().iterator() : null;

                    continue;
                }
            }

            throw new java.util.NoSuchElementException();
        }

        public void remove() {
            throw new UnsupportedOperationException("Not implemented.");
        }
    }
}
