/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

import plugins.Library.serial.Serialiser.*;
import plugins.Library.util.CompositeIterable;

import java.util.Map;

/**
** A progress that accumulates its data from the given group of progresses.
**
** TODO perhaps synchronize
**
** PRIORITY incorporate finalTotalEstimate into the status
**
** DOCUMENT
**
** @author infinity0
*/
public class CompoundProgress implements Progress {

	protected String name = "???";

	protected Iterable<? extends Progress> subprogress;

	public CompoundProgress() { }

	/**
	** Sets the iterable to accumulate progress information from. There are
	** several static helper methods you can use to create progress iterables
	** that are backed by a collection of tracker ids; see the rest of the
	** documentation for this class.
	**
	** @param subs The subprogresses to accumulating information from
	*/
	public void setSubprogress(Iterable<? extends Progress> subs) {
		subprogress = subs;
	}

	public int[] getStageSummary() {
		int[] counts = new int[3]; // total, inprogress, done
		for (Progress p: subprogress) {
			++counts[0];
			if (p == null) { continue; }
			++counts[1];
			if (!p.isTotalFinal() || p.partsDone() != p.partsTotal()) { continue; }
			++counts[2];
		}
		return counts;
	}

	public int[] getPartsSummary() {
		int[] counts = new int[3]; // (isTotalFinal)? 0: -1, inprogress, done
		for (Progress p: subprogress) {
			if (p == null) { counts[0] = -1; continue; }
			else if (!p.isTotalFinal()) { counts[0] = -1; }
			counts[1] += p.partsTotal();
			counts[2] += p.partsDone();
		}
		return counts;
	}

	public void setName(String n) {
		name = n;
	}

	/*========================================================================
	  public interface Progress
	 ========================================================================*/

	@Override public String getName() {
		return name;
	}

	@Override public String getStatus() {
		int[] s = getStageSummary();
		int[] p = getPartsSummary();
		return s[2] + "/" + s[1] + "/" + s[0] + " (" + p[2] + "/" + p[1] + "/" + (p[0]<0? "???": p[1])+ ")";
	}

	@Override public int partsDone() {
		int d = 0;
		for (Progress p: subprogress) {
			if (p != null) { d += p.partsDone(); }
		}
		return d;
	}

	@Override public int partsTotal() {
		int d = 0;
		for (Progress p: subprogress) {
			if (p != null) { d += p.partsTotal(); }
		}
		return d;
	}

	@Override public boolean isTotalFinal() {
		for (Progress p: subprogress) {
			if (p == null || !p.isTotalFinal()) { return false; }
		}
		return true;
	}

	/**
	** {@inheritDoc}
	**
	** This implementation extrapolates the number of parts the remaining tasks
	** will require from the combined number of parts of the in-progress tasks.
	*/
	@Override public int finalTotalEstimate() {
		int[] s = getStageSummary();
		int[] p = getPartsSummary();
		return p[1] / s[1] * p[0];
	}

	@Override public void join() throws InterruptedException, TaskAbortException {
		int s = 1;
		for (;;) {
			boolean foundnull = false;
			for (Progress p: subprogress) {
				if (p == null) { foundnull = true; continue; }
				p.join();
			}
			if (!foundnull) { break; }
			// yes, this is a really shit way of doing this. but the best alternative I
			// could come up with was the ugly commented-out stuff in ProgressTracker.

			// anyhow, Progress objects are usually created very very soon after its
			// tracking ID is added to the collection backing the Iterable<Progress>,
			// so this shouldn't happen that often.

			Thread.sleep(s*1000);
			// extend the sleep time with reducing probability, or reset it to 1
			if (s == 1 || Math.random() < 1/Math.log(s)/4) { ++s; } else { s = 1; }
		}
	}

	/*
	// moved here from ParallelSerialiser. might be useful.
	protected void joinAll(Iterable<P> plist) throws InterruptedException, TaskAbortException {
		Iterator<P> it = plist.iterator();
		while (it.hasNext()) {
			P p = it.next();
			try {
				p.join();
			} catch (TaskAbortException e) {
				if (e.isError()) {
					throw e;
				} else {
					// TODO perhaps have a handleNonErrorAbort() that can be overridden
					it.remove();
				}
			}
		}
	}
	*/


	/**
	** Creates an iterable over the {@link Progress} objects corresponding to
	** the given pull tracking ids, all tracked by the given tracker.
	**
	** @param tracker The single tracker for all the ids
	** @param ids The ids to track
	*/
	public static <T, P extends Progress> Iterable<P> makePullProgressIterable(final ProgressTracker<T, P> tracker, final Iterable<PullTask<T>> ids) {
		return new CompositeIterable<PullTask<T>, P>(ids) {
			@Override public P nextFor(PullTask<T> next) {
				return tracker.getPullProgress(next);
			}
		};
	}

	/**
	** Creates an iterable over the {@link Progress} objects corresponding to
	** the given push tracking ids, all tracked by the given tracker.
	**
	** @param tracker The single tracker for all the ids
	** @param ids The ids to track
	*/
	public static <T, P extends Progress> Iterable<P> makePushProgressIterable(final ProgressTracker<T, P> tracker, final Iterable<PushTask<T>> tasks) {
		return new CompositeIterable<PushTask<T>, P>(tasks) {
			@Override public P nextFor(PushTask<T> next) {
				return tracker.getPushProgress(next);
			}
		};
	}

	/**
	** Creates an iterable over the {@link Progress} objects corresponding to
	** the given pull tracking ids, each tracked by its own tracker.
	**
	** @param ids Map of tracking ids to their respective trackers
	*/
	public static <T, P extends Progress> Iterable<P> makePullProgressIterable(final Map<PullTask<T>, ProgressTracker<T, ? extends P>> ids) {
		return new CompositeIterable<Map.Entry<PullTask<T>, ProgressTracker<T, ? extends P>>, P>(ids.entrySet()) {
			@Override public P nextFor(Map.Entry<PullTask<T>, ProgressTracker<T, ? extends P>> next) {
				return next.getValue().getPullProgress(next.getKey());
			}
		};
	}

	/**
	** Creates an iterable over the {@link Progress} objects corresponding to
	** the given push tracking ids, each tracked by its own tracker.
	**
	** @param ids Map of tracking ids to their respective trackers
	*/
	public static <T, P extends Progress> Iterable<P> makePushProgressIterable(final Map<PushTask<T>, ProgressTracker<T, ? extends P>> ids) {
		return new CompositeIterable<Map.Entry<PushTask<T>, ProgressTracker<T, ? extends P>>, P>(ids.entrySet()) {
			@Override public P nextFor(Map.Entry<PushTask<T>, ProgressTracker<T, ? extends P>> next) {
				return next.getValue().getPushProgress(next.getKey());
			}
		};
	}

}
