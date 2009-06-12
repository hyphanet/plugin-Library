package plugins.XMLLibrarian;


class Request{
	public enum RequestType { FIND, PAGE, SEARCH };
	public enum Status { UNSTARTED, INPROGRESS, PARTIALRESULT, FINISHED };
	
	protected RequestType type;
	protected String subject;
	protected Status status;
	
	
	public Request(RequestType type, String subject){
		status = Status.UNSTARTED;
		this.type = type;
		this.subject = subject;
	}
	
	public Status getStatus(){
		return status;
	}
}
