/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index.xml;

import freenet.client.events.ClientEvent;
import freenet.client.events.SplitfileProgressEvent;
import freenet.support.Logger;
import java.util.ArrayList;
import plugins.Library.index.AbstractRequest;
import plugins.Library.index.ChainedRequest;
import plugins.Library.index.Request;
import plugins.Library.serial.Progress;
import plugins.Library.serial.ProgressParts;
import plugins.Library.serial.TaskAbortException;


/**
 * A Request implementation for xmlindex operations which have distinct parts working in serial
 * eg a search for 1 term on 1 index. Only used for XMLIndex
 * @author MikeB
 * @param <E> Return type
 */
public class FindRequest<E> extends AbstractRequest<E> implements Comparable<Request>, ChainedRequest<E> {
	private E resultnotfinished;
	private SubProgress currentProgress;

	public enum Stages {
		UNSTARTED("Not Started"),
		FETCHROOT("Fetching Index Root"),
		FETCHSUBINDEX("Fetching Subindex"),
		PARSE("Parsing Subindex"),
		DONE("Done");

		public final String description;
		Stages(String desc){
			this.description = desc;
		}
	};

	/**
	 * Create Request of stated type & subject
	 * @param subject
	 */
	public FindRequest(String subject) {
		super(subject);
		currentProgress = new SubProgress(Stages.UNSTARTED);
	}

	/**
	 * Log Exception for this request
	 */
	@Override
	public void setError(TaskAbortException e) {
		super.setError(e);
	}

	/**
	 * Stores an unfinished result, call setFinished() to mark as complete
	 * @param result
	 */
	@Override
	public void setResult(E result){
		resultnotfinished = result;
	}

	/**
	 * Moves the unfinished result so that the Progress is marked as finsihed
	 */
	public void setFinished(){
		super.setResult(resultnotfinished);
		resultnotfinished = null;
		setStage(Stages.DONE);
	}

	public int compareTo(Request right){
		return subject.compareTo(right.getSubject());
	}

	@Override
	public ProgressParts getParts() throws TaskAbortException {
		int stage = currentProgress.stage.ordinal();
		int end = Stages.DONE.ordinal();
		return ProgressParts.normalise(stage, (stage<end-1)?stage+1:end, end, ProgressParts.TOTAL_FINALIZED);
	}

	@Override
	public Progress getCurrentProgress() {
		return currentProgress;
	}

	@Override
	public String getStatus() {
		return getCurrentProgress().getSubject();
	}

	@Override
	public boolean isDone(){
		return currentProgress.stage==Stages.DONE;
	}

	/**
	 * Returns the result which hasnt been marked as completed
	 * @return
	 */
	E getUnfinishedResult() {
		return resultnotfinished;
	}

	/**
	 * Set the stage number, between 0 & 4 inclusive TODO : now stage is not used in Progress make this an enum
	 * @param i
	 */
	void setStage(Stages stage) {
		currentProgress= new SubProgress(stage);
	}

	/**
	 * Update the ProgressParts of the currentProgress of all the FindRequests in the List with the contents of the ClientEvent
	 * @param receivingEvent
	 * @param ce
	 */
	static void updateWithEvent(ArrayList<FindRequest> receivingEvent, ClientEvent ce) {
		for (FindRequest findRequest : receivingEvent) {
			findRequest.partsFromEvent(ce);
		}
	}

	/**
	 * Update the ProgressParts of the currentProgress with the ClientEvent
	 * @param ce
	 */
	private void partsFromEvent(ClientEvent ce) {
		currentProgress.partsFromEvent(ce);
	}

	@Override
	public String toString() {
		try {
			return "FindRequest: " + getStatus() + " " + getParts() + " - "+currentProgress;
		} catch (TaskAbortException ex) {
			return "Error forming FindRequest string";
		}
	}

	/**
	 * The FindRequest is a chained request so it has
	 */
	class SubProgress implements Progress {	// TODO finish this
		private ProgressParts parts = ProgressParts.normalise(0, 1, 2, -1);
		boolean done = false;
		public final Stages stage;

		/**
		 * Constructs a SubProgress for specified stage with a default unstarted ProgressParts
		 * @param stage
		 */
		public SubProgress (Stages stage){
			this.stage = stage;
			parts = ProgressParts.normalise(0, 0, 1, ProgressParts.ESTIMATE_UNKNOWN);
		}

		public String getSubject() {
			return stage.description;
		}

		public String getStatus() {
			return stage.description;
		}

		public ProgressParts getParts() throws TaskAbortException {
			return parts;
		}

		public boolean isDone() throws TaskAbortException {
			return parts.isDone();
		}

		/**
		 * Not used
		 * @throws java.lang.InterruptedException
		 */
		public void join() throws InterruptedException, TaskAbortException {
			throw new UnsupportedOperationException("Not supported.");
		}

		/**
		 * Sets the Progress of this to the contents of the CLientEvent
		 * @param ce
		 */
		public void partsFromEvent(ClientEvent ce) {
//			Logger.normal(this, "Setting parts in "+this+" from event "+ce.getClass());
			if(ce instanceof SplitfileProgressEvent){
				SplitfileProgressEvent spe = (SplitfileProgressEvent)ce;
				parts = ProgressParts.normalise(spe.succeedBlocks, spe.minSuccessfulBlocks, spe.minSuccessfulBlocks, spe.finalizedTotal?ProgressParts.TOTAL_FINALIZED:ProgressParts.ESTIMATE_UNKNOWN);
			}else
				throw new UnsupportedOperationException("Not supported yet. This ClientEvent hasn't been identified as being needed yet : "+ce.getClass().getName());	// Haven't come across any other ClientEvents arriving here yet
		}

		@Override
		public String toString(){
			return parts.toString();
		}
	}
}
