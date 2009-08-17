/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.serial.TaskAbortException;
import plugins.Library.serial.Progress;

import java.util.Date;

/**
** Interface for a request being handled asynchronously from the threads which
** need to know its status and results.
**
** ;RequestState : what is happening currently with this request
** ;Stage : non-parallel sequence of subevents that comprise the whole
** operation, eg. loading the base of an index before loading needed parts
**
** TODO maybe have {@code Request<K, V>} and {@code K getSubject()} and {@code
** V getResult()}.
**
** TODO rename this to Operation?
**
** @param <T> Type of result of the operation
** @author MikeB
** @author infinity0
*/
public interface Request<T> extends Progress, Runnable {

	/**
	** Returns the Date object representing the time at which this request was
	** started.
	*/
	public Date getStartDate();

	/**
	** Get the number of milliseconds elapsed since the request was started.
	*/
	public long getTimeElapsed();

	/**
	** Gets the result of this operation.
	*/
	public T getResult() throws TaskAbortException;

	/**
	** Run the request. Implementations should throw {@link
	** IllegalStateException} if the task is already running.
	**
	** @throws IllegalStateException if the request is already running.
	*/
	/*@Override**/ public void run();

	// URGENT needs a way to atomically start the request if it's not already started.

}
