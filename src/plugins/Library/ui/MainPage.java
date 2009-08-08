package plugins.Library.ui;


import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;
import java.util.ArrayList;
import java.util.Iterator;
import plugins.Library.Library;
import plugins.Library.index.CompositeRequest;
import plugins.Library.index.Request;
import plugins.Library.index.Request.RequestState;
import plugins.Library.search.InvalidSearchException;
import plugins.Library.search.Search;
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

		if (request.isParameterSet("request")){
			search = Search.getSearch(request.getIntParam("request"));
			if(search!=null){
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
				Logger.normal(this, "Refreshing request "+request.getIntParam("request")+" " +query);
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
		
		// Don't refresh if there is no request option, if it is finished or there is an error to show or js is enabled
		if(search!=null && !"".equals(search.getQuery()) && !search.isDone() && exceptions.size()<=0){
			// refresh will GET so use a request id
			if(!js)
				headers.put("Refresh", "2;url="+path()+"?request="+search.hashCode() + (showold?"&showold=on":""));
			// The JS isn't working again, so it's disabled again
			//contentNode.addChild("script", new String[]{"type", "src"}, new String[]{"text/javascript", path() + "static/" + (js ? "scriptjs" : "detectjs") + "?request="+search.hashCode()+(showold?"&showold=on":"")}).addChild("%", " ");
		}

		
        // Start of body
		contentNode.addChild(searchBox());


        // Show any errors
		HTMLNode errorDiv = new HTMLNode("div", "id", "errors");
		for (Exception exception : exceptions) {
			addError(errorDiv, exception);
		}
		contentNode.addChild(errorDiv);


        // If showing a search
        if(search != null){
			// show progress
			contentNode.addChild(progressBox());

            // If search is complete show results
            if (search.getState()==RequestState.FINISHED)
				try{
					ResultNodeGenerator nodegenerator = new ResultNodeGenerator(search.getResult(), true);
					contentNode.addChild(nodegenerator.generatePageEntryNode(showold, js));
				}catch(TaskAbortException ex){
					addError(errorDiv, ex);
				}catch(RuntimeException ex){
					addError(errorDiv, ex);
				}
			else
				contentNode.addChild("div", "id", "results").addChild("#");
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
				searchBox.addChild("input", new String[]{"name", "size", "type", "value"}, new String[]{"search", "40", "text", query});
				searchBox.addChild("input", new String[]{"name", "type", "value", "tabindex"}, new String[]{"find", "submit", "Find!", "1"});
				if(js)
					searchBox.addChild("input", new String[]{"type","name"}, new String[]{"hidden","js"});
				// Shows the list of bookmarked indexes TODO show descriptions on mouseover ??
				HTMLNode indexeslist = searchBox.addChild("ul", "class", "index-bookmark-list", "Select indexes");
				for (String bm : library.bookmarkKeys()){
					//Logger.normal(this, "Checking for bm="+Library.BOOKMARK_PREFIX+bm+" in \""+indexuri + " = " + selectedBMIndexes.contains(Library.BOOKMARK_PREFIX+bm)+" "+indexuri.contains(Library.BOOKMARK_PREFIX+bm));
					indexeslist.addChild("li")
						.addChild("input", new String[]{"type", "name", "value", (selectedBMIndexes.contains(Library.BOOKMARK_PREFIX+bm) ? "checked" : "size" )}, new String[]{"checkbox", "~"+bm, Library.BOOKMARK_PREFIX+bm, "1" } , bm);
				}

			HTMLNode optionsBox = searchForm.addChild("div", "style", "margin: 20px 0px 20px 20px; display: inline-table; text-align: left;", "Options");
				HTMLNode optionsList = optionsBox.addChild("ul", "class", "options-list");
//					optionsList.addChild("li")
//						.addChild("input", new String[]{"name", "type"}, new String[]{"groupusk", "checkbox"}, "Group USK Editions");
					optionsList.addChild("li")
						.addChild("input", new String[]{"name", "type", showold?"checked":"size"}, new String[]{"showold", "checkbox", "1"}, "Show older editions");
//					optionsList.addChild("li")
//						.addChild("input", new String[]{"name", "type"}, new String[]{"sort", "checkbox"}, "Sort by relevence");
				HTMLNode newIndexInput = optionsBox.addChild("div", new String[]{"class", "style"}, new String[]{"index", "display: inline-table;"}, "Add an index:");
					newIndexInput.addChild("br");
					newIndexInput.addChild("div", "style", "display: inline-block; width: 50px;", "Name:");
					newIndexInput.addChild("input", new String[]{"type", "class"}, new String[]{"text", "index"});
					newIndexInput.addChild("br");
					newIndexInput.addChild("div", "style", "display: inline-block; width: 50px;", "URI:");
					newIndexInput.addChild("input", new String[]{"name", "type", "value", "class"}, new String[]{"index", "text", etcIndexes, "index"});




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
	private HTMLNode progressBox(){
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
							.addChild("table", new String[]{"id", "class"}, new String[]{"progress-table", "progress-table"})
							.addChild(progressBar(search));
		return progressDiv;
	}

	/**
	 * Put an error on the page, under node, also draws a big grey box around the error
	 */
	public static void addError(HTMLNode node, Throwable error){
		Logger.error(MainPage.class, "Exception caught by interface : "+error.getMessage(), error);
		HTMLNode error1 = node.addChild("div", "style", "padding:10px;border:5px solid gray;margin:10px", error.toString());
		for (StackTraceElement ste : error.getStackTrace()){
			error1.addChild("br");
			error1.addChild("#", " -- "+ste.toString());
		}
		if(error.getCause()!=null)
			addError(error1, error.getCause());
	}

	
	/**
	 * Draw progress bars and describe progress
	 * FIXME doesnt seem to be displaying index names for multi searches
	 * @param request
	 * @return
	 */
	public static HTMLNode progressBar(Request request) {
		HTMLNode bar;
		// If it doesn't have subrequests, draw it's progress
		if(! ( request instanceof CompositeRequest ) ){
			bar = new HTMLNode("tr");
			// search term
			bar.addChild("td", request.getSubject());
			// search stage
			bar.addChild("td",
				(request.getState()==RequestState.INPROGRESS ||
				 request.getState()==RequestState.PARTIALRESULT) ? request.getCurrentStage()
				                                                          : request.getState().toString());
			// show fetch progress if fetching something
			if(request.isDone() || request.partsTotal()==0){
				bar.addChild("td", ""); bar.addChild("td");
			}else{
				int percentage = (int)(100*request.partsDone()/request.partsTotal());
				boolean fetchFinalized = request.isTotalFinal();
				bar
					.addChild("td", new String[]{"class"}, new String[]{"progress-bar-outline"})
					.addChild("div", new String[]{"class", "style"}, new String[]{fetchFinalized?"progress-bar-inner-final":"progress-bar-inner-nonfinal", "z-index : -1; width:"+percentage+"%;"});
				bar.addChild("td", fetchFinalized?percentage+"%":"Fetch length unknown");

			}
		// if request separates indexes, show their names
		} else {
			CompositeRequest compRequest = (CompositeRequest) request;
			if(request.getSubject().matches(".+%.+[ ;].+")){
				bar = new HTMLNode("tbody");
				Iterator it = compRequest.getSubRequests().iterator();
				while( it.hasNext()){
					Request r = (Request)it.next();
					HTMLNode indexrow = bar.addChild("tr");
					indexrow.addChild("td", r.getSubject().split("%")[1]);
					indexrow.addChild("td").addChild("table", "class", "progress-table").addChild(progressBar((Request)r));
				}
			// get progress for subrequests
			}else{
				bar = new HTMLNode("#");
				Iterator it = compRequest.getSubRequests().iterator();
				while( it.hasNext()){
					Request r = (Request)it.next();
					bar.addChild(progressBar((Request)r));
				}
			}
		}
		return bar;
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
		return "Search";
	}
}