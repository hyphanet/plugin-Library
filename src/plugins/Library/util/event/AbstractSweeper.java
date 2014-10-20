/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */


package plugins.Library.util.event;

/**
 * A partial implementation of {@link Sweeper}, defining some high-level
 * methods in terms of lower ones.
 *
 * This implementation is '''not''' thread-safe.
 *
 * @author infinity0
 */
abstract public class AbstractSweeper<T> implements Sweeper<T> {
    protected State      state;
    final public boolean once;

    /**
     * Construct a new sweeper.
     *
     * @param autostart Whether to construct the sweeper already open
     * @param onceonly Whether the sweeper can only be opened once
     */
    protected AbstractSweeper(boolean autostart, boolean onceonly) {
        state = (autostart) ? State.OPEN : State.NEW;
        once  = onceonly;
    }

    /**
     * Add an object to the underlying collection. Implementations need not
     * check for a valid state; this is done by {@link #acquire(Object)}.
     *
     * @return Whether the object was added
     */
    abstract protected boolean add(T object);

    /**
     * Remove an object from the underlying collection. Implementations need not
     * check for a valid state; this is done by {@link #release(Object)}.
     *
     * @return Whether the object was removed
     */
    abstract protected boolean remove(T object);

    /**
     * Helper method for implementations that also implement {@link Iterable}.
     *
     * @param it The iterator to remove an element from
     */
    protected void releaseFrom(java.util.Iterator<T> it) {
        if (state == State.NEW) {
            throw new IllegalStateException("Sweeper: not yet opened");
        }

        if (state == State.CLEARED) {
            throw new IllegalStateException("Sweeper: already cleared");
        }

        it.remove();

        if ((state == State.CLOSED) && !it.hasNext()) {
            state = State.CLEARED;
        }
    }

    /*
     * ========================================================================
     * public interface Sweeper
     * ========================================================================
     */

    /**
    ** {@inheritDoc}
    **
    ** This implementation also throws {@link IllegalStateException} if {@code
    ** once} is {@code true} and the sweeper has already been opened once.
    */
    /* @Override* */
    public void open() {
        if ((state != State.NEW) && (once || (state != State.CLOSED))) {
            throw new IllegalStateException("Sweeper: already opened or cleared");
        }

        state = State.OPEN;
    }

    /**
    ** {@inheritDoc}
    */
    /* @Override* */
    public boolean acquire(T object) {
        if (state != State.OPEN) {
            throw new IllegalStateException("Sweeper: not open");
        }

        return add(object);
    }

    /**
    ** {@inheritDoc}
    */
    /* @Override* */
    public boolean release(T object) {
        if (state == State.NEW) {
            throw new IllegalStateException("Sweeper: not yet opened");
        }

        if (state == State.CLEARED) {
            throw new IllegalStateException("Sweeper: already cleared");
        }

        boolean b = remove(object);

        if ((state == State.CLOSED) && (size() == 0)) {
            state = State.CLEARED;
        }

        return b;
    }

    /**
    ** {@inheritDoc}
    */
    /* @Override* */
    public void close() {
        if (state != State.OPEN) {
            throw new IllegalStateException("Sweeper: not open");
        }

        state = State.CLOSED;

        if (size() == 0) {
            state = State.CLEARED;
        }
    }

    /**
    ** {@inheritDoc}
    */
    /* @Override* */
    public State getState() {
        return state;
    }

    /**
    ** {@inheritDoc}
    */
    /* @Override* */
    public boolean isCleared() {
        return state == State.CLEARED;
    }
}
