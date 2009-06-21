/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

/**
** Parallel Serialiser using three buffers to load-balance things.
**
** TODO expand doc.
**
** @author infinity0
*/
abstract public class TripleBufferSerialiser<T> {

	// keep 3 buffers

	/**
	** Maximum number of waiting tasks that the BufferHandler will postpone
	** before handling a buffer.
	*/
	final int maxQueue = 4;

	public class BufferHandler extends Thread {

		public synchronized void run() {
			while (!buffersEmpty()) {
				// get the queue size once, instead of holding a lock on queue; improves concurrency
				int qsz = queueSize();

				if (qsz > maxQueue) {
					// handle the fullest buffer and then rotate buffers around.
					// synchronized (queue) { queue.notifyAll(); }

				} else if (buffersChanged()) {
					// continue, and wait

				} else if (qsz > 0) {
					// handle the fullest buffer and then rotate buffers around.
					// synchronized (queue) { queue.notifyAll(); }

				} else {
					// looks like nobody wants me to do anything anymore

					// if everything can fit in 1 buffers, do so

					// if everything can fit in 2 buffers, do so

					// otherwise, distribute them across 3 buffers.
					// argh! bin-packing problem.
					// (trivial if each task takes up only 1 space in the buffer, hard otherwise)
					// in the best case, the buffer will be 2/3 full
				}

				wait(1000);

			}

		}
	}

	/**
	** Handler for the buffers. Also acts as a big fat lock for all of them.
	*/
	BufferHandler handler;

	public boolean buffersChanged() {
		synchronized (handler) {
			throw new UnsupportedOperationException("Not implemented.");
		}
	}

	public boolean buffersEmpty() {
		synchronized (handler) {
			throw new UnsupportedOperationException("Not implemented.");
		}
	}

	public boolean buffersOvercrowded(TripleBufferSerialiseTask t) {
		synchronized (handler) {
			throw new UnsupportedOperationException("Not implemented.");
		}
	}

	/**
	** Try to put a task into a buffer. If this succeeds, notify the handler,
	** and return true.
	*/
	public boolean buffersPut(TripleBufferSerialiseTask t) {
		synchronized (handler) {
			throw new UnsupportedOperationException("Not implemented.");
			handler.notify();
		}
	}

	/**
	** Counts the number of tasks currently waiting to be added to the buffers.
	*/
	protected Integer queue;

	public void joinQueue() {
		synchronized (queue) {
			--queue;
		}
	}

	public void partQueue(int size) {
		synchronized (queue) {
			++queue;
		}
	}

	public int queueSize() {
		synchronized (queue) {
			int i = queue;
			return i;
		}
	}


	/************************************************************************
	 * public interface Serialiser
	 ************************************************************************/

	public InflateTask<T> newInflateTask(Object o);

	public DeflateTask<T> newDeflateTask(T o);

	public T inflate(Object dummy);

	public Object deflate(T skel) throws IllegalArgumentException;

	public interface TripleBufferSerialiseTask {

		public void start() {
			throw new UnsupportedOperationException("Not implemented.");
			// if the buffers are empty, add the task and start a BufferHandler, and return.

			synchronized (queue) {
				joinQueue();
				// while buffersPut(this) { queue.wait(); }
				partQueue();
			}

		}

		public void join() {
			// wait until this task is complete
			throw new UnsupportedOperationException("Not implemented.");
		}

		public void setOption(Object o);

	}

}
