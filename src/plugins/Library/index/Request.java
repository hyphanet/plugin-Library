/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.serial.TaskAbortException;
import plugins.Library.serial.Progress;

import java.util.Date;
import java.util.List;

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
public interface Request<T> extends Progress {

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
	** Returns the subject of this operation.
	*/
	public String getSubject();

	/**
	** Gets the result of this operation.
	*/
	public T getResult() throws TaskAbortException;


	/**
	** Whether the operation has completed.
	**
	** TODO perhaps make this part of Progress
	*/
	public boolean isDone();

	/**
	** Returns a RequestState describing the status of the operation.
	**
	** @see RequestState
	*/
	public RequestState getState();

	/**
	** Returns a {@link String} describing the status of the current stage.
	*/
	public String getCurrentStatus();

	/**
	** Returns the current stage of the operation.
	*/
	public String getCurrentStage();

	/**
	** To be overridden by subclasses which depend on subrequests.
	**
	** TODO: infinity0: I wasn't sure how to remove this functionality from
	** your other classes (FindRequest, Search) - can you do this part?
	**
	** @deprecated
	** @return List of Requests, or null
	*/
	public List<Request> getSubRequests();

	/**
	** Records the general state of the operation.
	**
	** ;UNSTARTED : Request initialised but not begun
	** ;INPROGRESS : Request started
	** ;PARTIALRESULT : Some result is availiable but not all
	** ;FINISHED : Complete result availiable
	** ;ERROR : Use {@link #getError()} to retrieve the exception which aborted
	** this operation
	*/
	public enum RequestState { UNSTARTED, INPROGRESS, PARTIALRESULT, FINISHED };

}
