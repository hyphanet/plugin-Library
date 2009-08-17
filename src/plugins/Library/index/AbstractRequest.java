/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.serial.TaskAbortException;
import plugins.Library.serial.TaskInProgressException;
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
** The programmer must call {@link #setResult(Object)} and {@link
** #setError(TaskAbortException)} to set the error and result fields correctly.
** If these methods are overridden, they '''must''' call the overriden ones
** using {@code super}.
**
** The programmer might also wish to override the following:
** * {@link #join()}
**
** @author MikeB
** @author infinity0
*/
public abstract class AbstractRequest<T> implements Request<T> {

	protected Date start;
	protected Date stop = null;

	final protected String subject;

	/**
	** Holds the error that caused the operation to abort, if any. If not
	** {@code null}, then thrown by {@link #getResult()}.
	*/
	private TaskAbortException error;

	/**
	** Holds the result of the operation once it completes. If {@link #error}
	** is {@code null}, then returned by {@link #getResult()}.
	*/
	private T result;

	/**
	** Create a Request with the given subject, and immediately starts it.
	*/
	public AbstractRequest(String sub) {
		this(sub, true);
	}

	/**
	** Create a Request with the given subject.
	**
	** @param sub The subject
	** @param autorun Whether to automatically run the request in the same
	**        thread as the caller of the constructor.
	*/
	public AbstractRequest(String sub, boolean autorun) {
		this.subject = sub;
		if (autorun) { run(); }
	}

	protected synchronized void setStartDate() {
		if (start != null) { throw new IllegalStateException("Request is already running"); }
		start = new Date();
	}

	protected synchronized void setResult(T res) {
		if (stop != null) { throw new IllegalStateException("Task has already finished."); }
		result = res;
		stop = new Date();
		notifyAll();
	}

	protected synchronized void setError(TaskAbortException err) {
		if (stop != null) { throw new IllegalStateException("Task has already finished."); }
		error = err;
		stop = new Date();
		notifyAll();
	}

	/**
	** Get the time taken for the operation to finish. If this is negative, the
	** task has not yet completed.
	**
	** TODO put this into {@link Request}?
	*/
	public long getTimeTaken() {
		if (stop == null) { return -1; }
		return stop.getTime() - start.getTime();
	}

	/*========================================================================
	  public interface Progress
	 ========================================================================*/

	/*@Override**/ public String getSubject() {
		return subject;
	}

	abstract public String getStatus();

	abstract public ProgressParts getParts() throws TaskAbortException;

	/*@Override**/ public synchronized boolean isDone() throws TaskAbortException {
		if (error != null) { throw error; }
		return result != null;
	}

	/*@Override**/ public synchronized boolean isStarted() {
		return start != null;
	}

	/*@Override**/ public synchronized void join() throws InterruptedException, TaskAbortException {
		while (stop == null) { wait(); }
		if (error != null) { throw error; }
	}

	/*========================================================================
	  public interface Request
	 ========================================================================*/

	/*@Override**/ public Date getStartDate() {
		return start;
	}

	/*@Override**/ public long getTimeElapsed() {
		return System.currentTimeMillis() - start.getTime();
	}

	/**
	** {@inheritDoc}
	**
	** This implementation returns {@link #result} if {@link #error} is {@code
	** null}, otherwise it throws it.
	*/
	/*@Override**/ public T getResult() throws TaskAbortException {
		if (error != null) { throw error; }
		return result;
	}

	/**
	** {@inheritDoc}
	**
	** This implementation just sets the start date.
	*/
	/*@Override**/ public void run() {
		setStartDate();
		runReal();
	}

	/*@Override**/ public void execute() throws TaskInProgressException {
		synchronized (this) {
			if (start != null) {
				throw new TaskInProgressException("Task already in progress", this);
			}
			setStartDate();
		}
		runReal();
	}

	// PRIORITY find a better way to do this
	public void runReal() { }

}
