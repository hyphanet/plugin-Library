/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.event;

/**
** A class which keeps track of objects and runs a job when there are none
** left. A sweeper only accepts objects when it is open, and only runs the job
** when it is closed.
**
** It is up to the implementation whether:
**
** * badly-timed calls to {@link #open()}, {@link #close()}, or {@link
**   #maybeRun()} return silently or throw {@link IllegalStateException}.
** * a newly-instantiated sweeper is automatically open
** * the sweeper can be re-opened after being closed.
** * the job runs immediately after the last object is {@link #release()
**   released}
**
** @author infinity0
*/
public interface Sweeper<T> extends java.io.Closeable {

	/**
	** Start accepting objects. If the trigger condition is reached whilst the
	** sweeper is accepting objects, execution should be delayed until {@link
	** close()} is called.
	*/
	public void open() throws IllegalStateException;

	/**
	** Acquire the given object for tracking.
	**
	** @return Number of objects held *after* the operation
	** @throws IllegalStateException if the job has already been executed, or
	**         if the *** is not currently accepting objects.
	*/
	public int acquire(T object);

	/**
	** Release the given object from being tracked.
	**
	** @return Number of objects held *after* the operation
	** @throws IllegalStateException if the job has already been executed
	*/
	public int release(T object);

	/**
	** Stop accepting objects.
	*/
	public void close() throws IllegalStateException;

	/**
	** Run the job, only if there are no objects left.
	**
	** @return Whether the job was run (and the condition was met).
	*/
	public boolean maybeRun() throws IllegalStateException;

}
