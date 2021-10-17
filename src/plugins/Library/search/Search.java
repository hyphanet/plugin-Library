/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import plugins.Library.Library;
import plugins.Library.search.ResultSet.ResultOperation;
import plugins.Library.ui.ResultNodeGenerator;
import plugins.Library.index.TermEntry;
import plugins.Library.util.exec.AbstractExecution;
import plugins.Library.util.exec.CompositeProgress;
import plugins.Library.util.exec.Execution;
import plugins.Library.util.exec.Progress;
import plugins.Library.util.exec.ProgressParts;
import plugins.Library.util.exec.TaskAbortException;
import freenet.support.Executor;
import freenet.support.HTMLNode;
import freenet.support.Logger;

/**
 * Performs asynchronous searches over many index or with many terms and search logic
 * TODO review documentation
 * @author MikeB
 */
public class Search extends AbstractExecution<Set<TermEntry>>
				implements CompositeProgress, Execution<Set<TermEntry>> {

	private static Library library;
	private static Executor executor;

	private ResultOperation resultOperation;

	private List<Execution<Set<TermEntry>>> subsearches;

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

	static volatile boolean logMINOR;
	static volatile boolean logDEBUG;
	
	static {
		Logger.registerClass(Search.class);
	}

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
	 * @return existing Search for this if it exists, new one otherwise or null if query is for a stopword or stop query
	 * @throws InvalidSearchException if any part of the search is invalid
	 */
	public static Search startSearch(String search, String indexuri) throws InvalidSearchException, TaskAbortException{
		search = search.toLowerCase(Locale.US).trim();
		if(search.length()==0)
			throw new InvalidSearchException("Blank search");
		search = fixCJK(search);

		// See if the same search exists
		if (hasSearch(search, indexuri))
			return getSearch(search, indexuri);

		if(logMINOR) Logger.minor(Search.class, "Starting new search for "+search+" in "+indexuri);

		String[] indices = indexuri.split("[ ;]");
		if(indices.length<1 || search.trim().length()<1)
			throw new InvalidSearchException("Attempt to start search with no index or terms");
		else if(indices.length==1){
			Search newSearch = splitQuery(search, indexuri);
			return newSearch;
		}else{
			// create search for multiple terms over multiple indices
			ArrayList<Execution<Set<TermEntry>>> indexrequests = new ArrayList<Execution<Set<TermEntry>>>(indices.length);
			for (String index : indices){
				Search indexsearch = startSearch(search, index);
				if(indexsearch==null)
					return null;
				indexrequests.add(indexsearch);
			}
			Search newSearch = new Search(search, indexuri, indexrequests, ResultOperation.DIFFERENTINDEXES);
			return newSearch;
		}
	}


	/** Transform <string of CJK> into characters separated by spaces.
	 * FIXME: I'm sure we could handle this a lot better! */
	private static String fixCJK(String search) {
		StringBuffer sb = null;
		int offset = 0;
		boolean wasCJK = false;
		for(;offset < search.length();) {
			int character = search.codePointAt(offset);
			if(SearchUtil.isCJK(character)) {
				if(wasCJK) {
					sb.append(' '); // Delimit characters by whitespace so we do an && search.
				}
				if(sb == null) {
					sb = new StringBuffer();
					sb.append(search.substring(0, offset));
				}
				wasCJK = true;
			} else {
				wasCJK = false;
			}
			if(sb != null)
				sb.append(Character.toChars(character));
			offset += Character.charCount(character);
		}
		if(sb != null)
			return sb.toString();
		else
			return search;
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
	private Search(String query, String indexURI, List<? extends Execution<Set<TermEntry>>> requests, ResultOperation resultOperation)
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
		List<Execution<Set<TermEntry>>> tempsubsearches = new ArrayList<Execution<Set<TermEntry>>>();
		for (Execution<Set<TermEntry>> request : requests) {
			if(request != null || resultOperation == ResultOperation.PHRASE)
				tempsubsearches.add(request);
			else
				throw new NullPointerException("Search cannot encapsulate nulls except in the case of a ResultOperation.PHRASE where they are treated as blanks");
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
		if(logMINOR) Logger.minor(this, "Created Search object for with subRequests :"+subsearches);
	}

	/**
	 * Encapsulate a request as a Search, only so original query and uri can be stored
	 *
	 * @param query the query this instance is being used for, only for reference
	 * @param indexURI the index uri this search is made on, only for reference
	 * @param request Request to encapsulate
	 */
	private Search(String query, String indexURI, Execution<Set<TermEntry>> request){
		super(makeString(query, indexURI));
		if(request == null)
			throw new NullPointerException("Search cannot encapsulate null (query=\""+query+"\" indexURI=\""+indexURI+"\")");
		query = query.toLowerCase(Locale.US).trim();
		subsearches = Collections.singletonList(request);

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
	 * @return single Search encompassing everything in the query or null if query is a stop word
	 * @throws InvalidSearchException if search query is invalid
	 */
	private static Search splitQuery(String query, String indexuri) throws InvalidSearchException, TaskAbortException{
		query = query.trim();
		if(query.matches("\\A[\\S&&[^-\"]]*\\Z")){
			// single search term
			// return null if stopword
			if(SearchUtil.isStopWord(query))
				return null;
			Execution<Set<TermEntry>> request = library.getIndex(indexuri).getTermEntries(query);
			if (request == null)
				throw new InvalidSearchException( "Something wrong with query=\""+query+"\" or indexURI=\""+indexuri+"\", maybe something is wrong with the index or it's uri is wrong." );
			return new Search(query, indexuri, request );
		}

		// Make phrase search (hyphen-separated words are also treated as phrases)
		if(query.matches("\\A\"[^\"]*\"\\Z") || query.matches("\\A((?:[\\S&&[^-]]+-)+[\\S&&[^-]]+)\\Z")){
			ArrayList<Execution<Set<TermEntry>>> phrasesearches = new ArrayList<Execution<Set<TermEntry>>>();
			String[] phrase = query.replaceAll("\"(.*)\"", "$1").split("[\\s-]+");
			if(logMINOR) Logger.minor(Search.class, "Phrase split: "+query);
			for (String subquery : phrase){
				Search term = startSearch(subquery, indexuri);
				phrasesearches.add(term);
			}
			// Not really sure how stopwords should be handled in phrases
			// currently i'm thinking that they should be treated as blanks
			// between other words and ignored in other cases "jesus of nazareth"
			// is treated as "jesus <blank> nazareth". Whereas "the who" will be
			// treated as a stop query as just searching for "who" and purporting
			// that the results are representative of "the who" is misleading.

			// this makes sure there are no trailing nulls at the start
			while(phrasesearches.size() > 0 && phrasesearches.get(0)==null)
				phrasesearches.remove(0);
			// this makes sure there are no trailing nulls at the end
			while(phrasesearches.size() > 0 && phrasesearches.get(phrasesearches.size()-1)==null)
				phrasesearches.remove(phrasesearches.size()-1);

			if(phrasesearches.size()>1)
				return new Search(query, indexuri, phrasesearches, ResultOperation.PHRASE);
			else
				return null;
		}



		if(logMINOR) Logger.minor(Search.class, "Splitting " + query);
		// Remove phrases, place them in arraylist and replace them with references to the arraylist
		ArrayList<String> phrases = new ArrayList<String>();
		Matcher nextPhrase = Pattern.compile("\"([^\"]*?)\"").matcher(query);
		StringBuffer sb = new StringBuffer();
		while (nextPhrase.find())
		{
			String phrase = nextPhrase.group(1);
			nextPhrase.appendReplacement(sb, "£"+phrases.size()+"€");
			phrases.add(phrase);
		}
		nextPhrase.appendTail(sb);
		
		if(logMINOR) Logger.minor(Search.class, "Phrases removed query: "+sb);

		// Remove the unmatched \" (if any)
		String formattedquery = sb.toString().replaceFirst("\"", "");
		if(logMINOR) Logger.minor(Search.class, "Removing the unmatched bracket: "+formattedquery);

		// Treat hyphens as phrases, as they are treated equivalently in spider so this is the most effective way now
		nextPhrase = Pattern.compile("((?:[\\S&&[^-]]+-)+[\\S&&[^-]]+)").matcher(formattedquery);
		sb.setLength(0);
		while (nextPhrase.find())
		{
			String phrase = nextPhrase.group(1);
			nextPhrase.appendReplacement(sb, "£"+phrases.size()+"€");
			phrases.add(phrase);
		}
		nextPhrase.appendTail(sb);

		formattedquery = sb.toString();
		if(logMINOR) Logger.minor(Search.class, "Treat hyphenated words as phrases: " + formattedquery);

		// Substitute service symbols. Those which are inside phrases should not be seen as "service" ones.
		formattedquery = formattedquery.replaceAll("\\s+or\\s+", "||");
		if(logMINOR) Logger.minor(Search.class, "OR-subst query : "+formattedquery);
		formattedquery = formattedquery.replaceAll("\\s+(?:not\\s*|-)(\\S+)", "^^($1)");
		if(logMINOR) Logger.minor(Search.class, "NOT-subst query : "+formattedquery);
		formattedquery = formattedquery.replaceAll("\\s+", "&&");
		if(logMINOR) Logger.minor(Search.class, "AND-subst query : "+formattedquery);

		// Put phrases back in
		String[] phraseparts=formattedquery.split("£");
		formattedquery=phraseparts[0];
		for (int i = 1; i < phraseparts.length; i++) {
			String string = phraseparts[i];
			if(logMINOR) Logger.minor(Search.class, "replacing phrase "+string.replaceFirst("(\\d+).*", "$1"));
			formattedquery += "\""+ phrases.get(Integer.parseInt(string.replaceFirst("(\\d+).*", "$1"))) +"\"" + string.replaceFirst("\\d+€(.*)", "$1");
		}
		if(logMINOR) Logger.minor(Search.class, "Phrase back query: "+formattedquery);

		// Make complement search
		if (formattedquery.contains("^^(")){
			ArrayList<Execution<Set<TermEntry>>> complementsearches = new ArrayList<Execution<Set<TermEntry>>>();
			String[] splitup = formattedquery.split("(\\^\\^\\(|\\))", 3);
			Search add = startSearch(splitup[0]+splitup[2], indexuri);
			Search subtract = startSearch(splitup[1], indexuri);
			if(add==null || subtract == null)
				return null;	// If 'and' is not to be searched for 'the -john' is not to be searched for, also 'john -the' wouldnt have shown many results anyway
			complementsearches.add(add);
			complementsearches.add(subtract);
			return new Search(query, indexuri, complementsearches, ResultOperation.REMOVE);
		}
		// Split intersections
		if (formattedquery.contains("&&")){
			ArrayList<Search> intersectsearches = new ArrayList<Search>();
			String[] intersects = formattedquery.split("&&");
			for (String subquery : intersects){
				Search subsearch = startSearch(subquery, indexuri);
				if (subsearch != null)		// We will assume that searching for 'the big apple' will near enough show the same results as 'big apple', so just ignore 'the' in interseaction
					intersectsearches.add(subsearch);
			}
			switch(intersectsearches.size()){
				case 0:				// eg. 'the that'
					return null;
				case 1 :			// eg. 'cake that' will return a search for 'cake'
					return intersectsearches.get(0);
				default :
					return new Search(query, indexuri, intersectsearches, ResultOperation.INTERSECTION);
			}
		}
		// Split Unions
		if (formattedquery.contains("||")){
			ArrayList<Execution<Set<TermEntry>>> unionsearches = new ArrayList<Execution<Set<TermEntry>>>();
			String[] unions = formattedquery.split("\\|\\|");
			for (String subquery : unions){
				Search add = startSearch(subquery, indexuri);
				if (add == null)	// eg a search for 'the or cake' would be almost the same as a search for 'the' and so should be treated as such
					return null;
				unionsearches.add(add);
			}
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
	public static boolean hasSearch(String search, String indexuri){
		if(search==null || indexuri==null)
			return false;
		search = search.toLowerCase(Locale.US).trim();
		return allsearches.containsKey(makeString(search, indexuri));
	}

	public static boolean hasSearch(int searchHash){
		return searchhashes.containsKey(searchHash);
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
	 */
	public static String makeString(String search, String indexuri){
		return search + "@" + indexuri;
	}

	/**
	 * A descriptive string for logging
	 */
	@Override
	public String toString(){
		return "Search: "+resultOperation+" - " + status + " : "+subject+" : "+subsearches;
	}

	/**
	 * @return List of Progresses this search depends on, it will not return CompositeProgresses
	 */
	public List<? extends Progress> getSubProgress(){
		if(logMINOR) Logger.minor(this, toString());

		if (subsearches == null)
			return null;
		// Only index splits will allowed as composites
		if (resultOperation == ResultOperation.DIFFERENTINDEXES)
			return subsearches;
		// Everything else is split into leaves
		List<Progress> subprogresses = new ArrayList<Progress>();
		for (Execution<Set<TermEntry>> request : subsearches) {
			if(request == null)
				continue;
			if( request instanceof CompositeProgress && ((CompositeProgress)request).getSubProgress()!=null && ((CompositeProgress) request).getSubProgress().iterator().hasNext()){
				for (Iterator<? extends Progress> it = ((CompositeProgress)request).getSubProgress().iterator(); it.hasNext();) {
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
	 */
	public boolean hasGeneratedResultNode(){
		return pageEntryNode != null;
	}

	/**
	 * After this finishes running, the status of this Search object will be correct, stimulates the creation of the result if all subreqquests are complete and the result isn't made
	 * @throws plugins.Library.util.exec.TaskAbortException
	 */
	private synchronized void setStatus() throws TaskAbortException{
		switch (status){
			case Unstarted :	// If Unstarted, status -> Busy
				status = SearchStatus.Busy;
			case Busy :
				if(!isSubRequestsComplete())
					for (Execution<Set<TermEntry>> request : subsearches)
						if(request != null && (!(request instanceof Search) || ((Search)request).status==SearchStatus.Busy))
							return;	// If Busy & still waiting for subrequests to complete, status remains Busy
				status = SearchStatus.Combining_First;	// If Busy and waiting for subrequests to combine, status -> Combining_First
			case Combining_First :	// for when subrequests are combining
				if(!isSubRequestsComplete())	// If combining first and subsearches still haven't completed, remain
					return;
				// If subrequests have completed start process to combine results
				resultset = new ResultSet(subject, resultOperation, subsearches, innerCanFailAndStillComplete());
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
		for(Execution<Set<TermEntry>> r : subsearches) {
			try {
				if(r != null && !r.isDone())
					return false;
			} catch (TaskAbortException e) {
				if(innerCanFailAndStillComplete()) continue;
				throw e;
			}
		}
		return true;
	}


	/**
	 * Return the set of results or null if it is not ready <br />
	 * @return Set of TermEntry
	 */
	@Override public Set<TermEntry> getResult() throws TaskAbortException {
		try {
			if(!isDone())
				return null;
		} catch (TaskAbortException e) {
			removeSearch(this);
			throw e;
		}

		removeSearch(this);
		Set<TermEntry> rs = resultset;
		return rs;
	}

	public HTMLNode getHTMLNode(){
		try {
			if (!isDone() || !formatResult) {
				return null;
			}
		} catch (TaskAbortException ex) {
			Logger.error(this, "Error finding out whether this is done", ex);
			return null;
		}

		removeSearch(this);
		HTMLNode pen = pageEntryNode;
		pageEntryNode = null;

		return pen;
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

	public void remove() {
		// FIXME abort the subsearches.
		// FIXME in fact this shouldn't be necessary, the TaskAbortException should be propagated upwards???
		// FIXME really we'd want to convert it into a failed status so we could show that one failed, and still show partial results
		if(subsearches != null) {
			for(Execution<Set<TermEntry>> sub : subsearches) {
				if(sub instanceof Search)
					((Search)sub).remove();
			}
		}
		removeSearch(this);
	}

	public boolean innerCanFailAndStillComplete() {
		switch(resultOperation) {
		case DIFFERENTINDEXES:
		case UNION:
			return true;
		}
		return false;
	}

}
