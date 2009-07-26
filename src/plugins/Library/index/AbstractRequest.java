/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.index.Request;
import plugins.Library.index.Request.RequestState;
import plugins.Library.serial.TaskAbortException;

import java.util.List;
import java.util.Date;

/**
** A partial implementation of {@link Request}, defining some higher-level
** functionality in terms of lower-level ones.
**
** @author MikeB
** @author infinity0
*/
public abstract class AbstractRequest<T> implements Request<T> {

	final protected String subject;
	final protected Date start;

	protected RequestState status = RequestState.UNSTARTED;
	protected Exception err;
	protected T result;

	/**
	 * Create Request of stated type & subject
	 * @param subject
	 */
	public AbstractRequest(String subject){
		this.subject = subject;
		this.start = new Date();
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

	@Override public T getResult() throws TaskAbortException {
		return result;
	}

	@Override public RequestState getState() {
		return status;
	}

	/**
	 * {@inheritDoc}
	 *
	 * This implementation returns true if RequestState is FINISHED or ERROR
	 */
	@Override public boolean isDone() {
		return status==RequestState.FINISHED || status == RequestState.ERROR;
	}

	@Override public Exception getError() {
		return err;
	}

	@Override public List<Request> getSubRequests() {
		return null;
	}

	@Override public void join() {
		throw new UnsupportedOperationException("not implemented");
	}

}
