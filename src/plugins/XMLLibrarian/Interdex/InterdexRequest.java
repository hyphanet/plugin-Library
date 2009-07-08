package plugins.XMLLibrarian.Interdex;

import java.util.Set;
import plugins.Interdex.index.Index;
import plugins.Interdex.index.Token;
import plugins.Interdex.util.DataNotLoadedException;
import plugins.XMLLibrarian.Request;


public class InterdexRequest implements Request<Set> {
	Token token;
	Set result;

	public InterdexRequest(Index index, String term){
		token = new Token(term);
		try{
			// Try to get from index
			result = index.getAllEntries(token);
		}catch(DataNotLoadedException e){
			// Dont know if anything needs to be done with the Exception like get the Dummy
			// Get a Serialiser
			// Ask Serialiser to get token
		}
	}

	public RequestStatus getRequestStatus() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public boolean isFinished() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	// Shouldn't need this for thuis implemnetation
	public Exception getError() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public int getSubStage() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public int getSubStageCount() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public long getNumBlocksCompleted() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public long getNumBlocksTotal() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public boolean isNumBlocksCompletedFinal() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public String getSubject() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public Set getResult() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public boolean hasResult() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public Set getSubRequests() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public boolean progressAccessed() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public int compareTo(Request right) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

}
