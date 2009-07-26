/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index.xml;

import java.util.List;
import plugins.Library.search.AbstractRequest;
import plugins.Library.search.Request;


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
	private long blocksCompleted;
	private long blocksTotal;
	private boolean blocksfinalized;
	private int expectedsize;
	E result;


	/**
	 * Create Request of stated type & subject
	 * @param subject
	 */
	public FindRequest(String subject){
		super(subject);
	}


	/**
	 * @return SubStage number between 1 and SubStageCount, for when overall operation length is not known but number of stages is
	 */
	public int getSubStage(){
		return stage;
	}
	/**
	 * @return the number of substages expected in this request
	 */
	public int getSubStageCount(){
		return stageNames.length;
	}

	/**
	 * Array of names of stages, length should be equal to the result of getSubStageCount()
	 * @return null if not used
	 */
	public String[] stageNames(){
		return stageNames;
	}

	/**
	 * @return blocks completed in SubStage
	 */
	public long getNumBlocksCompleted(){
		return blocksCompleted;
	}
	public long getNumBlocksTotal(){
		return blocksTotal;
	}
	/**
	 * TODO set finalized flag from eventDescription
	 * @return true if NumBlocksTotal is known to be final
	 */
	public boolean isNumBlocksCompletedFinal(){
		return blocksfinalized;
	}

	/**
	 * @return subject of this request
	 */
	public String getSubject(){
		return subject;
	}

	/**
	 * @return result of this request
	 */
	public E getResult(){
		return result;
	}

	/**
	 * @return true if progress hasn't changed since it was last read
	 * TODO implement access reporting properly
	 */
	public boolean progressAccessed(){
		return false;
	}

	/**
	 * Log Exception for this request, marks status as ERROR
	 */
	public void setError(Exception e) {
		err = e;
		status = RequestStatus.ERROR;
	}

	/**
	 * Sets the current status to a particular RequestStatus and stage number
	 * @param status
	 * @param stage
	 */
	public void setStage(RequestStatus status, int stage){
		this.status = status;
		this.stage = stage;
	}

	/**
	 * Stores a result and marks requestStatus as PARTIALRESULT, call setFinished to mark FINISHED
	 * @param result
	 */
	public void setResult(E result){
		status = RequestStatus.PARTIALRESULT;
		this.result = result;
	}

	/**
	 * Mark Request as FINISHED
	 */
	public void setFinished(){
		status=RequestStatus.FINISHED;
	}


	public int compareTo(Request right){
		return subject.compareTo(right.getSubject());
	}

	@Override
	public String toString(){
		return "Request subject="+subject+" status="+status.toString()+" stage="+stage+" progress="+blocksCompleted;
	}


	/**
	 * Updates Request with progress from event
	 * @param downloadProgress
	 * @param downloadSize
	 */
	private void updateWithEvent(long downloadProgress, long downloadSize, boolean finalized) {
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
		long downloadProgress = Integer.parseInt(download.split("/")[0]);
		long downloadSize = Integer.parseInt(download.split("/")[1]);
		boolean finalized = eventDescription.contains("(finalized total)");
		for (FindRequest request : requests)
			request.updateWithEvent(downloadProgress, downloadSize, finalized);
	}

	/**
	 * @return null, FindRequest is atomic
	 */
	public List<Request> getSubRequests() {
		return null;
	}
}
