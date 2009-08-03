/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

/**
** Basic progress implementation that has additional setter methods.
**
** '''USE OF THIS CLASS IS PRONE TO DEADLOCK - MAKE SURE YOU MATCH UP YOUR
** CALLS TO {@link #addPartDone()} AND {@link #addTotal(int, boolean)}.''' I
** will deal with this at some point, and make this class easier to use...
** possibly have a setDone() method like AtomicProgress has...
**
** NOTE: this implementation considers the task to be done (ie. {@link #join()}
** will unblock) when {@code totalfinal && pdone == total}. Hence, if you pass
** the progress object onto a child serialiser, but do additional processing of
** the task data after the child returns, '''it is vital that you call {@code
** addTotal(1, false)} before calling the child serialiser, and {@code
** addPartDone()} after it.''' Otherwise, if the child serialiser triggers the
** aforementioned condition, it will cause other threads blocked on {@code
** join()} to unblock and think the task has been completed, before the
** aforementioned additional processing has occured. ie:
**
** * Parent: [progress is at 8/8, not final]
** * Parent: subsrl.pullLive(task, progress)
** * Child: addTotal(8, true); [progress is at 8/16, final]
** * Child: addPartDone() * 8; [progress is at 16/16, final]
** * '''all threads blocked on {@link #join()} will unblock'''
** * Parent: yay, child has done its stuff, now I need to do some extra stuff
**   to actually complete the task
** * other threads: "wtf? data's changing randomly!?"
**
** (We don't wait for the child serialiser to finish to finalise the total,
** because we want the total to be finalised as soon as possible. Generally, we
** let the leaf serialiser finalise the total.)
**
** TODO perhaps synchronize
**
** @author infinity0
*/
public class SimpleProgress implements Progress {

	protected String name = "???";

	protected int pdone;

	protected int total;

	protected boolean totalfinal;

	protected boolean inprogress = true;

	protected TaskAbortException abort = null;

	public SimpleProgress() { }

	public synchronized void addPartDone() {
		pdone++;
		assert(pdone <= total);
		if (totalfinal && pdone == total) {
			inprogress = false;
			notifyAll();
		}
	}

	public synchronized void addTotal(int parts, boolean finalise) {
		if (finalise) {
			if (!totalfinal) {
				total += parts;
				totalfinal = true;
			} else {
				throw new IllegalArgumentException("Total already finalised");
			}
		} else {
			if (!totalfinal) {
				total += parts;
			} else {
				throw new IllegalArgumentException("Cannot un-finalise a final total!");
			}
		}
	}

	public synchronized void setAbort(TaskAbortException e) {
		abort = e;
		inprogress = false;
		notifyAll();
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
		String s = pdone + "/" + total;
		if (!totalfinal) { s += "???"; }
		return s;
	}

	@Override public int partsDone() {
		return pdone;
	}

	@Override public int partsTotal() {
		return total;
	}

	@Override public boolean isTotalFinal() {
		return totalfinal;
	}

	/**
	** {@inheritDoc}
	**
	** This implementation does not give an estimate.
	*/
	@Override public int finalTotalEstimate() {
		return -1;
	}

	@Override public synchronized void join() throws InterruptedException, TaskAbortException {
		while (inprogress) { wait(); }
		if (abort != null) {
			throw abort;
		}
	}

}
