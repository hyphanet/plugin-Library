package plugins.XMLLibrarian;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import freenet.support.HTMLEncoder;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import java.util.Set;
import java.util.TreeSet;


/**
 * Performs asynchronous searches
 * @author MikeB
 */
public class Search implements Request<Set<URIWrapper>> {
	static private XMLLibrarian xl;
	
	// TODO will be replaced with a binary tree to handle search logic although Set is more useful for multiple indices
	private HashSet<Request> subsearches;
	private TreeSet<URIWrapper> resultList = null;

	private String subject;
	private String query;
	private String indexURI;

	protected RequestStatus status;
	protected int stage=0;
	protected int stageCount;
	private long blocksCompleted;
	private long blocksTotal;
	protected Exception err;
	Set<URIWrapper> result;

	private static HashMap<String, Search> allsearches = new HashMap<String, Search>();


	/**
	 * Creates a search for any number of indices, starts and returns the associated Request object
	 *
	 * @param search string to be searched
	 * @param indexuri URI of index(s) to be used
	 * @throws InvalidSearchException if any part of the search is invalid
	 */
	public static Search startSearch(String search, String indexuri) throws InvalidSearchException{
		search = search.toLowerCase();

		// See if the same search exists
		if (hasSearch(search, indexuri))
			return getSearch(search, indexuri);

		Logger.minor(Search.class, "Starting new search for "+search+" in "+indexuri);

		String[] indices = indexuri.split(" ");
		String[] searchterms = search.split(" ");
		if(indices.length<1 || searchterms.length<1)
			throw new InvalidSearchException("Attempt to start search with no index or terms");
		else if(indices.length==1 && searchterms.length==1)
			return new Search(search, indexuri, Index.getIndex(indices[0]).find(search));
		else if(indices.length==1 && searchterms.length>1){
			// Create search for multiple terms over 1 index
			ArrayList<Request> requests = new ArrayList(searchterms.length);
			for (String term : searchterms)
				requests.add(Index.getIndex(indices[0]).find(term));
			return new Search(search, indexuri, requests);
		}else{
			// create search for multiple terms over multiple indices
			ArrayList<Request> indexrequests = new ArrayList(indices.length);
			for (String index : indices){
				ArrayList<Request> termrequests = new ArrayList(searchterms.length);
				for (String term : searchterms)
					termrequests.add(Index.getIndex(index).find(term));
				indexrequests.add(new Search(search, index, termrequests));
			}
			return new Search(search, indexuri, indexrequests);
		}
	}


	/**
	 * Creates Search instance depending on the given requests
	 *
	 * @param query the query this instance is being used for, only for reference
	 * @param indexURI the index uri this search is made on, only for reference
	 * @param requests subRequests of this search
	 * @throws InvalidSearchException if the search is invalid
	 **/
	private Search(String query, String indexURI, List<Request> requests) throws InvalidSearchException{
		query = query.toLowerCase();
		subsearches = new HashSet(requests);
		
		this.query = query;
		this.indexURI = indexURI;
		this.subject = makeString(query, indexURI);
		
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
	public Search(String query, String indexURI, Request request){
		query = query.toLowerCase();
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
	
	

	
	public static Search getSearch(String search, String indexuri){
		search = search.toLowerCase();
        
		// See if the same search exists
		if (hasSearch(search, indexuri))
			return allsearches.get(makeString(search, indexuri));
		else
			return null;
	}
	
	public static boolean hasSearch(String search, String indexuri){
		if(search==null || indexuri==null)
			return false;
		if(allsearches.containsKey(makeString(search, indexuri)))
			Logger.normal(allsearches.get(makeString(search, indexuri)), search);
		else
			Logger.normal(Search.class, makeString(search, indexuri)+" not found");
		return allsearches.containsKey(makeString(search, indexuri));
	}

    public String getQuery(){
		return query;
	}

	public String getIndexURI(){
		return indexURI;
	}
	

	public HTMLNode getResultNode(){
		// Output results
		int results = 0;

		HTMLNode node = new HTMLNode("div", "id", "results");
		HTMLNode resultTable = node.addChild("table", new String[]{"width", "class"}, new String[]{"95%", "librarian-results"});
		Iterator<URIWrapper> it = getResult().iterator();
		while (it.hasNext()) {
			HTMLNode entry = resultTable.addChild("tr").addChild("td").addChild("p").addChild("table", new String[]{"class", "width", "border"}, new String[]{"librarian-result", "95%", "1"});
			URIWrapper o = it.next();
			String showurl = o.URI;
			String showtitle = o.descr;
			if (showtitle.trim().length() == 0)
				showtitle = "not available";
			if (showtitle.equals("not available"))
				showtitle = showurl;
			String description = HTMLEncoder.encode(o.descr);
			if (!description.equals("not available")) {
				description = description.replaceAll("(\n|&lt;(b|B)(r|R)&gt;)", "<br>");
				description = description.replaceAll("  ", "&nbsp; ");
				description = description.replaceAll("&lt;/?[a-zA-Z].*/?&gt;", "");
			}
			showurl = HTMLEncoder.encode(showurl);
			if (showurl.length() > 60)
				showurl = showurl.substring(0, 15) + "&hellip;" + showurl.replaceFirst("[^/]*/", "/");
			String realurl = (o.URI.startsWith("/") ? "" : "/") + o.URI;
			realurl = HTMLEncoder.encode(realurl);
			entry.addChild("tr").addChild("td", new String[]{"align", "bgcolor", "class"}, new String[]{"center", "#D0D0D0", "librarian-result-url"})
				.addChild("a", new String[]{"href", "title"}, new String[]{realurl, o.URI}, showtitle);
			entry.addChild("tr").addChild("td", new String[]{"align", "class"}, new String[]{"left", "librarian-result-summary"});
			results++;
		}
		node.addChild("p").addChild("span", "class", "librarian-summary-found", xl.getString("Found")+results+xl.getString("results"));
		return node;
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
	@Override
	public Set<Request> getSubRequests(){
		return subsearches;
	}

	/**
	 * @return true if progress has been read since it was last updated
	 */
	@Override
	public boolean progressAccessed(){
		for(Request r : subsearches)
			if(!r.progressAccessed())
				return false;
		return true;
	}

	/**
	 * @return minimum of stages of all subsearches
	 */
	@Override
	public int getSubStage(){
		if(progressAccessed())
			return stage;
		stage=0;
		for(Request r : subsearches)
			stage+=r.getSubStageCount();
		return stage;
	}

	@Override
	public boolean isFinished(){
		for(Request r : subsearches)
			if(!r.isFinished())
				return false;
		return true;
	}
	@Override
	public boolean hasResult(){
		for(Request r : subsearches)
			if(r.hasResult())
				return true;
		return false;
	}
	@Override
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

	public int getSubStageCount() {
		if(progressAccessed())
			return stageCount;
		stageCount=0;
		for(Request r : subsearches)
			stageCount+=r.getSubStageCount();
		return stageCount;
	}

	public long getNumBlocksCompleted() {
		if(progressAccessed())
			return blocksCompleted;
		blocksCompleted=0;
		for(Request r : subsearches)
			blocksCompleted+=r.getNumBlocksCompleted();
		return blocksCompleted;
	}

	public long getNumBlocksTotal() {
		if(progressAccessed())
			return blocksTotal;
		blocksTotal=0;
		for(Request r : subsearches)
			blocksTotal+=r.getNumBlocksTotal();
		return blocksTotal;
	}

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

	public Set<URIWrapper> getResult() {
		// TODO implement search logic and cache search results
		result = new TreeSet<URIWrapper>();
		// Intersect all Sets
		for(Request<Set> r : subsearches)
			if(r.hasResult())
				if(result.size()>0)
					result.retainAll(r.getResult());
				else
					result.addAll(r.getResult());
		return result;
	}
}
