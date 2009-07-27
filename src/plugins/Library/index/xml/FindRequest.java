/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index.xml;

import java.util.List;
import plugins.Library.index.AbstractRequest;
import plugins.Library.index.Request.RequestState;
import plugins.Library.index.Request;
import plugins.Library.serial.TaskAbortException;


/**
 * A Request implementation for operations which aren't split into smaller parts,
 * eg a search for 1 term on 1 index. Only used for XMLIndex
 * TODO some of this stuff could go in an AbstractRequest class as it's common
 * @author MikeB
 * @param <E> Return type
 */
public class FindRequest<E> extends AbstractRequest<E> implements Comparable<Request>, Request<E> {
	protected int stage=0;
	protected final String[] stageNames = new String[]{
		"Nothing",
		"Fetching Index Root",
		"Fetching Subindex",
		"Parsing Subindex"
	};
	private int blocksCompleted;
	private int blocksTotal;
	private boolean blocksfinalized;
	private int expectedsize;

	/**
	 * Create Request of stated type & subject
	 * @param subject
	 */
	public FindRequest(String subject){
		super(subject);
	}


	/**
	 * @return blocks completed in SubStage
	 */
	@Override public int partsDone(){
		return blocksCompleted;
	}

	@Override public int partsTotal(){
		return blocksTotal;
	}

	/**
	 * TODO set finalized flag from eventDescription
	 * @return true if NumBlocksTotal is known to be final
	 */
	@Override public boolean isTotalFinal(){
		return blocksfinalized;
	}



	@Override public String getCurrentStage() {
		return stageNames[stage];
	}

	@Override public String getCurrentStatus() {
		// PRIORITY
		// not sure if this is what it's supposed to do...
		// getStatus is supposed to be the progress of the whole operation
		// getCurrentStatus is supposed to be the progress of the current stage
		return getStatus();
	}

	/**
	 * @return null, FindRequest is atomic
	 */
	@Override public List<Request> getSubRequests() {
		return null;
	}

	/**
	 * Log Exception for this request, marks status as ERROR
	 */
	public void setError(TaskAbortException e) {
		error = e;
		state = RequestState.ERROR;
	}

	/**
	 * Sets the current status to a particular RequestState and stage number
	 * @param state
	 * @param stage
	 */
	public void setStage(RequestState state, int stage){
		this.state = state;
		this.stage = stage;
	}

	/**
	 * Stores a result and marks RequestState as PARTIALRESULT, call setFinished to mark FINISHED
	 * @param result
	 */
	public void setResult(E result){
		state = RequestState.PARTIALRESULT;
		this.result = result;
	}

	/**
	 * Mark Request as FINISHED
	 */
	public void setFinished(){
		state = RequestState.FINISHED;
	}


	public int compareTo(Request right){
		return subject.compareTo(right.getSubject());
	}

	@Override
	public String toString(){
		return "Request subject="+subject+" status="+state.toString()+" stage="+stage+" progress="+blocksCompleted;
	}


	/**
	 * Updates Request with progress from event
	 * @param downloadProgress
	 * @param downloadSize
	 */
	private void updateWithEvent(int downloadProgress, int downloadSize, boolean finalized) {
		blocksCompleted = downloadProgress;
		blocksTotal = downloadSize;
		blocksfinalized = finalized;
	}

	/**
	 * Updates a list of requests with download progress from an event description
	 * TODO add more variables from parsing
	 * @param requests List of Requests to be updated with this event
	 * @param eventDescription Event.getDescription() for this event to be parsed
	 */
	public static void updateWithDescription(List<FindRequest> requests, String eventDescription){
		//for(FindRequest request : requests)
		//	request.eventDescription = eventDescription;
		if(eventDescription.split(" ")[1].equals("MIME"))
			return;
		if(eventDescription.split(" ")[1].equals("file")){
			for(FindRequest request : requests)
				request.expectedsize = Integer.parseInt(eventDescription.split(" ")[3]);
			return;
		}

		String download = eventDescription.split(" ")[2];
		int downloadProgress = Integer.parseInt(download.split("/")[0]);
		int downloadSize = Integer.parseInt(download.split("/")[1]);
		boolean finalized = eventDescription.contains("(finalized total)");
		for (FindRequest request : requests)
			request.updateWithEvent(downloadProgress, downloadSize, finalized);
	}

}
