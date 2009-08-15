/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search;

import freenet.support.Executor;
import freenet.support.HTMLNode;
import plugins.Library.Library;
import plugins.Library.index.Request;
import plugins.Library.index.AbstractRequest;
import plugins.Library.serial.ProgressParts;
import plugins.Library.serial.TaskAbortException;

import freenet.support.Logger;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import plugins.Library.index.CompositeRequest;
import plugins.Library.index.TermEntry;
import plugins.Library.search.ResultSet.ResultOperation;
import plugins.Library.serial.CompositeProgress;
import plugins.Library.serial.Progress;
import plugins.Library.ui.ResultNodeGenerator;

/**
 * Performs asynchronous searches over many index or with many terms and search logic
 * TODO review documentation
 * @author MikeB
 */
public class Search extends AbstractRequest<Set<TermEntry>>
				implements CompositeRequest<Set<TermEntry>> {

	private static Library library;
	private static Executor executor;

	private ResultOperation resultOperation;

	private List<Request<Set<TermEntry>>> subsearches;

	private String query;
	private String indexURI;

	/** Map of Searches by subject */
	private static HashMap<String, Search> allsearches = new HashMap<String, Search>();
	/** Map of Searches by hashCode */
	private static HashMap<Integer,Search> searchhashes = new HashMap<Integer, Search>();
	private ResultSet resultset;

	/**
	 * Settings for producing result nodes, if true a HTMLNode of the results will be generated after the results are complete which can be accessed via getResultNode()
	 */
	private boolean formatResult = false;
	private boolean htmlgroupusk;
	private boolean htmlshowold;
	private boolean htmljs;
	private ResultNodeGenerator resultNodeGenerator;
	private HTMLNode pageEntryNode;

	private enum SearchStatus { Unstarted, Busy, Combining_First, Combining_Last, Formatting, Done };
	private SearchStatus status = SearchStatus.Unstarted;


	private synchronized static void storeSearch(Search search){
		allsearches.put(search.getSubject(), search);
		searchhashes.put(search.hashCode(), search);
	}

	private static synchronized void removeSearch(Search search) {
		allsearches.remove(search.subject);
		searchhashes.remove(search.hashCode());
	}

	/**
	 * Creates a search for any number of indices, starts and returns the associated Request object
	 * TODO startSearch with array of indexes
	 *
	 * @param search string to be searched
	 * @param indexuri URI of index(s) to be used
	 * @param generateResultNode if set, the search operation will conclude with
	 * generating the results for displaying, if not set this step will be skipped.
	 * This action is not guarenteed, in the returned Search as this search may
	 * have already been started.
	 * @throws InvalidSearchException if any part of the search is invalid
	 */
	public static Search startSearch(String search, String indexuri) throws InvalidSearchException, TaskAbortException{
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
		else if(indices.length==1){
			Search newSearch = splitQuery(search, indexuri);
			return newSearch;
		}else{
			// create search for multiple terms over multiple indices
			ArrayList<Request> indexrequests = new ArrayList(indices.length);
			for (String index : indices)
				indexrequests.add(startSearch(search, index));
			Search newSearch = new Search(search, indexuri, indexrequests, ResultOperation.DIFFERENTINDEXES);
			return newSearch;
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
		if(resultOperation==ResultOperation.SINGLE && requests.size()!=1)
			throw new InvalidSearchException(requests.size() + " requests supplied with SINGLE operation");
		if(resultOperation==ResultOperation.REMOVE && requests.size()!=2)
			throw new InvalidSearchException("Negative operations can only have 2 parameters");
		if(		(	resultOperation==ResultOperation.PHRASE
					|| resultOperation == ResultOperation.INTERSECTION
					|| resultOperation == ResultOperation.UNION
					|| resultOperation == ResultOperation.DIFFERENTINDEXES )
				&& requests.size()<2)
			throw new InvalidSearchException(resultOperation.toString() + " operations need more than one term");
		
		query = query.toLowerCase(Locale.US).trim();
		
		// Create a temporary list of sub searches then make it unmodifiable
		List<Request<Set<TermEntry>>> tempsubsearches = new ArrayList();
		for (Request request : requests) {
			if(request != null)
				tempsubsearches.add(request);
			else
				throw new NullPointerException("Search cannot encapsulate null");
		}
		subsearches = Collections.unmodifiableList(tempsubsearches);

		this.query = query;
		this.indexURI = indexURI;
		this.resultOperation = resultOperation;
		try {
			setStatus();
		} catch (TaskAbortException ex) {
			setError(ex);
		}

		storeSearch(this);
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
		if(request == null)
			throw new NullPointerException("Search cannot encapsulate null (query=\""+query+"\" indexURI=\""+indexURI+"\")");
		query = query.toLowerCase(Locale.US).trim();
		subsearches = new ArrayList();
		subsearches.add(request);

		this.query = query;
		this.indexURI = indexURI;
		this.resultOperation = ResultOperation.SINGLE;
		try {
			setStatus();
		} catch (TaskAbortException ex) {
			setError(ex);
		}
		storeSearch(this);
	}


	/**
	 * Splits query into multiple searches, will be used for advanced queries
	 * @param query search query, can use various different search conventions
	 * @param indexuri uri for one index
	 * @return Set of subsearches or null if theres only one search
	 */
	private static Search splitQuery(String query, String indexuri) throws InvalidSearchException, TaskAbortException{
		if(query.matches("\\A\\w*\\Z")) {
			// single search term
			Request request = library.getIndex(indexuri).getTermEntries(query);
			if (request == null)
				throw new InvalidSearchException( "Something wrong with query=\""+query+"\" or indexURI=\""+indexuri+"\", maybe something is wrong with the index or it's uri is wrong." );
			return new Search(query, indexuri, request );
		}

		// Make phrase search
		if(query.matches("\\A\"[^\"]*\"\\Z")){
			ArrayList<Request> phrasesearches = new ArrayList();
			String[] phrase = query.replaceAll("\"(.*)\"", "$1").split(" ");
			Logger.minor(Search.class, "Phrase split"+query);
			for (String subquery : phrase){
				phrasesearches.add(splitQuery(subquery, indexuri));
			}
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
	public static void setup(Library library, Executor executor){
		Search.library = library;
		Search.executor = executor;
		Search.allsearches = new HashMap<String, Search>();
	}

	/**
	 * Gets a Search from the Map
	 * @param search
	 * @param indexuri
	 * @return Search or null if not found
	 */
	public synchronized static Search getSearch(String search, String indexuri){
		if(search==null || indexuri==null)
			return null;
		search = search.toLowerCase(Locale.US).trim();

		return allsearches.get(makeString(search, indexuri));
	}
	public synchronized static Search getSearch(int searchHash){
		return searchhashes.get(searchHash);
	}

	/**
	 * Looks for a given search in the map of searches
	 * @param search
	 * @param indexuri
	 * @return true if it's found
	 */
	public static synchronized boolean hasSearch(String search, String indexuri){
		if(search==null || indexuri==null)
			return false;
		search = search.toLowerCase(Locale.US).trim();
		return allsearches.containsKey(makeString(search, indexuri));
	}

	public static synchronized Map<String, Search> getAllSearches(){
		return Collections.unmodifiableMap(allsearches);
	}

    public String getQuery(){
		return query;
	}

	public String getIndexURI(){
		return indexURI;
	}

	/**
	 * Creates a string which uniquly identifies this Search object for comparison
	 * and lookup, wont make false positives but can make false negatives as search and indexuri aren't standardised
	 * 
	 * @param search
	 * @param indexuri
	 * @return
	 */
	public static String makeString(String search, String indexuri){
		return search + "@" + indexuri;
	}

	/**
	 * A descriptive string for logging
	 */
	@Override
	public String toString(){
		return "Search: "+resultOperation+" : "+subject+" : "+subsearches;
	}

	/**
	 * @return List of Progresses this search depends on, it will not return CompositeProgresses
	 */
	/*@Override**/ public List<? extends Progress> getSubProgress(){
		Logger.minor(this, toString());

		if (subsearches == null)
			return null;
		// Only index splits will allowed as composites
		if (resultOperation == ResultOperation.DIFFERENTINDEXES)
			return subsearches;
		// Everything else is split into leaves
		List<Progress> subprogresses = new ArrayList();
		for (Request<Set<TermEntry>> request : subsearches) {
			if( request instanceof CompositeProgress && ((CompositeProgress) request).getSubProgress()!=null && ((CompositeProgress) request).getSubProgress().iterator().hasNext()){
				for (Iterator<? extends Progress> it = ((CompositeRequest)request).getSubProgress().iterator(); it.hasNext();) {
					Progress progress1 = it.next();
					subprogresses.add(progress1);
				}
			}else
				subprogresses.add(request);
		}
		return subprogresses;
	}


	/**
	 * @return true if all are Finished and Result is ready, also stimulates the creation of the result if all subreqquests are complete and the result isn't made
	 */
	@Override public boolean isDone() throws TaskAbortException{
		setStatus();
		return status == SearchStatus.Done;
	}

	/**
	 * Returns whether the generator has formatted the results
	 * @return
	 */
	public boolean hasGeneratedResultNode(){
		return pageEntryNode != null;
	}

	public HTMLNode getHTMLNode(){
		return pageEntryNode;
	}
	
	/**
	 * After this finishes running, the status of this Search object will be correct, stimulates the creation of the result if all subreqquests are complete and the result isn't made
	 * @throws plugins.Library.serial.TaskAbortException
	 */
	private synchronized void setStatus() throws TaskAbortException{
		switch (status){
			case Unstarted :	// If Unstarted, status -> Busy
				status = SearchStatus.Busy;
			case Busy :
				if(!isSubRequestsComplete())
					for (Request<Set<TermEntry>> request : subsearches)
						if(!(request instanceof Search) || ((Search)request).status==SearchStatus.Busy)
							return;	// If Busy & still waiting for subrequests to complete, status remains Busy
				status = SearchStatus.Combining_First;	// If Busy and waiting for subrequests to combine, status -> Combining_First
			case Combining_First :	// for when subrequests are combining
				if(!isSubRequestsComplete())	// If combining first and subsearches still haven't completed, remain
					return;
				// If subrequests have completed start process to combine results
				resultset = new ResultSet(subject, resultOperation, subsearches);
				if(executor!=null)
					executor.execute(resultset, "Library.Search : combining results");
				else
					(new Thread(resultset, "Library.Search : combining results")).start();
				status = SearchStatus.Combining_Last;
			case Combining_Last :	// for when this is combining
				if(!resultset.isDone())
					return;		// If Combining & combine not finished, status remains as Combining
				subsearches = null;	// clear the subrequests after they have been combined
				// If finished Combining and asked to generate resultnode, start that process
				if(formatResult){
					// resultset doesn't exist but subrequests are complete so we can start up a resultset
					resultNodeGenerator = new ResultNodeGenerator(resultset, htmlgroupusk, htmlshowold, htmljs);
					if(executor!=null)
						executor.execute(resultNodeGenerator, "Library.Search : formatting results");
					else
						(new Thread(resultNodeGenerator, "Library.Search : formatting results")).start();
					status = SearchStatus.Formatting;	// status -> Formatting
				}else			// If not asked to format output, status -> done
					status = SearchStatus.Done;
			case Formatting :
				if(formatResult){
					// If asked to generate resultnode and still doing that, status remains as Formatting
					if(!resultNodeGenerator.isDone())
						return;
					// If finished Formatting or not asked to do so, status -> Done
					pageEntryNode = resultNodeGenerator.getPageEntryNode();
					resultNodeGenerator = null;
				}
				status = SearchStatus.Done;
			case Done :
				// Done , do nothing
		}
	}
	
	/**
	 * @return true if all are Finished, false otherwise
	 */
	private boolean isSubRequestsComplete() throws TaskAbortException{
		for(Request r : subsearches)
			if(!r.isDone())
				return false;
		return true;
	}
	

	/**
	 * Return the set of results or null if it is not ready <br />
	 * @return Set of TermEntry
	 */
	@Override public Set<TermEntry> getResult() throws TaskAbortException {
		if(!isDone())
			return null;
		
		removeSearch(this);
		Set<TermEntry> rs = resultset;
		resultset= null;

		return rs;
	}

	public synchronized void setMakeResultNode(boolean groupusk, boolean showold, boolean js){
		formatResult = true;
		htmlgroupusk = groupusk;
		htmlshowold = showold;
		htmljs = js;
	}

	@Override
	public ProgressParts getParts() throws TaskAbortException {
		if(subsearches==null)
			return ProgressParts.normalise(0, 0);
		return ProgressParts.getParts(this.getSubProgress(), ProgressParts.ESTIMATE_UNKNOWN);
	}

	@Override
	public String getStatus() {
		try {
			setStatus();
			return status.name();
		} catch (TaskAbortException ex) {
			return "Error finding Status";
		}
	}

	public boolean isPartiallyDone() {
		throw new UnsupportedOperationException("Not supported yet.");
	}
}
