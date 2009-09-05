/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.exec;

import java.util.Date;
import java.util.Set;
import java.util.HashSet;

/**
** A partial implementation of {@link Execution}, defining some higher-level
** functionality in terms of lower-level ones.
**
** To implement {@link Execution} fully from this class, the programmer needs to
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
**
** * {@link #join()}
**
** @author MikeB
** @author infinity0
*/
public abstract class AbstractExecution<V> implements Execution<V> {

	protected Date start;
	protected Date stop = null;

	/**
	** Keeps track of the acceptors.
	*/
	final protected Set<ExecutionAcceptor<V>> accept = new HashSet<ExecutionAcceptor<V>>();

	/**
	** Subject of the execution.
	*/
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
	private V result;

	/**
	** Create an {@code Execution} with the given subject.
	**
	** @param subj The subject
	*/
	public AbstractExecution(String subj) {
		this.subject = subj;
		setStartDate();
	}

	protected synchronized void setStartDate() {
		if (start != null) { throw new IllegalStateException("Execution is already running"); }
		start = new Date();
		for (ExecutionAcceptor<V> acc: accept) { offerStarted(acc); }
	}

	protected void offerStarted(ExecutionAcceptor<V> acc) {
		try {
			acc.acceptStarted(this);
		} catch (RuntimeException e) {
			// TODO maybe log this somewhere
		}
	}

	protected synchronized void setResult(V res) {
		if (stop != null) { throw new IllegalStateException("Execution has already finished."); }
		result = res;
		stop = new Date();
		notifyAll();
		for (ExecutionAcceptor<V> acc: accept) { offerDone(acc); }
	}

	protected void offerDone(ExecutionAcceptor<V> acc) {
		try {
			acc.acceptDone(this, result);
		} catch (RuntimeException e) {
			// TODO maybe log this somewhere
		}
	}

	protected synchronized void setError(TaskAbortException err) {
		if (stop != null) { throw new IllegalStateException("Execution has already finished."); }
		error = err;
		stop = new Date();
		notifyAll();
		for (ExecutionAcceptor<V> acc: accept) { offerAborted(acc); }
	}

	protected void offerAborted(ExecutionAcceptor<V> acc) {
		try {
			acc.acceptAborted(this, error);
		} catch (RuntimeException e) {
			// TODO maybe log this somewhere
		}
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
	  public interface Execution
	 ========================================================================*/

	/*@Override**/ public Date getStartDate() {
		return start;
	}

	/*@Override**/ public long getTimeElapsed() {
		return System.currentTimeMillis() - start.getTime();
	}

	/*@Override**/ public long getTimeTaken() {
		if (stop == null) { return -1; }
		return stop.getTime() - start.getTime();
	}

	/**
	** {@inheritDoc}
	**
	** This implementation returns {@link #result} if {@link #error} is {@code
	** null}, otherwise it throws it.
	*/
	/*@Override**/ public V getResult() throws TaskAbortException {
		if (error != null) { throw error; }
		return result;
	}

	/*@Override**/ public synchronized void addAcceptor(ExecutionAcceptor<V> acc) {
		accept.add(acc);
		// trigger the event if the task is already done/aborted
		if (start != null) { offerStarted(acc); }
		if (error != null) { offerDone(acc); }
		if (result != null) { offerAborted(acc); }
	}

}
