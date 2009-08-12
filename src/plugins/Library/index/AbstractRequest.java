/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.serial.TaskAbortException;
import plugins.Library.serial.Progress;
import plugins.Library.serial.ProgressParts;

import java.util.Date;

/**
** A partial implementation of {@link Request}, defining some higher-level
** functionality in terms of lower-level ones.
**
** To implement {@link Request} fully from this class, the programmer needs to
** implement the following methods:
**
** * {@link Progress#getParts()}
**
** and make sure the {@link #error}, and {@link #result} fields are set
** appropriately during the course of the operation. (You can use {@link
** #setResult(Object)} and {@link #setError(TaskAbortException)} to do this.)
**
** The programmer might also wish to override the following:
** * {@link #join()}
**
** @author MikeB
** @author infinity0
*/
public abstract class AbstractRequest<T> implements Request<T> {

	final protected Date start;
	protected Date stop = null;

	protected String subject;

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
	** Create a Request with the given subject, with the start time set to the
	** current time.
	*/
	public AbstractRequest(String sub){
		this.subject = sub;
		this.start = new Date();
	}

	protected void setResult(T res) {
		if (stop != null) { throw new IllegalStateException("Task has already finished."); }
		result = res;
		stop = new Date();
	}

	protected void setError(TaskAbortException err) {
		if (stop != null) { throw new IllegalStateException("Task has already finished."); }
		error = err;
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

	@Override public String getSubject() {
		return subject;
	}

	@Override abstract public String getStatus();

	@Override abstract public ProgressParts getParts() throws TaskAbortException;

	@Override public boolean isDone() throws TaskAbortException {
		if (error != null) { throw error; }
		return result != null;
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
		return System.currentTimeMillis() - start.getTime();
	}

	/**
	** {@inheritDoc}
	**
	** This implementation returns {@link #result} if {@link #error} is {@code
	** null}, otherwise it throws it.
	*/
	@Override public T getResult() throws TaskAbortException {
		if (error != null) { throw error; }
		return result;
	}

}
