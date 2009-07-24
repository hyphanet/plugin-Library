/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

/**
** A progress that accumulates its data from the given group of progresses.
**
** @author infinity0
*/
public class CompoundProgress implements Progress {

	Iterable<? extends Progress> subprogress;

	public CompoundProgress() {
	}

	public void setSubprogress(Iterable<? extends Progress> subs) {
		subprogress = subs;
	}

	/*========================================================================
	  public interface Progress
	 ========================================================================*/

	@Override public String getName() {
		// PRIORITY
		return "a collection of tasks";
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

	@Override public void join() throws InterruptedException {
		throw new UnsupportedOperationException("not implemented");
		/*for (Progress p: subprogress) {
			// PRIORITY join()
		}*/
	}

}
