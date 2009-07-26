
package plugins.Library.search;

import java.util.List;

/**
 *
 * @author MikeB
 */
public abstract class AbstractRequest<E> implements Request<E> {
	protected RequestStatus status = RequestStatus.UNSTARTED;
	protected Exception err;
	protected String subject;



	/**
	 * Create Request of stated type & subject
	 * @param subject
	 */
	public AbstractRequest(String subject){
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
	 * @return true if RequestStatus is PARTIALRESULT or FINISHED
	 */
	public boolean hasResult(){
		return status==RequestStatus.FINISHED||status==RequestStatus.PARTIALRESULT;
	}

	public List getSubRequests() {
		return null;
	}

}
