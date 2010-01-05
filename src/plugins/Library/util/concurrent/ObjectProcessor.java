/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.concurrent;

import plugins.Library.util.func.Closure;
import plugins.Library.util.func.SafeClosure;
import plugins.Library.util.func.Tuples.*;
import static plugins.Library.util.func.Tuples.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
** DOCUMENT.
**
** TODO NORM maybe just get rid of Scheduler
**
** @author infinity0
*/
public class ObjectProcessor<T, E, X extends Exception> implements Scheduler {

	final protected BlockingQueue<T> in;
	final protected BlockingQueue<$2<T, X>> out;
	final protected Map<T, E> dep;
	final protected Closure<T, X> clo;
	final protected Executor exec;

	protected Thread auto = null;
	protected boolean open = true;

	/**
	** @param i Queue for input items
	** @param o Queue for output/error items
	** @param d Queue for item deposits
	** @param c Closure to call on each item
	** @param x Executor to run each closure call
	*/
	public ObjectProcessor(BlockingQueue<T> i, BlockingQueue<$2<T, X>> o, Map<T, E> d, Closure<T, X> c, Executor x) {
		in = i;
		out = o;
		dep = d;
		clo = c;
		exec = x;
	}

	public synchronized void submit(T item, E deposit) throws InterruptedException {
		if (!open) { throw new IllegalStateException("ObjectProcessor: not open"); }
		if (dep.containsKey(item)) {
			throw new IllegalArgumentException("ObjectProcessor: object " + item + " already submitted");
		}

		dep.put(item, deposit);
		in.put(item);
	}

	public synchronized void update(T item, E deposit) {
		if (!open) { throw new IllegalStateException("ObjectProcessor: not open"); }
		if (!dep.containsKey(item)) {
			throw new IllegalArgumentException("ObjectProcessor: object " + item + " not yet submitted");
		}

		dep.put(item, deposit);
	}

	public synchronized $3<T, E, X> accept() throws InterruptedException {
		$2<T, X> item = out.take();
		return $3(item._0, dep.remove(item._0), item._1);
	}

	public synchronized boolean hasPending() {
		return !dep.isEmpty();
	}

	public synchronized boolean hasCompleted() {
		return !out.isEmpty();
	}

	public void handle() throws InterruptedException {
		final T item = in.take();
		exec.execute(new Runnable() {
			/*@Override**/ public void run() {
				X ex = null;
				try { clo.invoke(item); }
				catch (Exception e) { ex = (X)e; }
				try { out.put($2(item, ex)); }
				catch (InterruptedException e) { throw new UnsupportedOperationException(); }
			}
		});
	}

	/*@Override**/ public synchronized void close() {
		open = false;
		if (auto != null) { auto.interrupt(); }
	}

	/**
	** Starts a new thread to automatically handle objects submitted to this
	** processor.
	**
	** @param shutdown If not {@code null}, this is called on the executor
	**        when the thread completes.
	** @throws IllegalThreadStateException if a thread was already started
	*/
	public synchronized void auto(final SafeClosure<Executor> shutdown) {
		if (auto == null) {
			auto = new Thread() {
				@Override public void run() {
					try {
						while (!in.isEmpty() || open) {
							try {
								handle();
							} catch (InterruptedException e) {
								continue;
							}
						}
					} finally {
						if (shutdown != null) { shutdown.invoke(exec); }
					}
				}
			};
		}
		auto.start();
	}

	// public class Object

	@Override public void finalize() {
		close();
	}

}
