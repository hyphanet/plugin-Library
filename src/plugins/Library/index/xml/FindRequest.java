/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index.xml;

import freenet.client.events.ClientEvent;
import freenet.client.events.SplitfileProgressEvent;
import java.util.ArrayList;
import java.util.Set;
import plugins.Library.index.AbstractRequest;
import plugins.Library.index.Request;
import plugins.Library.index.TermPageEntry;
import plugins.Library.serial.ChainedProgress;
import plugins.Library.serial.Progress;
import plugins.Library.serial.ProgressParts;
import plugins.Library.serial.TaskAbortException;


/**
 * A Request implementation for operations which aren't split into smaller parts,
 * eg a search for 1 term on 1 index. Only used for XMLIndex
 * TODO some of this stuff could go in an AbstractRequest class as it's common
 * @author MikeB
 * @param <E> Return type
 */
public class FindRequest<E> extends AbstractRequest<E> implements Comparable<Request>, Request<E>, ChainedProgress {
	protected int stage=0;
	protected final String[] stageNames = new String[]{
		"Nothing",
		"Fetching Index Root",
		"Fetching Subindex",
		"Parsing Subindex"
	};
	private ArrayList<SubProgress> subProgresses;
	private E resultnotfinished;

	/**
	 * Create Request of stated type & subject
	 * @param subject
	 * @param positions, should we look for positions?
	 */
	public FindRequest(String subject) {
		super(subject);
		subProgresses = new ArrayList<SubProgress>(3);
	}

	/**
	 * Log Exception for this request, marks status as ERROR
	 */
	@Override
	public void setError(TaskAbortException e) {
		super.setError(e);
	}

	/**
	 * Stores a result and marks RequestState as PARTIALRESULT, call setFinished to mark FINISHED
	 * @param result
	 */
	@Override
	public void setResult(E result){
		resultnotfinished = result;
	}

	public void setFinished(){
		super.setResult(resultnotfinished);
		resultnotfinished = null;
	}

	public int compareTo(Request right){
		return subject.compareTo(right.getSubject());
	}

	@Override
	public ProgressParts getParts() throws TaskAbortException {
		return ProgressParts.getParts(subProgresses, 3);
	}

	@Override
	public Progress getCurrentProgress() {
		return subProgresses.get(stage);
	}

	@Override
	public String getStatus() {
		return stageNames[stage];
	}

	E getUnfinishedResult() {
		return resultnotfinished;
	}

	void setStage(int i) {
		if(i < 3 && i >= 0){
			stage = i;
		}else
			throw new IndexOutOfBoundsException("Valid stages for a FindRequest are 0 to 2 inclusive not " + i);
	}

	static void updateWithEvent(ArrayList<FindRequest> waitingOnMainIndex, ClientEvent ce) {
		for (FindRequest findRequest : waitingOnMainIndex) {
			findRequest.partsFromEvent(ce);
		}
	}

	private void partsFromEvent(ClientEvent ce) {
		subProgresses.get(stage).partsFromEvent(ce);
	}


	class SubProgress implements Progress {	// TODO finish this
		private ProgressParts parts;

		public String getSubject() {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		public String getStatus() {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		public ProgressParts getParts() throws TaskAbortException {
			return parts;
		}

		public boolean isDone() throws TaskAbortException {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		public void join() throws InterruptedException, TaskAbortException {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		public void partsFromEvent(ClientEvent ce) {
			if(ce instanceof SplitfileProgressEvent){
				SplitfileProgressEvent spe = (SplitfileProgressEvent)ce;
				parts = ProgressParts.normalise(spe.succeedBlocks, spe.totalBlocks);
			}else
				throw new UnsupportedOperationException("Not supported yet. : "+ce.getClass().getName());
		}
	}
}
