/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */


package plugins.Library.util.event;

import java.util.Collections;
import java.util.Iterator;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

/**
 * A {@link Sweeper} which uses a given {@link Collection} to keep track of the
 * objects added. It also allows iteration through these objects, and supports
 * calls to {@link Iterator#remove()}.
 *
 * @author infinity0
 */
public class TrackingSweeper<T, C extends Collection<T>> extends AbstractSweeper<T>
        implements Iterable<T> {
    final protected C objs;
    final protected C objs_i;

    /**
     * Construct a new sweeper.
     *
     * If {@code coll_immute} is {@code null}, then {@code coll} will be used
     * in its place. This has a slight performance advantage over a wrapping
     * "immutable" view; but only use it when trusted code is going to access
     * the sweeper.
     *
     * @param autostart Whether to construct the sweeper already open
     * @param onceonly Whether the sweeper can only be opened once
     * @param coll A {@link Collection} to hold the items in
     * @param coll_immute An immutable view of the same collection
     */
    public TrackingSweeper(boolean autostart, boolean onceonly, C coll, C coll_immute) {
        super(autostart, onceonly);

        if ( !coll.isEmpty()) {
            throw new IllegalArgumentException(
                "TrackingSweeper: cannot use a non-empty collection");
        }

        objs   = coll;
        objs_i = (coll_immute == null) ? coll : coll_immute;
    }

    /**
     * Construct a new sweeper, automatically inferring an immutable view using
     * {@link #inferImmutable(Collection)}
     *
     * @param autostart Whether to construct the sweeper already open
     * @param coll A {@link Collection} to hold the items in
     */
    public TrackingSweeper(boolean autostart, boolean onceonly, C coll) {
        this(autostart, onceonly, coll, inferImmutable(coll));
    }

    /**
     * Attempts to infer an immutable view of the given collection, using the
     * available static creators given in {@link Collections}.
     *
     * Note that this method will always return an immutable {@link Collection}
     * if the input is not a {@link SortedSet}, {@link Set}, or {@link List};
     * this may or may not be what you want.
     */
    public static <T, C extends Collection<T>> C inferImmutable(C coll) {
        if (coll instanceof SortedSet) {
            return (C) Collections.unmodifiableSortedSet((SortedSet<T>) coll);
        } else if (coll instanceof Set) {
            return (C) Collections.unmodifiableSet((Set<T>) coll);
        } else if (coll instanceof List) {
            return (C) Collections.unmodifiableList((List<T>) coll);
        } else {
            return (C) Collections.unmodifiableCollection(coll);
        }
    }

    public C view() {
        return objs_i;
    }

    /**
    ** {@inheritDoc}
    */
    @Override
    protected boolean add(T object) {
        return objs.add(object);
    }

    /**
    ** {@inheritDoc}
    */
    @Override
    protected boolean remove(T object) {
        return objs.remove(object);
    }

    /**
    ** {@inheritDoc}
    */
    /* @Override* */
    public int size() {
        return objs.size();
    }

    /* @Override* */
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            final Iterator<T> it = objs.iterator();
            /* @Override* */
            public boolean hasNext() {
                return it.hasNext();
            }
            /* @Override* */
            public T next() {
                return it.next();
            }
            /* @Override* */
            public void remove() {
                releaseFrom(it);
            }
        };
    }
}
