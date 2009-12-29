/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.event;

import plugins.Library.util.event.Sweeper.SweeperState;

/**
** A {@link Sweeper} which only counts the number of objects tracked. It does
** not care about which objects these are; it assumes the user handles this.
**
** @author infinity0
*/
public class CountingSweeper<T> implements Sweeper<T> {

	protected SweeperState state;
	protected int objects = 0;

	/**
	** Construct a new sweeper.
	**
	** @param autostart Whether to construct the sweeper already open.
	*/
	public CountingSweeper(boolean autostart) {
		state = (autostart)? SweeperState.OPEN: SweeperState.NEW;
	}

	/**
	** {@inheritDoc}
	*/
	/*@Override**/ public void open() {
		if (state != SweeperState.NEW) { throw new IllegalStateException("Sweeper: already opened"); }
		state = SweeperState.OPEN;
	}

	/**
	** {@inheritDoc}
	*/
	/*@Override**/ public int acquire(T object) {
		if (state != SweeperState.OPEN) { throw new IllegalStateException("Sweeper: not open"); }
		return objects++;
	}

	/**
	** {@inheritDoc}
	*/
	/*@Override**/ public int release(T object) {
		if (state == SweeperState.NEW) { throw new IllegalStateException("Sweeper: not yet opened"); }
		if (state == SweeperState.CLEARED) { throw new IllegalStateException("Sweeper: already cleared"); }
		--objects;
		if (objects > 0) {
			/* test most likely scenario first */
		} else if (objects == 0) {
			if (state == SweeperState.CLOSED) { state = SweeperState.CLEARED; }
		} else {
			assert(state == SweeperState.OPEN);
			throw new IllegalStateException("Sweeper: tried to release too many objects " + object);
		}
		return objects;
	}

	/**
	** {@inheritDoc}
	*/
	/*@Override**/ public void close() {
		if (state != SweeperState.OPEN) { throw new IllegalStateException("Sweeper: not open"); }
		state = SweeperState.CLOSED;
	}

	/**
	** {@inheritDoc}
	*/
	/*@Override**/ public SweeperState getState() {
		return state;
	}

}
