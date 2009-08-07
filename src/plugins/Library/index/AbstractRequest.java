/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.index.Request.RequestState;
import plugins.Library.serial.TaskAbortException;

import java.util.List;
import java.util.Date;

/**
** A partial implementation of {@link Request}, defining some higher-level
** functionality in terms of lower-level ones.
**
** To implement {@link Request} fully from this class, the programmer needs to
** implement the following methods:
**
** * {@link Request#getCurrentStage()}
** * {@link Request#getCurrentStatus()}
** * {@link Request#partsDone()}
** * {@link Request#partsTotal()}
** * {@link Request#isTotalFinal()}
**
** and make sure the {@link #state}, {@link #error}, and {@link #result} fields
** are set appropriately during the course of the operation. (You can use
** {@link #setResult(Object)} and {@link #setError(TaskAbortException)} to do
** this.)
**
** The programmer might also wish to override the following:
**
** * {@link #finalTotalEstimate()}
** * {@link #join()}
**
** @author MikeB
** @author infinity0
*/
public abstract class AbstractRequest<T> implements Request<T> {

	final protected String subject;
	final protected Date start;
	protected Date stop = null;

	/**
	** Holds the state of the operation. Returned by {@link #getState()}.
	*/
	protected RequestState state = RequestState.UNSTARTED;

	/**
	** Holds the error that caused the operation to abort, if any. If not
	** {@code null}, then thrown by {@link #getResult()}.
	*/
	protected TaskAbortException error;

	/**
	** Holds the result of the operation once it completes. If {@link #error}
	** is {@code null}, then returned by {@link #getResult()}.
	*/
	protected T result;

	/**
	** Create Request of the given subject, with the start time set to the
	** current time.
	**
	** @param subject
	*/
	public AbstractRequest(String subject){
		this.subject = subject;
		this.start = new Date();
	}

	protected void setResult(T res) {
		if (stop != null) { throw new IllegalStateException("Task has already finished."); }
		result = res;
		state = RequestState.FINISHED;
		stop = new Date();
	}

	protected void setError(TaskAbortException err) {
		if (stop != null) { throw new IllegalStateException("Task has already finished."); }
		error = err;
		state = RequestState.FINISHED;
		stop = new Date();
	}

	/**
	** Get the time taken for the operation to finish.
	**
	** TODO put this into {@link Request}?
	*/
	public long getTimeTaken() {
		if (stop == null) { throw new IllegalStateException("Task not yet finished."); }
		return stop.getTime() - start.getTime();
	}

	/*========================================================================
	  public interface Progress
	 ========================================================================*/

	@Override public String getName() {
		return "Requesting " + getSubject();
	}


	@Override public String getStatus() {
		String s = partsDone() + "/" + partsTotal();
		if (!isTotalFinal()) { s += "???"; }
		return s;
	}

	/**
	** {@inheritDoc}
	**
	** This implementation does not give an estimate.
	*/
	@Override public int finalTotalEstimate() {
		return -1;
	}

	/**
	** {@inheritDoc}
	**
	** This implementation throws {@link UnsupportedOperationException}.
	*/
	@Override public void join() throws InterruptedException, TaskAbortException {
		throw new UnsupportedOperationException("not implemented");
	}

	/*========================================================================
	  public interface Request
	 ========================================================================*/

	@Override public Date getStartDate() {
		return start;
	}

	@Override public long getTimeElapsed() {
		return (new Date()).getTime() - start.getTime();
	}

	@Override public String getSubject() {
		return subject;
	}

	/**
	** {@inheritDoc}
	**
	** This implementation returns {@link #result} if {@link #error} is {@code
	** null}, otherwise it throws it.
	*/
	@Override public T getResult() throws TaskAbortException {
		if (error != null) {
			throw error;
		} else {
			return result;
		}
	}

	/**
	** {@inheritDoc}
	**
	** This implementation returns {@link #state}.
	*/
	@Override public RequestState getState() {
		return state;
	}

	/**
	** {@inheritDoc}
	**
	** This implementation returns true if RequestState is FINISHED
	 * @deprecated now ERROR is gone this seems pointless
	*/
	@Override public boolean isDone() {
		return state==RequestState.FINISHED;
	}


	/**
	** {@inheritDoc}
	**
	** This implementation returns {@code null}.
	*/
	@Override public List<Request> getSubRequests() {
		return null;
	}

}
