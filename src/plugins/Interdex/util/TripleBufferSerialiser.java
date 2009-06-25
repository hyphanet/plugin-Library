/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.Archiver.*;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
** Parallel Serialiser using three buffers to load-balance things.
**
** TODO expand doc, clean up structure of this...
**
** @author infinity0
*/
abstract public class TripleBufferSerialiser<T> /*implements Serialiser<T>*/ {

	// keep 3 buffers

	/**
	** Maximum number of waiting tasks that the BufferHandler will postpone
	** before handling a buffer.
	*/
	final int queueSize;

	/**
	** Queue of tasks currently waiting to be added to the buffers.
	*/
	protected ArrayBlockingQueue<TripleBufferTask> queue;

	/**
	** Handler for the buffers. Also acts as a big fat lock for all of them.
	*/
	BufferHandler handler;

	protected TripleBufferSerialiser(int i) {
		queueSize = i;
		queue = new ArrayBlockingQueue<TripleBufferTask>(i);
	}

	abstract public BufferHandler newBufferHandler();

	abstract public class BufferHandler extends Thread {

		TripleBufferTask[] heldTasks;
		int held = 0;

		public synchronized void run() {

			TripleBufferTask task;

			while ((task = pollQueue()) != null) {
				if (false /* we can fit task into one of the buffers */) {
					// put it into the buffer
				} else {
					// leave it for later
					heldTasks[held++] = task;

					if (held == queueSize) {

						// flush the most filled-up buffer,
						// put all the pending tasks into the buffers

					}
				}
			}

			// looks like we timed out, and there are no more tasks on the queue, so
			// handle what we have left, including heldTasks.

			// if everything can fit in 1, 2, or 3 buffers, do so

			// otherwise, distribute them across 4 buffers, as evenly as possible
			// argh! bin-packing problem.
			// (trivial if each task takes up only 1 space in the buffer, hard otherwise)
			// in the best case, the buffer will be over 3/4 full

		}

		public TripleBufferTask pollQueue() {
			try {
				return queue.poll(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// TODO better way of handling this...
				Thread.currentThread().interrupt();
				return null;
			}
		}

	}


	/************************************************************************
	 * public interface Serialiser
	 ************************************************************************/

	//public PullTask<T> makePullTask(Object o);

	//public PushTask<T> makePushTask(T o);

	//public T inflate(Object meta);

	//public Object deflate(T skel) throws IllegalArgumentException;

	abstract public class TripleBufferTask extends Task {

		protected Boolean started = Boolean.FALSE;
		protected Boolean done = Boolean.FALSE;

		public synchronized void start() {
			try {
				queue.put(this);
			} catch (InterruptedException e) {
				// TODO maybe have a different exception for this... IOException?
				throw new DataNotLoadedException("Serialisation task failed: interrupted!", e, this, null, null);
			}

			// if there is no handler, start one
			if (handler == null) {
				handler = newBufferHandler();
				handler.start();
			}

			started = Boolean.TRUE;
		}

		public synchronized void join() {
			if (!started) { return; }
			while (!done) {
				try {
					wait();
				} catch (InterruptedException e) {
					throw new DataNotLoadedException("Serialisation task failed: interrupted!", e, this, null, null);
				}
			}
		}

		// public void setOption(Object o);

	}

}
