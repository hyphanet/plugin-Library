/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package plugins.Library.ui;


import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;
import java.util.ArrayList;
import java.util.Iterator;
import plugins.Library.Library;
import plugins.Library.index.Request;
import plugins.Library.search.InvalidSearchException;
import plugins.Library.search.Search;
import plugins.Library.serial.ChainedProgress;
import plugins.Library.serial.CompositeProgress;
import plugins.Library.serial.Progress;
import plugins.Library.serial.ProgressParts;
import plugins.Library.serial.TaskAbortException;



/**
 * Generates the main search page
 *
 * @author MikeB
 */
class MainPage implements WebPage {
	private final Library library;
	private final PluginRespirator pr;

	private Search search = null;
	private ArrayList<Exception> exceptions = new ArrayList();
	private boolean showold = false;
	private boolean js = false;
	private String query = "";
	/** All of the indexes used in the request together seperated by spaces */
	private String indexstring = Library.DEFAULT_INDEX_SITE;
	/** Array of all the bookmark indexes used in this request */
	private ArrayList<String> selectedBMIndexes = new ArrayList();
	/** Any other indexes which are not bookmarks seperated by spaces */
	private String etcIndexes ="";
	private boolean groupusk;
	private StringBuilder messages = new StringBuilder();

	// For when a freesite requests that a bookmark be added, user authorization is needed
	private boolean authorize;
	private String addindexname;
	private String addindexuri;
	
	
	/**
	 * TODO the whole index thing here is a huge mess
	 * FIXME ajaxy stuff isnt working, it doesnt seem to renew the timeout in the js, it stopped working when the response type of the xml was changed to text/xml
	 * @param library
	 * @param pr
	 */
	MainPage(Library library, PluginRespirator pr) {
		this.library = library;
		this.pr = pr;
	}

	/**
	 * Process a get request, the only parameters allowed for a get request are
	 * request id (for an ongoing request) and formatting parameters
	 */
	public void processGetRequest(HTTPRequest request){
		js = request.isParameterSet("js");
		showold = request.isParameterSet("showold");
		groupusk = request.isParameterSet("groupusk");

		if (request.isParameterSet("request")){
			search = Search.getSearch(request.getIntParam("request"));
			if(search!=null){
				if(request.isPartSet("indexname") && request.getPartAsString("indexname", 20).length() > 0){
					library.addBookmark(request.getPartAsString("indexname", 20), request.getPartAsString("index", 128));
				}
				query = search.getQuery();
				indexstring = search.getIndexURI();
				String[] indexes = indexstring.split("[ ;]");
				for (String string : indexes) {
					if(string.length() > Library.BOOKMARK_PREFIX.length() && library.bookmarkKeys().contains(string.substring(Library.BOOKMARK_PREFIX.length())))
						selectedBMIndexes.add(string);
					else
						etcIndexes += string.trim() + " ";
				}
				etcIndexes = etcIndexes.trim();
				Logger.minor(this, "Refreshing request "+request.getIntParam("request")+" " +query);
			}
		}
	}
	
	/**
	 * Process a post request
	 * 
	 * @see plugins.XMLSpider.WebPage#processPostRequest(freenet.support.api.HTTPRequest,
	 * freenet.support.HTMLNode)
	 */
	public void processPostRequest(HTTPRequest request, HTMLNode contentNode, boolean userAccess ) {
		js = request.isPartSet("js");
		showold = request.isPartSet("showold");
		groupusk = request.isPartSet("groupusk");


		// Check for adding an index
		// TODO make sure name is valid
		if(request.isPartSet("indexname") && request.getPartAsString("indexname", 20).length() > 0){
			if (userAccess){
				library.addBookmark(request.getPartAsString("indexname", 20), request.getPartAsString("index", 128));
			}else{
				authorize = true;
				addindexname = request.getPartAsString("indexname", 20);
				addindexuri = request.getPartAsString("index", 128);
			}
		}

		// Get index list
		indexstring = "";
		for (String bm : library.bookmarkKeys()){
			String bmid = (Library.BOOKMARK_PREFIX + bm).trim();
			//Logger.normal(this, "checking for ~" + bm + " - "+request.isPartSet("~"+bm));
			if(request.isPartSet("~"+bm)){
				indexstring += bmid + " ";
				selectedBMIndexes.add(bmid);
			}
		}
		if (request.isPartSet("index")){
			indexstring += request.getPartAsString("index", 256).trim();
			etcIndexes = request.getPartAsString("index", 256).trim();
		}
		indexstring = indexstring.trim();

		if("".equals(indexstring))
			indexstring = Library.DEFAULT_INDEX_SITE;

		if(etcIndexes.length()==0 && selectedBMIndexes.size() == 0)
			selectedBMIndexes.add(Library.DEFAULT_INDEX_SITE);
		
		
		// get search query
		if (request.isPartSet("search")){
			// Start or continue a search
			query = request.getPartAsString("search", 256);
			
			try {
				//Logger.normal(this, "starting search for "+query+" on "+indexuri);
				search = Search.startSearch(query, indexstring);
			} catch (InvalidSearchException ex) {
				exceptions.add(ex);
			} catch (TaskAbortException ex) {
				exceptions.add(ex);		// TODO handle these exceptions separately
			} catch (RuntimeException ex){
				exceptions.add(ex);
			}
		}

	}


	
	/**
	 * Write the search page out
	 *
	 * 
	 * @see plugins.XMLSpider.WebPage#writeContent(freenet.support.api.HTTPRequest,
	 * freenet.support.HTMLNode)
	 */
	public void writeContent(HTMLNode contentNode, MultiValueTable<String, String> headers) {
		//Logger.normal(this, "Writing page for "+ query + " " + search + " " + indexuri);

		String refreshURL = null;
		if ( search != null)
			refreshURL = path()+"?request="+search.hashCode() + (showold?"&showold=on":"") + (groupusk?"&groupusk=on":"");
		try {
			// Don't refresh if there is no request option, if it is finished or there is an error to show or js is enabled
			if (search != null && !"".equals(search.getQuery()) && !search.isDone() && exceptions.size() <= 0) {
				// refresh will GET so use a request id
				if (!js && !authorize) {
					headers.put("Refresh", "2;url=" + refreshURL);
					//contentNode.addChild("script", new String[]{"type", "src"}, new String[]{"text/javascript", path() + "static/" + (js ? "scriptjs" : "detectjs") + "?request="+search.hashCode()+(showold?"&showold=on":"")}).addChild("%", " ");
					//contentNode.addChild("script", new String[]{"type", "src"}, new String[]{"text/javascript", path() + "static/" + (js ? "scriptjs" : "detectjs") + "?request="+search.hashCode()+(showold?"&showold=on":"")}).addChild("%", " ");
				}
			}
		} catch (TaskAbortException ex) {
			exceptions.add(ex);	// TODO do this much better
		}

		
        // Start of body
		// authorization box, gives the user a choice of whther to authorize something
		if(authorize){
			HTMLNode authorizeBox = contentNode.addChild("div");
			authorizeBox.addChild("h1", "Your authorization required :");
			HTMLNode bookmarkBox = authorizeBox.addChild("div", "Whatever started this request is trying to add a bookmark you your index bookmarks, do you want to add a bookmark with the name \""+addindexname+"\" and uri \""+addindexuri+"\"?");
			bookmarkBox.addChild("a", "href", refreshURL + "&indexname="+addindexname+"&index="+addindexuri, "yes");
			bookmarkBox.addChild("br");
			bookmarkBox.addChild("a", "href", refreshURL, "no");
		}
		contentNode.addChild(searchBox());


        // Show any errors
		HTMLNode errorDiv = new HTMLNode("div", "id", "errors");
		for (Exception exception : exceptions) {
			addError(errorDiv, exception);
		}
		contentNode.addChild(errorDiv);
		contentNode.addChild("p", messages.toString());


        // If showing a search
        if(search != null){
			try {
				// show progress
				contentNode.addChild(progressBox());
				// If search is complete show results
				if (search.isDone()) {
					// TODO dont do it this way
					try {
						ResultNodeGenerator nodegenerator = new ResultNodeGenerator(search.getResult(), groupusk);
						contentNode.addChild(nodegenerator.generatePageEntryNode(showold, js));
					} catch (TaskAbortException ex) {
						addError(errorDiv, ex);
					} catch (RuntimeException ex) {
						addError(errorDiv, ex);
					}
				} else {
					contentNode.addChild("div", "id", "results").addChild("#");
				}
			} catch (TaskAbortException ex) {
				// this will never catch TODO do this much much nicer
			}
        }
	}


	/**
	 * Create search form
	 * @param search already started
	 * @param indexuri
	 * @param js whether js has been detected
	 * @param showold
	 * @return
	 */
	private HTMLNode searchBox(){
		// Put all bookmarked indexes being used into a list and leave others in a string
//		Logger.normal(this, "searchBox for "+search + "  " + indexuri);
//		String[] indexes = indexuri.split("[ ;]");
//		Set<String> allbookmarks = library.bookmarkKeys();
//		ArrayList<String> usedbookmarks = new ArrayList();
//		indexuri = "";
//		for (String string : indexes) {
//			if(string.startsWith(Library.BOOKMARK_PREFIX)
//					&& allbookmarks.contains(string.substring(Library.BOOKMARK_PREFIX.length())))
//				usedbookmarks.add(string);
//			else
//				indexuri += string + " ";
//		}
//		indexuri.trim();


		HTMLNode searchDiv = new HTMLNode("div", new String[]{"id", "style"}, new String[]{"searchbar", "text-align: center;"});
		HTMLNode searchForm = pr.addFormChild(searchDiv, path(), "searchform");
			HTMLNode searchBox = searchForm.addChild("div", "style", "display: inline-table; text-align: left; margin: 20px 20px 20px 0px;");
				searchBox.addChild("#", "Search query:");
				searchBox.addChild("br");
				searchBox.addChild("input", new String[]{"name", "size", "type", "value", "title"}, new String[]{"search", "40", "text", query, "Enter a search query. You can use standard search syntax such as 'and', 'or', 'not' and \"\" double quotes around phrases"});
				searchBox.addChild("input", new String[]{"name", "type", "value", "tabindex"}, new String[]{"find", "submit", "Find!", "1"});
				if(js)
					searchBox.addChild("input", new String[]{"type","name"}, new String[]{"hidden","js"});
				// Shows the list of bookmarked indexes TODO show descriptions on mouseover ??
				HTMLNode indexeslist = searchBox.addChild("ul", "class", "index-bookmark-list", "Select indexes");
				for (String bm : library.bookmarkKeys()){
					//Logger.normal(this, "Checking for bm="+Library.BOOKMARK_PREFIX+bm+" in \""+indexuri + " = " + selectedBMIndexes.contains(Library.BOOKMARK_PREFIX+bm)+" "+indexuri.contains(Library.BOOKMARK_PREFIX+bm));
					indexeslist.addChild("li")
						.addChild("input", new String[]{"type", "name", "value", (selectedBMIndexes.contains(Library.BOOKMARK_PREFIX+bm) ? "checked" : "size" )}, new String[]{"checkbox", "~"+bm, Library.BOOKMARK_PREFIX+bm, "1", } , bm);
				}

			HTMLNode optionsBox = searchForm.addChild("div", "style", "margin: 20px 0px 20px 20px; display: inline-table; text-align: left;", "Options");
				HTMLNode optionsList = optionsBox.addChild("ul", "class", "options-list");
					optionsList.addChild("li")
						.addChild("input", new String[]{"name", "type", groupusk?"checked":"size", "title"}, new String[]{"groupusk", "checkbox", "1", "If set, the results are returned grouped by site and edition, this makes the results quicker to scan through but will disrupt ordering on relevance, if apllicable to the indexs you are using."}, "Group USK Editions");
					optionsList.addChild("li")
						.addChild("input", new String[]{"name", "type", showold?"checked":"size", "title"}, new String[]{"showold", "checkbox", "1", "If set, older editions are shown in the results greyed out, otherwise only the most recent are shown."}, "Show older editions");
					
				HTMLNode newIndexInput = optionsBox.addChild("div", new String[]{"class", "style"}, new String[]{"index", "display: inline-table;"}, "Add an index:");
					newIndexInput.addChild("br");
					newIndexInput.addChild("div", "style", "display: inline-block; width: 50px;", "Name:");
					newIndexInput.addChild("input", new String[]{"name", "type", "class", "title"}, new String[]{"indexname", "text", "index", "If both a name and uri are entered, this index is added as a bookmark"});
					newIndexInput.addChild("br");
					newIndexInput.addChild("div", "style", "display: inline-block; width: 50px;", "URI:");
					newIndexInput.addChild("input", new String[]{"name", "type", "value", "class", "title"}, new String[]{"index", "text", etcIndexes, "index", "URI or path of index to search on, if a name is given above, this index is stored in bookmarks"});




						/*
					.addChild("td", L10nString.getString("Index"))
						.addChild("input", new String[]{"name", "type", "value", "size"}, new String[]{"index", "text", indexuri, "40"});
				searchTable.addChild("tr")
					.addChild("td", L10nString.getString("ShowOldVersions"))
						.addChild("input", new String[]{"name", "type", showold?"checked":"size"}, new String[]{"showold", "checkbox", showold?"checked":"1"});
						 * */
		return searchDiv;
	}


	/**
	 * Draw progress box with bars
	 * @param search
	 * @param indexuri
	 * @param request request to get progress from
	 * @return
	 */
	private HTMLNode progressBox() throws TaskAbortException{
			HTMLNode progressDiv = new HTMLNode("div", "id", "progress");
            // Search description
			HTMLNode progressTable = progressDiv.addChild("table", "width", "100%");
				HTMLNode searchingforCell = progressTable.addChild("tr")
					.addChild("td");
						searchingforCell.addChild("#", L10nString.getString("Searching-for"));
						searchingforCell.addChild("span", "class", "librarian-searching-for-target")
							.addChild("b", query);
						searchingforCell.addChild("#", L10nString.getString("in-index"));
						searchingforCell.addChild("i", indexstring);


				// Search status
				HTMLNode statusRow = progressTable.addChild("tr");
					statusRow.addChild("td")
							.addChild("div", "id", "librarian-search-status")
							.addChild("table", new String[]{"id", "class"}, new String[]{"progress-base", "progress-table"})
							.addChild("tbody")
							.addChild(progressBar(search));
		return progressDiv;
	}

	/**
	 * Put an error on the page, under node, also draws a big grey box around
	 * the error, unless it is an InvalidSearchException in which case it just shows the description
	 */
	public static void addError(HTMLNode node, Throwable error){
//		if(error instanceof InvalidSearchException){	// Print description
//			node.addChild("p", error.getLocalizedMessage());
//			// TODO if there is a cause there should be some way to view this if needed for debugging
//		}else{	// Else print stack trace
			HTMLNode error1 = node.addChild("div", "style", "padding:10px;border:5px solid gray;margin:10px", error.toString());
			for (StackTraceElement ste : error.getStackTrace()){
				error1.addChild("br");
				error1.addChild("#", " -- "+ste.toString());
			}
			if(error.getCause()!=null)
				addError(error1, error.getCause());
//		}
	}

	
	/**
	 * Draw progress bars and describe progress, CompositeProgess are drawn as a table with each row containing a subProgress
	 * @param request
	 * @return
	 */
	public static HTMLNode progressBar(Progress progress) throws TaskAbortException {
		Iterator sizeTestIterator;
		if( progress instanceof CompositeProgress){
			CompositeProgress compProg = (CompositeProgress) progress;
			// Complicated method to check whether this CompositeProgress actually has more than one subProgress
			if((sizeTestIterator =((CompositeProgress) progress).getSubProgress().iterator()).next()!=null && sizeTestIterator.hasNext()) {	// getSubProgress could return a Collection? then i could use size()
				// If it has more than one, draw out a table for it
				HTMLNode row = new HTMLNode("tr");	// TODO there is no point doing this it puts a table in a table
				HTMLNode bar = row.addChild("td").addChild("table", new String[]{"id", "class"}, new String[]{"composite-"+progress.getSubject(), "progress-table"});
				if(compProg.getSubject().matches(".+@.+[ ;].+")){	// TODO better way to asses Index splits
					Iterator it2 = compProg.getSubProgress().iterator();
					while( it2.hasNext()){
						Progress p = (Request)it2.next();
						HTMLNode indexrow = bar.addChild("tr");
						indexrow.addChild("td", p.getSubject().split("@")[1]);
						indexrow.addChild("td").addChild(progressBar(p));
					}
				}else{
					// draw progress bars for subrequests
					Iterator it2 = compProg.getSubProgress().iterator();
					while( it2.hasNext()){
						Request r = (Request)it2.next();
						bar.addChild(progressBar(r));
					}
				}
				return row;
			}else
				// Draw a single bar for the single progress
				return progressBar(compProg.getSubProgress().iterator().next());
		} else {
			// Draw progress bar for single or chained progress
			ProgressParts parts;
			if(progress instanceof ChainedProgress && ((ChainedProgress)progress).getCurrentProgress()!=null)
				parts = ((ChainedProgress)progress).getCurrentProgress().getParts();
			else
				parts = progress.getParts();

			HTMLNode bar = new HTMLNode("tr");
			// search term
			bar.addChild("td", progress.getSubject());
			// search stage
			bar.addChild("td", progress.getStatus());
			// show fetch progress if fetching something
			if(progress.isDone() || progress.getParts().totalest==0){
				bar.addChild("td", ""); bar.addChild("td");
			}else{
				float fractiondone = parts.getKnownFractionDone();
				int percentage = (int)(((float)100)*fractiondone);	// TODO cater for all data and invalid (negative) values
				boolean fetchFinalized = parts.finalizedTotal();
//				Logger.normal(MainPage.class, "Drawing bar for : "+progress.toString() + "  - -  "+parts.toString()+ " - done = "+ percentage+" finalized ? = "+fetchFinalized+ "  fraction done = " + parts.getKnownFractionDone()+" percentage="+percentage+" - "+fractiondone);

				bar.addChild("td", new String[]{"class", "style"}, new String[]{"progress-bar-outline", "padding: 0px 3px;"})
					.addChild("div", new String[]{"class", "style"}, new String[]{fetchFinalized?"progress-bar-inner-final":"progress-bar-inner-nonfinal", "z-index : -1; width:"+percentage+"%;"});
				bar.addChild("td", fetchFinalized?percentage+"%":"Operation length unknown");
			}
			return bar;
		}
	}
	
	
	
	@Override
	public WebPage clone() {
		return new MainPage(library, pr);
	}
	
	public String path() {
		return "/library/";
	}
	
	public String supportedMethods() {
		return "GET, POST";
	}

	public String name() {
		return "WelcomeToadlet.searchFreenet";
	}

	public String menu() {
		return "FProxyToadlet.categoryBrowsing";
	}
}