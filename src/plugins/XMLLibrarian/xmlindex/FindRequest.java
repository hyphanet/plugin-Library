package plugins.XMLLibrarian.xmlindex;

import freenet.support.Logger;
import java.util.List;
import java.util.Set;
import plugins.XMLLibrarian.Request;


/**
 * A Request implementation for operations which aren't split into smaller parts,
 * eg a search for 1 term on 1 index
 * @author MikeB
 * @param <E> Return type
 */
public class FindRequest<E> implements Comparable<Request>, Request<E>{
	protected String subject;
	
	protected RequestStatus status;
	protected int stage=0;
	protected int stageCount;
	private long blocksCompleted;
	private long blocksTotal;
	private int expectedsize;
	protected Exception err;
	private String eventDescription;
	Set<E> result;
	private boolean resultChanged=true;
	
	
	/**
	 * Create Request of stated type & subject
	 * @param subject
	 */
	public FindRequest(String subject){
		status = RequestStatus.UNSTARTED;
		this.subject = subject;
	}

	/**
	 * @return  UNSTARTED, INPROGRESS, PARTIALRESULT, FINISHED, ERROR
	 */
	public RequestStatus getRequestStatus(){
		return status;
	}
	/**
	 * @return true if RequestStatus is FINISHED or ERROR
	 */
	public boolean isFinished(){
		return status==RequestStatus.FINISHED || status == RequestStatus.ERROR;
	}
	/**
	 * @return an error found in this operation
	 */
	public Exception getError(){
		return err;
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
		return stageCount;
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
		return false;
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
	public Set<E> getResult(){
		resultChanged = false;
		return result;
	}
	/**
	 * @return true if RequestStatus is PARTIALRESULT or FINISHED
	 */
	public boolean hasResult(){
		return status==RequestStatus.FINISHED||status==RequestStatus.PARTIALRESULT;
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
	public void setStage(RequestStatus status, int stage, int stagecount){
		this.status = status;
		this.stage = stage;
		this.stageCount=stagecount;
	}

	/**
	 * Stores a result and marks requestStatus as PARTIALRESULT, call setFinished to mark FINISHED
	 * @param result
	 */
	public void setResult(Set<E> result){
		status = RequestStatus.PARTIALRESULT;
		this.result = result;
	}

	public void addResult(E entry){
		status = RequestStatus.PARTIALRESULT;
		resultChanged = true;
		result.add(entry);
	}

	public boolean resultChanged(){
		return resultChanged;
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
		return "Request subject="+subject+" status="+status.toString()+" event="+eventDescription+" stage="+stage+" progress="+blocksCompleted;
	}


	/**
	 * Updates Request with progress from event
	 * @param downloadProgress
	 * @param downloadSize
	 */
	private void updateWithEvent(long downloadProgress, long downloadSize) {
		blocksCompleted = downloadProgress;
		blocksTotal = downloadSize;
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
		for (FindRequest request : requests)
			request.updateWithEvent(downloadProgress, downloadSize);
		Logger.normal(FindRequest.class, "updated requests with progress");
	}


	public String getEventDescription() {
		return eventDescription;
	}

	/**
	 * @return null, FindRequest is atomic
	 */
	public Set<Request> getSubRequests() {
		return null;
	}
}
