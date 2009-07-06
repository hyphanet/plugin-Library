package plugins.XMLLibrarian;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import freenet.support.Logger;
import java.util.Locale;
import java.util.Map;


/**
 * Performs asynchronous searches over many index or with many terms and search logic
 * @author MikeB
 */
public class Search implements Request<URIWrapper> {
	static private XMLLibrarian xl;

	/**
	 * What should be done with the results of subsearches
	 * TODO look into a complement set operation and phrase search, would need a different method for storing subsearches though
	 */
	public enum ResultOperation{INTERSECTION, UNION};
	private ResultOperation resultOperation;
	
	private HashSet<Request> subsearches;

	private String subject;
	private String query;
	private String indexURI;

	protected RequestStatus status;
	protected int stage=0;
	protected int stageCount;
	private long blocksCompleted;
	private long blocksTotal;
	protected Exception err;

	private static HashMap<String, Search> allsearches = new HashMap<String, Search>();


	/**
	 * Creates a search for any number of indices, starts and returns the associated Request object
	 *
	 * @param search string to be searched
	 * @param indexuri URI of index(s) to be used
	 * @throws InvalidSearchException if any part of the search is invalid
	 */
	public static Search startSearch(String search, String indexuri) throws InvalidSearchException{
		search = search.toLowerCase(Locale.US).trim();
		if(search.length()==0)
			throw new InvalidSearchException("Blank search");

		// See if the same search exists
		if (hasSearch(search, indexuri))
			return getSearch(search, indexuri);

		Logger.minor(Search.class, "Starting new search for "+search+" in "+indexuri);

		String[] indices = indexuri.split("[ ;]");
		String[] searchterms = search.split("[^\\p{L}\\{N}]+");
		if(indices.length<1 || searchterms.length<1)
			throw new InvalidSearchException("Attempt to start search with no index or terms");
		else if(indices.length==1 && searchterms.length==1)
			return new Search(search, indexuri, Index.getIndex(indices[0]).find(search));
		else if(indices.length==1 && searchterms.length>1){
			// Create search for multiple terms over 1 index
			ArrayList<Request> requests = new ArrayList(searchterms.length);
			for (String term : searchterms)
				requests.add(startSearch(term,indices[0]));
			return new Search(search, indexuri, requests, ResultOperation.INTERSECTION);
		}else{
			// create search for multiple terms over multiple indices
			ArrayList<Request> indexrequests = new ArrayList(indices.length);
			for (String index : indices){
				ArrayList<Request> termrequests = new ArrayList(searchterms.length);
				for (String term : searchterms)
					termrequests.add(startSearch(term, index));
				indexrequests.add(new Search(search, index, termrequests, ResultOperation.UNION));
			}
			return new Search(search, indexuri, indexrequests, ResultOperation.UNION);
		}
	}



	/**
	 * Creates Search instance depending on the given requests
	 *
	 * @param query the query this instance is being used for, only for reference
	 * @param indexURI the index uri this search is made on, only for reference
	 * @param requests subRequests of this search
	 * @param resultOperation Which set operation to do on the results of the subrequests
	 * @throws InvalidSearchException if the search is invalid
	 **/
	private Search(String query, String indexURI, List<Request> requests, ResultOperation resultoperation) throws InvalidSearchException{
		query = query.toLowerCase(Locale.US).trim();
		subsearches = new HashSet(requests);
		
		this.query = query;
		this.indexURI = indexURI;
		this.subject = makeString(query, indexURI);
		this.resultOperation = resultoperation;
		
		allsearches.put(subject, this);
		Logger.minor(this, "Created Search object for with subRequests :"+subsearches);
	}

	/**
	 * Encapsulate a request as a Search, only so original query and uri can be stored
	 *
	 * @param query the query this instance is being used for, only for reference
	 * @param indexURI the index uri this search is made on, only for reference
	 * @param request Request to encapsulate
	 */
	private Search(String query, String indexURI, Request request){
		query = query.toLowerCase(Locale.US).trim();
		subsearches = new HashSet();
		subsearches.add(request);

		this.query = query;
		this.indexURI = indexURI;
		this.subject = makeString(query, indexURI);
		allsearches.put(subject, this);
	}
	
	
//	/**
//	 * Splits query into multiple searches, will be used for advanced queries
//	 *
//	 * @return Set of subsearches or null if theres only one search
//	 */
//	private HashSet<Search> splitQuery(String query, Index index) throws InvalidSearchException{
//		String[] searchWords = query.split("[^\\p{L}\\{N}]+");
//		if (searchWords.length < 2)
//			return null; // Just one search term
//
//		HashSet<Search> searches = new HashSet<Search>(searchWords.length);
//		for ( String searchtoken : searchWords)
//			searches.add(startSearch(searchtoken, index));
//		return searches;
//	}


	/**
	 * Sets the parent plugin to be used for logging & plugin api
	 * @param xl Parent plugin
	 */
	public static void setup(XMLLibrarian xl){
		Search.xl = xl;
		Search.allsearches = new HashMap<String, Search>();
	}
	
	/**
	 * Gets a Search from the Map
	 * @param search
	 * @param indexuri
	 * @return Search or null if not found
	 */
	public static Search getSearch(String search, String indexuri){
		search = search.toLowerCase(Locale.US).trim();
        
		// See if the same search exists
		if (hasSearch(search, indexuri))
			return allsearches.get(makeString(search, indexuri));
		else
			return null;
	}

	/**
	 * Looks for a given search in the map of searches
	 * @param search
	 * @param indexuri
	 * @return true if it's found
	 */
	public static boolean hasSearch(String search, String indexuri){
		if(search==null || indexuri==null)
			return false;
		if(allsearches.containsKey(makeString(search, indexuri)))
			Logger.minor(allsearches.get(makeString(search, indexuri)), search);
		else
			Logger.minor(Search.class, makeString(search, indexuri)+" not found");
		return allsearches.containsKey(makeString(search, indexuri));
	}

	public static Map<String, Search> getAllSearches(){
		return allsearches;
	}

    public String getQuery(){
		return query;
	}

	public String getIndexURI(){
		return indexURI;
	}

	public static String makeString(String search, String indexuri){
		return search + "%" + indexuri;
	}

	@Override
	public String toString(){
		return "Search: "+subject+" : "+subsearches;
	}

	/**
	 * @return Set of Requests this search depends on
	 */
	public Set<Request> getSubRequests(){
		return subsearches;
	}

	/**
	 * @return true if progress has been read since it was last updated
	 */
	public boolean progressAccessed(){
		for(Request r : subsearches)
			if(!r.progressAccessed())
				return false;
		return true;
	}


	/**
	 * @return true if all are Finished, false otherwise
	 */
	public boolean isFinished(){
		for(Request r : subsearches)
			if(!r.isFinished())
				return false;
		return true;
	}
	/**
	 * @return true if any have result, false otherwise
	 */
	public boolean hasResult(){
		for(Request r : subsearches)
			if(r.hasResult())
				return true;
		return false;
	}
	/**
	 * @return a RequestStatus based on all subsearches in the following order
	 * ERROR if any have an ERROR<br />
	 * FINISHED if all are finished<br />
	 * PARTIALRESULT if any have result or partialresult<br />
	 * INPROGRESS otherwise
	 */
	public RequestStatus getRequestStatus(){
		if(getError()!=null)
			return RequestStatus.ERROR;
		else if(isFinished())
			return RequestStatus.FINISHED;
		else if(hasResult())
			return RequestStatus.PARTIALRESULT;
		else
			return RequestStatus.INPROGRESS;
	}

	public Exception getError() {
		for(Request r : subsearches)
			if(r.getError()!=null)
				return r.getError();
		return null;
	}

	/**
	 * @return sum of SubStages
	 */
	public int getSubStage(){
//		if(progressAccessed())
//			return stage;
		stage=0;
		for(Request r : subsearches)
			stage+=r.getSubStage();
		return stage;
	}

	/**
	 * @return sum of SubStageCount
	 */
	public int getSubStageCount() {
//		if(progressAccessed())
//			return stageCount;
		stageCount=0;
		for(Request r : subsearches)
			stageCount+=r.getSubStageCount();
		return stageCount;
	}

	/**
	 * Array of names of stages, length should be equal to the result of getSubStageCount()
	 * @return null if not used
	 */
	public String[] stageNames(){
		return null;
	}

	/**
	 * @return sum of NumBlocksCompleted
	 */
	public long getNumBlocksCompleted() {
//		if(progressAccessed())
//			return blocksCompleted;
		blocksCompleted=0;
		for(Request r : subsearches)
			blocksCompleted+=r.getNumBlocksCompleted();
		return blocksCompleted;
	}

	/**
	 * @return sum of NumBlocksTotal
	 * TODO record all these progress things to speed up
	 */
	public long getNumBlocksTotal() {
//		if(progressAccessed())
//			return blocksTotal;
		blocksTotal=0;
		for(Request r : subsearches)
			blocksTotal+=r.getNumBlocksTotal();
		return blocksTotal;
	}

	/**
	 * @return true if all subsearches numblocks are final
	 */
	public boolean isNumBlocksCompletedFinal() {
		for(Request r : subsearches)
			if(!r.isNumBlocksCompletedFinal())
				return false;
		return true;
	}

	public String getSubject() {
		return subject;
	}

	public int compareTo(Request right) {
		return this.getSubject().compareTo(right.getSubject());
	}

	/**
	 * Perform an intersection on results of all subsearches and return <br />
	 * @return Set of URIWrappers
	 */
	public Set<URIWrapper> getResult() {
		if(getRequestStatus() != Request.RequestStatus.FINISHED)
			return null;

		TreeSet<URIWrapper> result = new TreeSet<URIWrapper>();
		for(Request<URIWrapper> r : subsearches)
			if(r.hasResult())
				if(result.size()>0)
					switch(resultOperation){
						case UNION:
							result.addAll(r.getResult());
						case INTERSECTION:
							result.retainAll(r.getResult());
					}
				else
					result.addAll(r.getResult());
		allsearches.remove(subject);
		return result;
	}
}
