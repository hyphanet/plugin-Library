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
import plugins.Library.Library;
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
class MainPage {
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
	private boolean groupusk = false;
	private StringBuilder messages = new StringBuilder();

	// For when a freesite requests that a bookmark be added, user authorization is needed
	private boolean authorize = false;
	private String addindexname;
	private String addindexuri;


	MainPage(Exception e, Library library, PluginRespirator pr) {
		exceptions.add(e);
		this.library = library;
		this.pr = pr;
	}
	
	
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

		if (request.isParameterSet("request") && Search.hasSearch(request.getIntParam("request"))){
			search = Search.getSearch(request.getIntParam("request"));
			search.setMakeResultNode(groupusk, showold, js);
			if(search!=null){
				if(request.isParameterSet("indexname") && request.getParam("indexname").length() > 0){
					library.addBookmark(request.getParam("indexname"), request.getParam("index"));
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
		if(request.isPartSet("indexname") && request.getPartAsString("indexname", 20).length() > 0 && request.isPartSet("index") && request.getPartAsString("index", 128).length() > 0){
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
				if(search == null)
					messages.append("Stopwords too prominent in search term, try removing words like 'the, 'and' and 'that' and any words less than 3 characters");
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
		try{
			//Logger.normal(this, "Writing page for "+ query + " " + search + " " + indexuri);

			// Generate the url to refresh to to update the progress
			String refreshURL = null;
			if ( search != null)
				refreshURL = path()+"?request="+search.hashCode() + (showold?"&showold=on":"") + (groupusk?"&groupusk=on":"");



			// Start of body
			// authorization box, gives the user a choice of whther to authorize something
			if(authorize){
				HTMLNode authorizeBox = contentNode.addChild("div", "class", "authorization-box");
				authorizeBox.addChild("h1", "Your authorization required :");
				HTMLNode bookmarkBox = authorizeBox.addChild("div", "Whatever started this request is trying to add a bookmark to your index bookmarks, do you want to add this index bookmark?");
				bookmarkBox.addChild("br");
				bookmarkBox.addChild("#", "Name : ");
				bookmarkBox.addChild("b", "\""+addindexname+"\"");
				bookmarkBox.addChild("br");
				bookmarkBox.addChild("#", "URI : ");
				bookmarkBox.addChild("b", "\""+addindexuri+"\"");
				bookmarkBox.addChild("br");
				bookmarkBox.addChild("a", "href", refreshURL + "&indexname="+addindexname+"&index="+addindexuri, "Add to index bookmarks");
				bookmarkBox.addChild("br");
				bookmarkBox.addChild("a", "href", refreshURL, "Do not add to bookmarks");
			}
			contentNode.addChild(searchBox());


			// Show any errors
			HTMLNode errorDiv = contentNode.addChild("div", "id", "errors");
			contentNode.addChild("p", messages.toString());


			// If showing a search
			if(search != null){
				// show progress
				contentNode.addChild(progressBox());
				// If search is complete show results
				if (search.isDone()) {
					if(search.hasGeneratedResultNode()){
						contentNode.addChild(search.getHTMLNode());
						Logger.normal(this, "Got pre generated result node.");
					}else
						try {
							Logger.normal(this, "Blocking to generate resultnode.");
							ResultNodeGenerator nodegenerator = new ResultNodeGenerator(search.getResult(), groupusk, showold, js);
							nodegenerator.run();
							contentNode.addChild(nodegenerator.getPageEntryNode());
						} catch (TaskAbortException ex) {
							exceptions.add(ex);
						} catch (RuntimeException ex) {
							exceptions.add(ex);
						}
				} else {
					contentNode.addChild("div", "id", "results").addChild("#");
				}
			}


			for (Exception exception : exceptions) {
				addError(errorDiv, exception);
			}


			// Don't refresh if there is no request option, if it is finished or there is an error to show or js is enabled
			if (search != null && !"".equals(search.getQuery()) && !search.isDone() && exceptions.size() <= 0) {
				// refresh will GET so use a request id
				if (!js && !authorize) {
					headers.put("Refresh", "2;url=" + refreshURL);
					//contentNode.addChild("script", new String[]{"type", "src"}, new String[]{"text/javascript", path() + "static/" + (js ? "script.js" : "detect.js") + "?request="+search.hashCode()+(showold?"&showold=on":"")}).addChild("%", " ");
					//contentNode.addChild("script", new String[]{"type", "src"}, new String[]{"text/javascript", path() + "static/" + (js ? "script.js" : "detect.js") + "?request="+search.hashCode()+(showold?"&showold=on":"")}).addChild("%", " ");
				}
			}
		}catch(TaskAbortException e) {
			exceptions.add(e);
			search = null;
			writeContent(contentNode, headers);
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
		HTMLNode searchDiv = new HTMLNode("div", new String[]{"id", "style"}, new String[]{"searchbar", "text-align: center;"});
		if(pr!=null){
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
							.addChild("input", new String[]{"name", "type", groupusk?"checked":"size", "title"}, new String[]{"groupusk", "checkbox", "1", "If set, the results are returned grouped by site and edition, this makes the results quicker to scan through but will disrupt ordering on relevance, if applicable to the indexs you are using."}, "Group sites and editions");
						optionsList.addChild("li")
							.addChild("input", new String[]{"name", "type", showold?"checked":"size", "title"}, new String[]{"showold", "checkbox", "1", "If set, older editions are shown in the results greyed out, otherwise only the most recent are shown."}, "Show older editions");

					HTMLNode newIndexInput = optionsBox.addChild("div", new String[]{"class", "style"}, new String[]{"index", "display: inline-table;"}, "Add an index:");
						newIndexInput.addChild("br");
						newIndexInput.addChild("div", "style", "display: inline-block; width: 50px;", "Name:");
						newIndexInput.addChild("input", new String[]{"name", "type", "class", "title"}, new String[]{"indexname", "text", "index", "If both a name and uri are entered, this index is added as a bookmark"});
						newIndexInput.addChild("br");
						newIndexInput.addChild("div", "style", "display: inline-block; width: 50px;", "URI:");
						newIndexInput.addChild("input", new String[]{"name", "type", "value", "class", "title"}, new String[]{"index", "text", etcIndexes, "index", "URI or path of index to search on, if a name is given above, this index is stored in bookmarks"});
		}else
			searchDiv.addChild("#", "No PluginRespirater, so Form cannot be displayed");
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
		synchronized (progress){
			if( progress instanceof CompositeProgress && ((CompositeProgress) progress).getSubProgress()!=null && ((CompositeProgress) progress).getSubProgress().iterator().hasNext()){
				// Put together progress bars for all the subProgress
				HTMLNode block = new HTMLNode("#");
				block.addChild("tr").addChild("td", "colspan", "6", progress.getSubject() + " : "+progress.getStatus());
				if(((CompositeProgress) progress).getSubProgress() != null)
					for (Progress progress1 : ((CompositeProgress) progress).getSubProgress()) {
						block.addChild(progressBar(progress1));
					}
				return block;
			} else {
				// Draw progress bar for single or chained progress
				ProgressParts parts;
				if(progress instanceof ChainedProgress && ((ChainedProgress)progress).getCurrentProgress()!=null)
					parts = ((ChainedProgress)progress).getCurrentProgress().getParts();
				else
					parts = progress.getParts();

				HTMLNode bar = new HTMLNode("tr");
				bar.addChild("td");
				// search term
				bar.addChild("td", progress.getSubject());
				// search stage
				bar.addChild("td", progress.getStatus());
				// show fetch progress if fetching something
				if(progress.isDone() || progress.getParts().known==0){
					bar.addChild("td", ""); bar.addChild("td");
				}else{
					float fractiondone = parts.getKnownFractionDone();
					int percentage = (int)(((float)100)*fractiondone);	// TODO cater for all data and invalid (negative) values
					boolean fetchFinalized = parts.finalizedTotal();

					bar.addChild("td", new String[]{"class", "style"}, new String[]{"progress-bar-outline", "padding: 0px 3px;"})
						.addChild("div", new String[]{"class", "style"}, new String[]{fetchFinalized?"progress-bar-inner-final":"progress-bar-inner-nonfinal", "z-index : -1; width:"+percentage+"%;"});
					bar.addChild("td", fetchFinalized?percentage+"%":"Operation length unknown");
				}
				return bar;
			}
		}
	}



	public static String path() {
		return "/library/";
	}
}
