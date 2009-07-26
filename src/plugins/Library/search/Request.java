/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package plugins.Library.search;

import java.util.List;

/**
 * Interface for a request being handled asynchronously from the threads which
 * need to know it's status and results.
 * <p />
 * RequestStatus - what is happening currently with this request <br />
 * SubStage -	progress of nonparallel events which need to occur for this Request,
 *					eg. loading the base of an index before loading needed parts<br />
 * Blocks -		smaller elements of a stage, eg. the progress of a fetch
 *
 * @param <E> Class of elements of result of Request
 *
 * @author MikeB
 */
public interface Request<E> {
	/**
	 * UNSTARTED : Request initialised but not begun<br />
	 * INPROGRESS : Request started<br />
	 * PARTIALRESULT : Some result is availiable but not all<br />
	 * FINISHED : Complete result availiable<br />
	 * ERROR : Use getError() to retrieve the exception which stopped this request
	 */
	public enum RequestStatus { UNSTARTED, INPROGRESS, PARTIALRESULT, FINISHED, ERROR };

	/**
	 * Returns a RequestStatus for this Request
	 * @see RequestStatus
	 */
	public RequestStatus getRequestStatus();
	/**
	 * @return true if RequestStatus is FINISHED or ERROR
	 */
	public boolean isFinished();

	public Exception getError();

	/**
	 * SubStage number between 1 and SubStageCount, for when overall operation
	 * length is not known but number of stages is
	 */
	public int getSubStage();
	public int getSubStageCount();

	/**
	 * Array of names of stages, length should be equal to the result of getSubStageCount()
	 * @return null if not used
	 */
	public String[] stageNames();

	/**
	 * @return blocks completed in SubStage
	 */
	public long getNumBlocksCompleted();
	public long getNumBlocksTotal();
	/**
	 * @return true if NumBlocksTotal is known to be final
	 */
	public boolean isNumBlocksCompletedFinal();

	/**
	 * @return subject of this request
	 */
	public String getSubject();

	/**
	 * @return result of this request
	 */
	public E getResult() throws InvalidSearchException;
	/**
	 * @return true if RequestStatus is PARTIALRESULT or FINISHED
	 */
	public boolean hasResult();

	/**
	 * To be overridden by subclasses which depend on subrequests
	 * @return List of Requests, or null
	 */
	public List<Request> getSubRequests();

	/**
	 * Progress accessed flag<br />
	 * Should return true if the status of this request hasnt changed since it was last read, otherwise false.<br />
	 * If not implemented, return false
	 * @return true if progress hasn't changed since it was last read
	 */
	//public boolean progressAccessed();
}
