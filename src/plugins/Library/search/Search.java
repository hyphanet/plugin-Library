/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search;

import plugins.Library.Library;
import plugins.Library.index.Request.RequestState;
import plugins.Library.index.Request;
import plugins.Library.index.AbstractRequest;
import plugins.Library.index.URIWrapper;
import plugins.Library.search.InvalidSearchException;
import plugins.Library.serial.TaskAbortException;

import freenet.support.Logger;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Performs asynchronous searches over many index or with many terms and search logic
 * @author MikeB
 */
public class Search extends AbstractRequest<Set<URIWrapper>>
implements Request<Set<URIWrapper>> {

	/**
	 * What should be done with the results of subsearches\n
	 * INTERSECTION : result is common results of subsearches\n
	 * UNION : result is all results of subsearches\n
	 * REMOVE : (only 2 subsearches alowd) result is the results of the first subsearch with the second search removed
	 */
	public enum ResultOperation{INTERSECTION, UNION, REMOVE, PHRASE};
	private ResultOperation resultOperation;

	private ArrayList<Request> subsearches;

	private String query;
	private String indexURI;

	protected int stage=0;
	protected int stageCount;
	private int blocksCompleted;
	private int blocksTotal;

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
		if(indices.length<1 || search.trim().length()<1)
			throw new InvalidSearchException("Attempt to start search with no index or terms");
		else if(indices.length==1)
			return splitQuery(search, indexuri);
		else{
			// create search for multiple terms over multiple indices
			ArrayList<Request> indexrequests = new ArrayList(indices.length);
			for (String index : indices)
				indexrequests.add(startSearch(search, index));
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
	private Search(String query, String indexURI, List<Request> requests, ResultOperation resultOperation)
	throws InvalidSearchException{
		super(makeString(query, indexURI));
		if(resultOperation==ResultOperation.REMOVE && requests.size()!=2)
			throw new InvalidSearchException("Negative operations can only have 2 parameters");
		if(resultOperation==ResultOperation.PHRASE && requests.size()<=2)
			throw new InvalidSearchException("Phrase operations need more than one term");
		query = query.toLowerCase(Locale.US).trim();
		subsearches = new ArrayList(requests);

		this.query = query;
		this.indexURI = indexURI;
		this.resultOperation = resultOperation;

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
		super(makeString(query, indexURI));
		query = query.toLowerCase(Locale.US).trim();
		subsearches = new ArrayList();
		subsearches.add(request);

		this.query = query;
		this.indexURI = indexURI;
		this.resultOperation = ResultOperation.UNION;
		allsearches.put(subject, this);
	}


	/**
	 * Splits query into multiple searches, will be used for advanced queries
	 * @param query search query, can use various different search conventions
	 * @param indexuri uri for one index
	 * @return Set of subsearches or null if theres only one search
	 */
	private static Search splitQuery(String query, String indexuri) throws InvalidSearchException{
		if(query.matches("\\A\\w*\\Z"))
			// single search term
			return new Search(query, indexuri, Library.getIndex(indexuri).getTermEntries(query));

		// Make phrase search
		if(query.matches("\\A\".*\"\\Z")){
			ArrayList<Request> phrasesearches = new ArrayList();
			String[] phrase = query.replaceAll("\"(.*)\"", "$1").split(" ");
			Logger.minor(Search.class, "Phrase split"+query);
			for (String subquery : phrase)
				phrasesearches.add(splitQuery(subquery, indexuri));
			return new Search(query, indexuri, phrasesearches, ResultOperation.PHRASE);
		}

		Logger.minor(Search.class, "Splitting " + query);
		String formattedquery="";
		// Remove phrases, place them in arraylist and replace tem with references to the arraylist
		ArrayList<String> phrases = new ArrayList();
		String[] phraseparts = query.split("\"");
		if(phraseparts.length>1)
			for (int i = 0; i < phraseparts.length; i++) {
				String string = phraseparts[i];
				formattedquery+=string;
				if (++i < phraseparts.length){
					string = phraseparts[i];
					formattedquery+="$"+phrases.size();
					phrases.add(string);
				}
			}
		else
			formattedquery=query;
		Logger.minor(Search.class, "phrases removed query : "+formattedquery);
		formattedquery = formattedquery.replaceAll("\\s+or\\s+", "||");
		formattedquery = formattedquery.replaceAll("\\s+(?:not\\s*|-)(\\S+)", "^^($1)");
		Logger.minor(Search.class, "not query : "+formattedquery);
		formattedquery = formattedquery.replaceAll("\\s+", "&&");
		Logger.minor(Search.class, "and query : "+formattedquery);

		// Put phrases back in
		phraseparts=formattedquery.split("\\$");
		formattedquery=phraseparts[0];
		for (int i = 1; i < phraseparts.length; i++) {
			String string = phraseparts[i];
			Logger.minor(Search.class, "replacing phrase "+string.replaceFirst("(\\d+).*", "$1"));
			formattedquery += "\""+ phrases.get(Integer.parseInt(string.replaceFirst("(\\d+).*", "$1"))) +"\"" + string.replaceFirst("\\d+(.*)", "$1");
		}
		Logger.minor(Search.class, "phrase back query : "+formattedquery);

		// Make complement search
		if (formattedquery.contains("^^(")){
			ArrayList<Request> complementsearches = new ArrayList();
			String[] splitup = formattedquery.split("(\\^\\^\\(|\\))", 3);
			complementsearches.add(splitQuery(splitup[0]+splitup[2], indexuri));
			complementsearches.add(splitQuery(splitup[1], indexuri));
			return new Search(query, indexuri, complementsearches, ResultOperation.REMOVE);
		}
		// Split intersections
		if (formattedquery.contains("&&")){
			ArrayList<Request> intersectsearches = new ArrayList();
			String[] intersects = formattedquery.split("&&");
			for (String subquery : intersects)
				intersectsearches.add(splitQuery(subquery, indexuri));
			return new Search(query, indexuri, intersectsearches, ResultOperation.INTERSECTION);
		}
		// Split Unions
		if (formattedquery.contains("||")){
			ArrayList<Request> unionsearches = new ArrayList();
			String[] unions = formattedquery.split("\\|\\|");
			for (String subquery : unions)
				unionsearches.add(splitQuery(subquery, indexuri));
			return new Search(query, indexuri, unionsearches, ResultOperation.UNION);
		}

		Logger.error(Search.class, "No split made, "+formattedquery+query);
		return null;
	}


	/**
	 * Sets the parent plugin to be used for logging & plugin api
	 */
	public static void setup(){
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
		return "Search: "+resultOperation+" : "+subject+" : "+subsearches;
	}

	/**
	 * @return List of Requests this search depends on
	 */
	@Override public List<Request> getSubRequests(){
		return subsearches;
	}


	/**
	 * @return true if all are Finished, false otherwise
	 */
	@Override public boolean isDone(){
		for(Request r : subsearches)
			if(!r.isDone())
				return false;
		return true;
	}
	/**
	 * @return true if any have result, false otherwise
	 */
	protected boolean hasResult(){
		for(Request r : subsearches) {
			switch (r.getState()) {
			case FINISHED:
			case PARTIALRESULT:
				return true;
			default:
			}
		}
		return false;
	}

	/**
	 * @return a RequestState based on all subsearches in the following order
	 * ERROR if any have an ERROR<br />
	 * FINISHED if all are finished<br />
	 * PARTIALRESULT if any have result or partialresult<br />
	 * INPROGRESS otherwise
	 */
	@Override public RequestState getState(){
		if (getError()!=null)
			return RequestState.ERROR;
		else if (isDone())
			return RequestState.FINISHED;
		else if (hasResult())
			return RequestState.PARTIALRESULT;
		else
			return RequestState.INPROGRESS;
	}

	@Override public Exception getError() {
		for(Request r : subsearches)
			if(r.getError()!=null)
				return r.getError();
		return null;
	}

/*
	PRIORITY temporarily disabled for now
	protected int getSubStage(){
//		if(progressAccessed())
//			return stage;
		stage=0;
		for(Request r : subsearches)
			stage+=r.getSubStage();
		return stage;
	}

	protected int getSubStageCount() {
//		if(progressAccessed())
//			return stageCount;
		stageCount=0;
		for(Request r : subsearches)
			stageCount+=r.getSubStageCount();
		return stageCount;
	}
*/

	/**
	 * Array of names of stages, length should be equal to the result of getSubStageCount()
	 * @return null if not used
	 */
	@Override public String getCurrentStage(){
		//return "Stage: "+request.getSubStage()+"/"+request.getSubStageCount();
		return "Stage: ???/???";
	}

	@Override public String getCurrentStatus() {
		throw new UnsupportedOperationException("not implemented");
	}

	/**
	 * @return sum of NumBlocksCompleted
	 */
	@Override public int partsDone() {
//		if(progressAccessed())
//			return blocksCompleted;
		blocksCompleted=0;
		for(Request r : subsearches)
			blocksCompleted+=r.partsDone();
		return blocksCompleted;
	}

	/**
	 * @return sum of NumBlocksTotal
	 * TODO record all these progress things to speed up
	 */
	@Override public int partsTotal() {
//		if(progressAccessed())
//			return blocksTotal;
		blocksTotal=0;
		for(Request r : subsearches)
			blocksTotal+=r.partsTotal();
		return blocksTotal;
	}

	/**
	 * @return true if all subsearches numblocks are final
	 */
	@Override public boolean isTotalFinal() {
		for(Request r : subsearches)
			if(!r.isTotalFinal())
				return false;
		return true;
	}

	public int compareTo(Request right) {
		return getSubject().compareTo(right.getSubject());
	}

	/**
	 * Perform an intersection on results of all subsearches and return <br />
	 * @return Set of URIWrappers
	 */
	@Override public Set<URIWrapper> getResult() throws TaskAbortException {
		if(getState() != Request.RequestState.FINISHED)
			return null;

		ResultSet result = new ResultSet();
		switch(resultOperation){
			case UNION:
				for(Request<Set<URIWrapper>> r : subsearches)
					result.addAll(r.getResult());
				break;
			case INTERSECTION:
				Iterator<Request> it = subsearches.iterator();
				Request<Set<URIWrapper>> r = it.next();
				result.addAll(r.getResult());
				while (it.hasNext()) {
					r = it.next();
					result.retainAll(r.getResult());
				}
				break;
			case REMOVE:
				result.addAll((Set<URIWrapper>)subsearches.get(0).getResult());
				result.removeAll((Set<URIWrapper>)subsearches.get(1).getResult());
				break;
			case PHRASE:
				Logger.minor(this, "Getting results for phrase");
				it = subsearches.iterator();
				r = it.next();
				result.addAll(r.getResult());
				while (it.hasNext()) {
					r = it.next();
					try {
						result.retainFollowed(r.getResult());
					} catch (Exception ex) {
						throw new TaskAbortException("All the data was fetched, but the query could not be completed.",
						      new InvalidSearchException("Unable to determine term positions on these requests and thus unable to determine Phrase matches", ex));
					}
				}
				break;
		}

		allsearches.remove(subject);
		return result;
	}
}
