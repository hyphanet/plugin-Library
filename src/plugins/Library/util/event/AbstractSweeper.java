/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.event;

import plugins.Library.util.event.Sweeper.SweeperState;

/**
** A partial implementation of {@link Sweeper}, defining some high-level
** methods in terms of lower ones.
**
** This implementation is '''not''' thread-safe.
**
** @author infinity0
*/
abstract public class AbstractSweeper<T> implements Sweeper<T> {

	protected SweeperState state;

	/**
	** Construct a new sweeper.
	**
	** @param autostart Whether to construct the sweeper already open.
	*/
	public AbstractSweeper(boolean autostart) {
		state = (autostart)? SweeperState.OPEN: SweeperState.NEW;
	}

	/**
	** Add an object to the underlying collection. Implementations need not
	** check for a valid state; this is done by {@link #acquire(Object)}.
	**
	** @return Whether the object was added.
	*/
	abstract protected boolean add(T object);

	/**
	** Remove an object from the underlying collection. Implementations need not
	** check for a valid state; this is done by {@link #release(Object)}.
	**
	** @return Whether the object was removed.
	*/
	abstract protected boolean remove(T object);

	/**
	** Helper method for implementations that also implement {@link Iterable}.
	**
	** @param it The iterator to remove an element from.
	*/
	protected void releaseFrom(java.util.Iterator<T> it) {
		if (state == SweeperState.NEW) { throw new IllegalStateException("Sweeper: not yet opened"); }
		if (state == SweeperState.CLEARED) { throw new IllegalStateException("Sweeper: already cleared"); }

		it.remove();
		if (state == SweeperState.CLOSED && !it.hasNext()) { state = SweeperState.CLEARED; }
	}

	/*========================================================================
	  public interface Sweeper
	 ========================================================================*/

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
	/*@Override**/ public boolean acquire(T object) {
		if (state != SweeperState.OPEN) { throw new IllegalStateException("Sweeper: not open"); }

		return add(object);
	}

	/**
	** {@inheritDoc}
	*/
	/*@Override**/ public boolean release(T object) {
		if (state == SweeperState.NEW) { throw new IllegalStateException("Sweeper: not yet opened"); }
		if (state == SweeperState.CLEARED) { throw new IllegalStateException("Sweeper: already cleared"); }

		boolean b = remove(object);
		if (state == SweeperState.CLOSED && size() == 0) { state = SweeperState.CLEARED; }
		return b;
	}

	/**
	** {@inheritDoc}
	*/
	/*@Override**/ public void close() {
		if (state != SweeperState.OPEN) { throw new IllegalStateException("Sweeper: not open"); }
		state = SweeperState.CLOSED;
		if (size() == 0) { state = SweeperState.CLEARED; }
	}

	/**
	** {@inheritDoc}
	*/
	/*@Override**/ public SweeperState getState() {
		return state;
	}

}
