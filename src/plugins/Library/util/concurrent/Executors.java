/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
** Class providing various {@link Executor}s.
**
** @author infinity0
*/
public class Executors {

	/**
	** Classes can reference this field when they don't want to create a new
	** executor themselves. This is just a wrapper around the real executor,
	** which can be set via {@link #setDefaultExecutor}. If no executor has
	** been set by the time the first call to {@link Executor#execute()} is
	** made, a {@link ThreadPoolExecutor} is created, using 64 cached threads
	** with a timeout of 1 second and a rejection policy of "{@link
	** ThreadPoolExecutor.CallerRunsPolicy caller runs}".
	*/
	final public static Executor DEFAULT_EXECUTOR = new Executor() {
		/*@Override**/ public void execute(Runnable r) {
			if (default_exec == null) {
				default_exec = new ThreadPoolExecutor(
					0x40, 0x40, 1, TimeUnit.SECONDS,
					new LinkedBlockingQueue<Runnable>(),
					new ThreadPoolExecutor.CallerRunsPolicy()
				);
			}
			default_exec.execute(r);
		}
	};

	public static void setDefaultExecutor(Executor e) {
		default_exec = e;
	}

	/**
	** The executor backing {@link #DEFAULT_EXECUTOR}.
	*/
	private static Executor default_exec = null;

	private Executors() { }

}
