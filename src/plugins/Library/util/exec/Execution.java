/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */


package plugins.Library.util.exec;

import java.util.Date;

//import java.util.concurrent.Future;

/**
 * Interface for an execution being handled concurrently to the threads which
 * need to know its status and results.
 *
 * TODO LOW maybe have {@code Execution<K, V>} and {@code K getSubject()}
 *
 * @param <V> Type of result of the execution
 * @author MikeB
 * @author infinity0
 */
public interface Execution<V> extends Progress /* , Future<V> */ {

    /**
     * Returns the Date object representing the time at which this request was
     * started.
     */
    public Date getStartDate();

    /**
     * Get the number of milliseconds elapsed since the request was started.
     */
    public long getTimeElapsed();

    /**
     * Get the number of milliseconds it took for the execution to complete or
     * abort, or {@code -1} if it has not yet done so.
     */
    public long getTimeTaken();

    /**
     * Gets the result of this operation, or throws the error that caused it to
     * abort.
     */
    public V getResult() throws TaskAbortException;

    /**
     * Attach an {@link ExecutionAcceptor} to this execution.
     *
     * Implementations should trigger the appropriate methods on all acceptors
     * as the corresponding events are triggered, and on newly-added acceptors
     * if events have already happened, ''in the same order in which they
     * happened'''. (This is slightly different from a listener, where past
     * events are missed by newly-added listeners.)
     *
     * Generally, adding an acceptor twice should not give different results,
     * but this is up to the implementation. Unchecked exceptions (ie. {@link
     * RuntimeException}s) thrown by any acceptor should be caught (and ideally
     * handled).
     */
    public void addAcceptor(ExecutionAcceptor<V> acc);

    /*
     * ========================================================================
     * public interface Future
     * ========================================================================
     */

    // TODO LOW could retrofit this if it's ever needed...
    // *@Override**/ public boolean cancel(boolean mayInterruptIfRunning);
    // *@Override**/ public V get();
    // *@Override**/ public V get(long timeout, TimeUnit unit);
    // *@Override**/ public boolean isCancelled();
    // *@Override**/ public boolean isDone();
}
