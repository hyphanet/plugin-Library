/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

/**
** A progress that accumulates its data from the given group of progresses.
**
** TODO perhaps synchronize
**
** PRIORITY incorporate finalTotalEstimate into the status
**
** @author infinity0
*/
public class CompoundProgress implements Progress {

	protected String name = "";

	Iterable<? extends Progress> subprogress;

	public CompoundProgress() {
	}

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
			// PRIORITY join()
		}*/
	}

}
