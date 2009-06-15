package plugins.XMLLibrarian;

import freenet.client.events.ClientEventListener;
import freenet.client.events.ClientEvent;
import com.db4o.ObjectContainer;

import freenet.client.async.ClientContext;


class Request<E> implements Comparable<Request>{
	public enum RequestType { FIND, PAGE, SEARCH };
	public enum RequestStatus { UNSTARTED, INPROGRESS, PARTIALRESULT, FINISHED };
	
	protected RequestType type;
	protected String subject;
	protected int stage=0;
	protected RequestStatus status;
	protected Status statusgiver;
	E result;
	
	
	public Request(RequestType type, String subject){
		status = RequestStatus.UNSTARTED;
		this.type = type;
		this.subject = subject;
	}
	
	public void setStage(RequestStatus status, int stage, Status statusgiver){
		this.status = status;
		this.stage = stage;
		this.statusgiver = statusgiver;
	}
	
	public Status getStatus(){
		return statusgiver;
	}
	
	public String getSubject(){
		return subject;
	}
	
	public void setResult(E result){
		status = RequestStatus.PARTIALRESULT;
		this.result = result;
	}
	
	public E getResult(){
		return result;
	}
	
	public void finished(){
		status=RequestStatus.FINISHED;
	}
	
	
	public int compareTo(Request right){
		return 1024 * status.compareTo(right.status) + subject.compareTo(right.subject);
	}
	
	public String toString(){
		return "Request subject="+subject+" description="+status.toString()+" stage="+stage+" progress="+statusgiver.getDownloadedBlocks();
	}
}
