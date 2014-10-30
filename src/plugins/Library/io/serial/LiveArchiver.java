/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */


package plugins.Library.io.serial;

import plugins.Library.io.serial.Serialiser.*;
import plugins.Library.util.exec.Progress;
import plugins.Library.util.exec.TaskAbortException;

/**
 * An interface that handles a single {@link Serialiser.Task} and sends live
 * updates of its {@link Progress}.
 *
 * @author infinity0
 */
public interface LiveArchiver<T, P extends Progress> extends Archiver<T> {

    /**
     * Executes a {@link PullTask} and update the progress associated with it.
     * {@link TaskAbortException}s are stored in the progress object, rather
     * than thrown.
     *
     * Implementations must also modify the state of the progress object such
     * that after the operation completes, all threads blocked on {@link
     * Progress#join()} will either return normally or throw the exact {@link
     * TaskAbortException} that caused it to abort.
     *
     * '''If this does not occur, deadlock will result'''.
     */
    public void pullLive(PullTask<T> task, P p) throws TaskAbortException;

    /**
     * Executes a {@link PushTask} and update the progress associated with it.
     * {@link TaskAbortException}s are stored in the progress object, rather
     * than thrown.
     *
     * Implementations must also modify the state of the progress object such
     * that after the operation completes, all threads blocked on {@link
     * Progress#join()} will either return normally or throw the exact {@link
     * TaskAbortException} that caused it to abort.
     *
     * '''If this does not occur, deadlock will result'''.
     */
    public void pushLive(PushTask<T> task, P p) throws TaskAbortException;
}
