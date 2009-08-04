/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

import java.util.Iterator;
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
	** that are backed by a collection of tracker ids - see {@link
	** #makeProgressIterable(ProgressTracker, Iterable, boolean)} and {@link
	** #makeProgressIterable(Map, boolean)}.
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
		throw new UnsupportedOperationException("not implemented");
		/*for (Progress p: subprogress) {
			// URGENT join()
		}*/
	}


	/**
	** Creates an iterable over the {@link Progress} objects corresponding to
	** the given tracking ids, which are all tracked by the given tracker.
	**
	** @param tracker The single tracker for all the ids
	** @param ids The ids to track
	** @param pull Whether these are pull progresses or push progresses
	*/
	public static <P extends Progress> Iterable<P> makeProgressIterable(final ProgressTracker<?, P> tracker, final Iterable<?> ids, final boolean pull) {
		return (pull)?
		new Iterable<P>() {
			public Iterator<P> iterator() {
				final Iterator<?> it = ids.iterator();
				return new Iterator<P>() {
					private Object last;
					@Override public boolean hasNext() { return it.hasNext(); }
					@Override public P next() { return tracker.getPullProgress(last = it.next()); }
					@Override public void remove() { it.remove(); tracker.remPullProgress(last); }
				};
			}
		}:
		new Iterable<P>() {
			public Iterator<P> iterator() {
				final Iterator<?> it = ids.iterator();
				return new Iterator<P>() {
					private Object last;
					@Override public boolean hasNext() { return it.hasNext(); }
					@Override public P next() { return tracker.getPushProgress(last = it.next()); }
					@Override public void remove() { it.remove(); tracker.remPushProgress(last); }
				};
			}
		};
	}

	/**
	** Creates an iterable over the {@link Progress} objects corresponding to
	** the given tracking ids, which are each tracked by its own tracker.
	**
	** @param ids Map of tracking ids to their respective trackers
	** @param pull Whether these are pull progresses or push progresses
	*/
	public static <P extends Progress> Iterable<P> makeProgressIterable(final Map<Object, ProgressTracker<?, P>> ids, final boolean pull) {
		return (pull)?
		new Iterable<P>() {
			@Override public Iterator<P> iterator() {
				final Iterator<Map.Entry<Object, ProgressTracker<?, P>>> it = ids.entrySet().iterator();
				return new Iterator<P>() {
					private Map.Entry<Object, ProgressTracker<?, P>> last;
					@Override public boolean hasNext() { return it.hasNext(); }
					@Override public P next() { last = it.next(); return last.getValue().getPullProgress(last.getKey()); }
					@Override public void remove() { it.remove(); last.getValue().remPullProgress(last.getKey()); }
				};
			}
		}:
		new Iterable<P>() {
			@Override public Iterator<P> iterator() {
				final Iterator<Map.Entry<Object, ProgressTracker<?, P>>> it = ids.entrySet().iterator();
				return new Iterator<P>() {
					private Map.Entry<Object, ProgressTracker<?, P>> last;
					@Override public boolean hasNext() { return it.hasNext(); }
					@Override public P next() { last = it.next(); return last.getValue().getPushProgress(last.getKey()); }
					@Override public void remove() { it.remove(); last.getValue().remPushProgress(last.getKey()); }
				};
			}
		};
	}

}
