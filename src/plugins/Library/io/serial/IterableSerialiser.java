/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.io.serial;

import plugins.Library.util.exec.TaskAbortException;

/**
** An interface that handles an iterable group of {@link Serialiser.Task}s.
**
** @author infinity0
*/
public interface IterableSerialiser<T> extends Archiver<T> {

	/**
	** Execute everything in a group of {@link PullTask}s, returning only when
	** they are all done.
	**
	** @param tasks The group of tasks to execute
	*/
	void pull(Iterable<PullTask<T>> tasks) throws TaskAbortException;

	// FIXME OPT NORM
	// Split up pull() into startPull and endPull. startPull would do the actual
	// fetch, probably to a Bucket, and endPull would do the YAML parsing.
	// This will allow us to run many slow startPull's at once, while limiting the 
	// number of memory-consuming endPull's so that we don't end up with a backlog
	// of lots of parsed data that we don't yet need.
	// See comments in FreenetArchiver.pull, ParallelSerialiser.createPullJob.
	
	/**
	** Execute everything in a group of {@link PushTask}s, returning only when
	** they are all done.
	**
	** @param tasks The group of tasks to execute
	*/
	void push(Iterable<PushTask<T>> tasks) throws TaskAbortException;

	// FIXME OPT HIGH
	// Split up push() into startPush and endPush. startPush will return as soon
	// as there are valid .meta values for each task. endPush will wait for the 
	// actual insert (or whatever) to finish, and then clear the .data.
	// Currently we have a dirty hack (for FreenetArchiver), which achieves a 
	// similar outcome:
	// FreenetArchiver returns as soon as it has a URI and pretends it's finished.
	// The high level caller using FreenetArchiver must call an explicit 
	// waitForAsyncInserts() method on it before finishing.
	// Packer.push() is more or less unchanged as a result.
	// Ultimately this is all necessary because inserts can take a long time and 
	// we need to limit memory usage.
	
}
