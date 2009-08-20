/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.exec;

import java.io.IOException;

/**
** Thrown when a task aborts. DOCUMENT
**
** @author infinity0
** @see Serialiser
*/
public class TaskAbortException extends Exception {

	/**
	** Whether the abortion was due to an error condition. Defaults to {@code
	** true}. A {@code false} value generally means that the task was itself
	** aborted, but that the intended effects of the task can be treated as
	** having been accomplished, eg. {@link TaskCompleteException}.
	**
	** Multiple-task serialisers should handle abortions as follows: non-error
	** abortions result in the task being removed from the taskgroup; error
	** abortions result are rethrown as-is.
	*/
	final protected boolean error;

	/**
	** Whether the failure is temporary, and the client should try again at
	** a later time. Eg. this should be {@code true} for abortions caused by
	** {@link IOException}, and {@code false} for abortions caused by {@link
	** DataFormatException}. Defaults to {@code false}.
	*/
	final protected boolean retry;

	/**
	** Constructs a new exception with the specified parameters.
	**
	** @param s The detail message
	** @param t The cause
	** @param e Whether the current abortion is {@link #error}.
	** @param r Whether a {@link #retry} is likely to succeed.
	*/
	public TaskAbortException(String s, Throwable t, boolean e, boolean r) {
		super(s, t);
		error = e;
		retry = r;
	}

	/**
	** Constructs a new exception with the specified paramaters, marked as
	** error.
	**
	** @param s The detail message
	** @param t The cause
	** @param r Whether a {@link #retry} is likely to succeed.
	*/
	public TaskAbortException(String s, Throwable t, boolean r) {
		this(s, t, true, r);
	}

	/**
	** Constructs a new exception with the specified paramaters, marked as
	** error and non-retry.
	**
	** @param s The detail message
	** @param t The cause
	*/
	public TaskAbortException(String s, Throwable t) {
		this(s, t, true, false);
	}

	/**
	** @see #error
	*/
	public boolean isError() {
		return error;
	}

	/**
	** @see #retry
	*/
	public boolean shouldRetry() {
		return retry;
	}

}
