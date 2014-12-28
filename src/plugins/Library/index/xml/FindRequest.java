/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index.xml;

import freenet.client.events.ClientEvent;
import freenet.client.events.ExpectedFileSizeEvent;
import freenet.client.events.ExpectedHashesEvent;
import freenet.client.events.ExpectedMIMEEvent;
import freenet.client.events.SendingToNetworkEvent;
import freenet.client.events.SplitfileCompatibilityModeEvent;
import freenet.client.events.SplitfileProgressEvent;
import freenet.library.util.exec.AbstractExecution;
import freenet.library.util.exec.ChainedProgress;
import freenet.library.util.exec.Execution;
import freenet.library.util.exec.Progress;
import freenet.library.util.exec.ProgressParts;
import freenet.library.util.exec.TaskAbortException;
import freenet.support.Logger;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Set;

import plugins.Library.index.TermPageEntry;
import plugins.Library.index.TermEntry;


/**
 * An {@link Execution} implementation for xmlindex operations which have distinct parts working in serial
 * eg a search for 1 term on 1 index. Only used for XMLIndex
 * @author MikeB
 */
public class FindRequest extends AbstractExecution<Set<TermEntry>> implements Comparable<Execution>, ChainedProgress, Execution<Set<TermEntry>> {
	private Set<TermPageEntry> resultnotfinished;
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
	//@Override
	public void setResult(Set<TermPageEntry> result){
		resultnotfinished = result;
	}

	/**
	 * Moves the unfinished result so that the Progress is marked as finsihed
	 * TODO get rid of this way of doing it
	 */
	public void setFinished(){
		super.setResult(Collections.<TermEntry>unmodifiableSet(resultnotfinished));
		resultnotfinished = null;
		setStage(Stages.DONE);
	}

	public int compareTo(Execution right){
		return subject.compareTo(right.getSubject());
	}

	@Override
	public ProgressParts getParts() throws TaskAbortException {
		int stage = currentProgress.stage.ordinal();
		int end = Stages.DONE.ordinal();
		return ProgressParts.normalise(stage, (stage<end-1)?stage+1:end, end, ProgressParts.TOTAL_FINALIZED);
	}

	public Progress getCurrentProgress() {
		return currentProgress;
	}

	@Override
	public String getStatus() {
		return getCurrentProgress().getSubject();
	}

	@Override
	public boolean isDone() throws TaskAbortException{
		super.isDone();
		return currentProgress.stage==Stages.DONE;
	}

	/**
	 * Returns the result which hasnt been marked as completed
	 */
	Set<TermPageEntry> getUnfinishedResult() {
		return resultnotfinished;
	}

	/**
	 * Set the stage number, between 0 & 4 inclusive TODO : now stage is not used in Progress make this an enum
	 * @param stage The stage number to set
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

		public boolean isStarted() {
			return true;
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
			}else if(ce instanceof SendingToNetworkEvent || ce instanceof ExpectedMIMEEvent || ce instanceof ExpectedFileSizeEvent || ce instanceof SplitfileCompatibilityModeEvent || ce instanceof ExpectedHashesEvent) {
				// Ignore.
				return;
			}else{
				parts = ProgressParts.normalise(0, 0);
				Logger.error(this, "Fetch progress will not update due to unrecognised ClientEvent : "+ce.getClass().getName());
			}
		}

		@Override
		public String toString(){
			return parts.toString();
		}
	}
}
