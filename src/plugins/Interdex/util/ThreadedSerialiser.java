/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

/**
** Defines additional methods that a Serialiser using threading should support.
** All of these methods return {@link Thread} objects which have already been
** started, and whose {@link Thread#join()} methods will block until all the
** tasks passed into the method have finished. In other words, {@code
** start*().join()} should do the same thing as {@code do*()}.
*/
public interface ThreadedSerialiser<T> extends Serialiser<T> {

	/**
	** Start a {@link PullTask}.
	**
	** @return An already-started {@link Thread} running the tasks.
	*/
	public Thread startPull(PullTask<T> task);

	/**
	** Start a {@link PushTask}.
	**
	** @return An already-started {@link Thread} running the tasks.
	*/
	public Thread startPush(PushTask<T> task);

	/**
	** Start everything in a group of {@link PullTask}s. Either returns null,
	**
	** @return An already-started {@link Thread} running the series of tasks.
	*/
	public Thread startPull(Iterable<PullTask<T>> tasks);

	/**
	** Start everything in a group of {@link PushTask}s.
	**
	** @return An already-started {@link Thread} running the series of tasks.
	*/
	public Thread startPush(Iterable<PushTask<T>> tasks);

}
