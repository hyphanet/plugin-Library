/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.event;

/**
** A class which holds a group of objects, eg. for the purposes of delaying an
** action until all objects have been released.
**
** @author infinity0
*/
public interface Sweeper<T> extends java.io.Closeable {

	public enum SweeperState { NEW, OPEN, CLOSED, CLEARED }

	/**
	** Start accepting objects. If the trigger condition is reached whilst the
	** sweeper is accepting objects, execution should be delayed until {@link
	** #close()} is called.
	**
	** @throws IllegalStateException if the current state is not {@link
	**         SweeperState#NEW NEW}
	*/
	public void open();

	/**
	** Acquire the given object for tracking.
	**
	** @return Whether the object was acquired
	** @throws IllegalStateException if the current state is not {@link
	**         SweeperState#OPEN OPEN}
	*/
	public boolean acquire(T object);

	/**
	** Release the given object from being tracked.
	**
	** When the sweeper is {@link SweeperState#CLOSED CLOSED} and there are no
	** more objects to track, it becomes {@link SweeperState#CLEARED CLEARED}.
	**
	** @return Whether the object was released
	** @throws IllegalStateException if the current state is not {@link
	**         SweeperState#OPEN OPEN} or {@link SweeperState#CLOSED CLOSED}.
	*/
	public boolean release(T object);

	/**
	** Stop accepting objects.
	**
	** @throws IllegalStateException if the current state is not {@link
	**         SweeperState#OPEN OPEN}.
	*/
	public void close();

	/**
	** Returns the current state.
	*/
	public SweeperState getState();

	/**
	** Returns the number of objects held by the sweeper.
	*/
	public int size();

}
