/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

import plugins.Library.serial.Serialiser.*;
import plugins.Library.util.concurrent.Scheduler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;

/**
** An interface that passes tasks to a {@link Scheduler} and retrieves them
** when they are complete, through the use of {@link BlockingQueue}s.
**
** @author infinity0
*/
public interface ScheduledSerialiser<T> extends IterableSerialiser<T> {

	/**
	** Creates a {@link Scheduler} that executes {@link PullTask}s, using the
	** given {@link BlockingQueue}s to communicate the task information.
	**
	** The {@link Scheduler} should be {@link Scheduler#close()}d when no more
	** tasks will be passed to it.
	**
	** Implementations should only add to the output and error queues when the
	** action represented by the task has '''completed'''. For example, the
	** error map should not contain any {@link TaskInProgressException}.
	**
	** @param input Queue to add task requests to
	** @param output Queue to pop completed tasks from
	** @param error Map of tasks to the errors that aborted them
	*/
	public Scheduler pullSchedule(BlockingQueue<PullTask<T>> input,
	                              BlockingQueue<PullTask<T>> output,
	                              ConcurrentMap<PullTask<T>, TaskAbortException> error);

	/**
	** Creates a {@link Scheduler} that executes {@link PushTask}s, using the
	** given {@link BlockingQueue}s to communicate the task information.
	**
	** The {@link Scheduler} should be {@link Scheduler#close()}d when no more
	** tasks will be passed to it.
	**
	** Implementations should only add to the output and error queues when the
	** action represented by the task has '''completed'''. For example, the
	** error map should not contain any {@link TaskInProgressException}.
	**
	** @param input Queue to add task requests to
	** @param output Queue to pop completed tasks from
	** @param error Map of tasks to the errors that aborted them
	*/
	public Scheduler pushSchedule(BlockingQueue<PushTask<T>> input,
	                              BlockingQueue<PushTask<T>> output,
	                              ConcurrentMap<PushTask<T>, TaskAbortException> error);

}
