/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */


package plugins.Library.util.event;

/**
 * A {@link Sweeper} which only counts the number of objects added. It does
 * not care about which objects these are; it assumes the user handles this.
 *
 * @author infinity0
 */
public class CountingSweeper<T> extends AbstractSweeper<T> {
    protected int count = 0;

    /**
     * Construct a new sweeper.
     *
     * @param autostart Whether to construct the sweeper already open
     * @param onceonly Whether the sweeper can only be opened once
     */
    public CountingSweeper(boolean autostart, boolean onceonly) {
        super(autostart, onceonly);
    }

    /**
    ** {@inheritDoc}
    **
    ** This implementation will always return true, since it does not keep
    ** track of the actual count in the collection.
    */
    @Override
    protected boolean add(T object) {
        ++count;

        return true;
    }

    /**
    ** {@inheritDoc}
    **
    ** This implementation will always return true, since it does not keep
    ** track of the actual count in the collection.
    */
    @Override
    protected boolean remove(T object) {
        if (count == 0) {
            throw new IllegalStateException("CountingSweeper: no objects to release: " + object);
        }

        --count;

        return true;
    }

    /**
    ** {@inheritDoc}
    */
    /* @Override* */
    public int size() {
        return count;
    }
}
