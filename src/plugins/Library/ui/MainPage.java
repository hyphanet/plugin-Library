/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.ui;

import plugins.Library.Library;
import plugins.Library.search.InvalidSearchException;
import plugins.Library.search.Search;
import plugins.Library.util.exec.ChainedProgress;
import plugins.Library.util.exec.CompositeProgress;
import plugins.Library.util.exec.Progress;
import plugins.Library.util.exec.ProgressParts;
import plugins.Library.util.exec.TaskAbortException;

import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Generates the main search page
 *
 * @author MikeB
 */
class MainPage {
	/** map of search hashes to pages on them */
	private static final HashMap<Integer, MainPage> searchPages = new HashMap<Integer, MainPage>();

	private synchronized static void addpage(int hashCode, MainPage page) {
		searchPages.put(hashCode, page);
	}

	private synchronized static void cleanUpPages(){
		for (Iterator<Integer> it = searchPages.keySet().iterator(); it.hasNext();) {
			if (!Search.hasSearch(it.next()))
					it.remove();
		}
	}

	private synchronized static MainPage getPage(int intParam) {
		return searchPages.get(intParam);
	}



	private final Library library;
	private final PluginRespirator pr;

	private Search search = null;
	private ArrayList<Exception> exceptions = new ArrayList<Exception>();
	private boolean showold = false;
	private boolean js = false;
	private String query = "";
	/** All of the indexes used in the request together seperated by spaces */
	private String indexstring = Library.DEFAULT_INDEX_SITE;
	/** Array of all the bookmark indexes used in this request */
	private ArrayList<String> selectedBMIndexes = new ArrayList<String>();
	/** Any other indexes which are not bookmarks */
	private ArrayList<String> selectedOtherIndexes = new ArrayList<String>();
	/** Any other indexes which are not bookmarks seperated by spaces */
	private boolean groupusk = false;
	private StringBuilder messages = new StringBuilder();

	private String addindexname = "";
	private String addindexuri = "";
	
	static volatile boolean logMINOR;
	static volatile boolean logDEBUG;
	
	static {
		Logger.registerClass(MainPage.class);
	}


	MainPage(Exception e, Library library, PluginRespirator pr) {
		exceptions.add(e);
		this.library = library;
		this.pr = pr;
	}


	/**
	 * FIXME ajaxy stuff isnt working, it doesnt seem to renew the timeout in the js, it stopped working when the response type of the xml was changed to text/xml
	 * @param library
	 * @param pr
	 */
	MainPage(Library library, PluginRespirator pr) {
		this.library = library;
		this.pr = pr;
	}

	/**
	 * Process a get request, the only parameter allowed for a get request is
	 * request id (for an ongoing request)
	 */
	public static MainPage processGetRequest(HTTPRequest request){
		if (request.isParameterSet("request") && searchPages.containsKey(request.getIntParam("request"))){
			return getPage(request.getIntParam("request"));
		}
		return null;
	}

	/** post commands */
	private static enum Commands {
		/** performs a search */
		find,
		/** adds a new index to the bookmarks in Library */
		addbookmark,
		/** puts an index in the add bookmark box to be named, requires an integer parameter between 0 and "extraindexcount" to specify which index is being added */
		addindex,
		/** deletes a bookmark from the Library, requires an integer parameter between 0 and the number of bookmarks */
		removebookmark
	}

	/**
	 * Process a post request
	 * @param userAccess 
	 *
	 * @see plugins.XMLSpider.WebPage#processPostRequest(freenet.support.api.HTTPRequest,
	 * freenet.support.HTMLNode)
	 */
	public static MainPage processPostRequest(HTTPRequest request, HTMLNode contentNode, boolean hasFormPassword, boolean userAccess, Library library, PluginRespirator pr ) {
		cleanUpPages();
		MainPage page = new MainPage(library, pr);

		page.js = request.isPartSet("js");
		page.showold = request.isPartSet("showold");
		page.groupusk = request.isPartSet("groupusk");
		String[] etcIndexes = request.getPartAsStringFailsafe("indexuris", 256).trim().split("[ ;]");
		page.query = request.getPartAsStringFailsafe("search", 256);

		if(userAccess) {
			page.addindexname = request.getPartAsStringFailsafe("addindexname", 32);
			page.addindexuri = request.getPartAsStringFailsafe("addindexuri", 256);
		}

		// Get bookmarked index list
		page.indexstring = "";
		for (String bm : library.bookmarkKeys()){
			String bmid = (Library.BOOKMARK_PREFIX + bm).trim();
			if(request.isPartSet("~"+bm)){
				page.indexstring += bmid + " ";
				page.selectedBMIndexes.add(bmid);
			}
		}
		// Get other index list
		for (int i = 0; i < request.getIntPart("extraindexcount", 0); i++) {
			if (request.isPartSet("index"+i)){
				String otherindexuri = request.getPartAsStringFailsafe("index"+i, 256);
				page.indexstring += otherindexuri + " ";
				page.selectedOtherIndexes.add(otherindexuri);
			}
		}
		for (String string : etcIndexes) {
			if(string.length()>0){
				page.indexstring += string + " ";
				page.selectedOtherIndexes.add(string);
			}
		}
		page.indexstring = page.indexstring.trim();

		if("".equals(page.indexstring))
			page.indexstring = Library.DEFAULT_INDEX_SITE;

		if(page.selectedBMIndexes.size() == 0 && page.selectedOtherIndexes.size() == 0)
			page.selectedBMIndexes.add(Library.DEFAULT_INDEX_SITE);





		// get search query
		if (request.isPartSet(Commands.find.toString())){
			if(hasFormPassword) {
				// Start or continue a search
				try {
					if(logMINOR)
						Logger.minor(MainPage.class, "starting search for "+page.query+" on "+page.indexstring);
					page.search = Search.startSearch(page.query, page.indexstring);
					if(page.search == null)
						page.messages.append("Stopwords too prominent in search term, try removing words like 'the', 'and' and 'that' and any words less than 3 characters");
					else{
						page.search.setMakeResultNode(page.groupusk, page.showold, true);	// for the moment js will always be on for results, js detecting isnt being used
						
						// at this point pages is in a state ready to be saved
						addpage(page.search.hashCode(), page);
					}
				} catch (InvalidSearchException ex) {
					page.messages.append("Problem with search : "+ex.getLocalizedMessage()+"\n");
				} catch (TaskAbortException ex) {
					page.exceptions.add(ex);		// TODO handle these exceptions separately
				} catch (RuntimeException ex){
					page.exceptions.add(ex);
				}
			}
		}else if (request.isPartSet(Commands.addbookmark.toString())) {
			try {
				// adding an index
				// TODO make sure name is valid
				if (userAccess && hasFormPassword && page.addindexname.length() > 0 && URLEncoder.encode(page.addindexname, "UTF-8").equals(page.addindexname)) {
					// Use URLEncoder to have a simple constraint on names
					library.addBookmark(page.addindexname, page.addindexuri);
					if (page.selectedOtherIndexes.contains(page.addindexuri)) {
						page.selectedOtherIndexes.remove(page.addindexuri);
						page.selectedBMIndexes.add(Library.BOOKMARK_PREFIX + page.addindexname);
					}
					page.addindexname = "";
					page.addindexuri = "";
				}
			} catch (UnsupportedEncodingException ex) {
				Logger.error(page, "Encoding error", ex);
			}
		}else{	// check if one of the other indexes is being added as a bookmark, this doesnt actually add it but moves it into the add box
			for (int i = 0; i < request.getIntPart("extraindexcount", 0); i++) { // TODO make sure name is valid
				if (request.isPartSet(Commands.addindex.toString()+i))
					page.addindexuri = request.getPartAsStringFailsafe("index"+i, 256);
			}
			if(userAccess && hasFormPassword)
				// check if one of the bookmarks is being removed
				for (String bm : library.bookmarkKeys()) {
					if (request.isPartSet(Commands.removebookmark+bm)){
						library.removeBookmark(bm);
						break;
					}
				}
		}

		return page;
	}



	/**
	 * Write the search page out
	 *
	 *
	 * @see plugins.XMLSpider.WebPage#writeContent(freenet.support.api.HTTPRequest,
	 * freenet.support.HTMLNode)
	 */
	public void writeContent(HTMLNode contentNode, MultiValueTable<String, String> headers) {
		HTMLNode errorDiv = contentNode.addChild("div", "id", "errors");

		for (Exception exception : exceptions) {
			addError(errorDiv, exception, messages);
		}

		try{
			//Logger.normal(this, "Writing page for "+ query + " " + search + " " + indexuri);
			contentNode.addChild("script", new String[]{"type", "src"}, new String[]{"text/javascript", "/library/static/script.js"}).addChild("%", " ");

			// Generate the url to refresh to to update the progress
			String refreshURL = null;
			if ( search != null)
				refreshURL = path()+"?request="+search.hashCode();




			if(search == null)
				contentNode.addChild("#",L10nString.getString("page-warning"));
			
			contentNode.addChild(searchBox());




			// If showing a search
			if(search != null){
				// show progress
				contentNode.addChild(progressBox());
				// If search is complete show results
				if (search.isDone()) {
					if(search.hasGeneratedResultNode()){
						contentNode.addChild(search.getHTMLNode());
					}else
						try {
							ResultNodeGenerator nodegenerator = new ResultNodeGenerator(search.getResult(), groupusk, showold, true); // js is being switch on always currently due to detection being off
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




			// Don't refresh if there is no request option, if it is finished or there is an error to show or js is enabled
			if (search != null && !"".equals(search.getQuery()) && !search.isDone() && exceptions.size() <= 0) {
				// refresh will GET so use a request id
				if (!js) {
					headers.put("Refresh", "2;url=" + refreshURL);
				}
			}
		}catch(TaskAbortException e) {
			if(search != null)
				search.remove();
			exceptions.add(e);
			search = null;
			addError(errorDiv, e, messages);
		}

		// Show any errors
		errorDiv.addChild("p", messages.toString());
	}


	/**
	 * Create search form
	 *
	 * // @param search already started
	 * // @param indexuri
	 * // @param js whether js has been detected
	 * // @param showold
	 *
	 * @return an {@link HTMLNode} representing the search form
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
					for (String bm : library.bookmarkKeys()) {
						HTMLNode bmItem = indexeslist.addChild("li");
						bmItem.addChild("input", new String[]{"name", "type", "value", "title", (selectedBMIndexes.contains(Library.BOOKMARK_PREFIX+bm) ? "checked" : "size" )}, new String[]{"~"+bm, "checkbox", Library.BOOKMARK_PREFIX+bm, "Index uri : "+library.getBookmark(bm), "1" } , bm);
						bmItem.addChild("input", new String[]{"name", "type", "value", "title", "class"}, new String[]{Commands.removebookmark+bm, "submit", "X", "Delete this bookmark", "index-bookmark-delete" });
					}
					int i=0;
					for (String uri : selectedOtherIndexes) {
						HTMLNode removeItem = indexeslist.addChild("li");
							String showuri;
							try {
								showuri = (new FreenetURI(uri)).toShortString();
							} catch (MalformedURLException e) {
								showuri = uri;
							}
							removeItem.addChild("input", new String[]{"type", "name", "value", "checked"}, new String[]{"checkbox", "index"+i, uri, "checked", } , showuri);
							removeItem.addChild("input", new String[]{"name", "type", "value"}, new String[]{Commands.addindex.toString()+i, "submit", "Add=>" });
							i++;
					}
					indexeslist.addChild("input", new String[]{"name", "type", "value"}, new String[]{"extraindexcount", "hidden", ""+selectedOtherIndexes.size()});
					indexeslist.addChild("li")
							.addChild("input", new String[]{"name", "type", "value", "class", "title"}, new String[]{"indexuris", "text", "", "index", "URI or path of other index(s) to search on"});


				HTMLNode optionsBox = searchForm.addChild("div", "style", "margin: 20px 0px 20px 20px; display: inline-table; text-align: left;", "Options");
					HTMLNode optionsList = optionsBox.addChild("ul", "class", "options-list");
						optionsList.addChild("li")
							.addChild("input", new String[]{"name", "type", groupusk?"checked":"size", "title"}, new String[]{"groupusk", "checkbox", "1", "If set, the results are returned grouped by site and edition, this makes the results quicker to scan through but will disrupt ordering on relevance, if applicable to the indexs you are using."}, "Group sites and editions");
						optionsList.addChild("li")
							.addChild("input", new String[]{"name", "type", showold?"checked":"size", "title"}, new String[]{"showold", "checkbox", "1", "If set, older editions are shown in the results greyed out, otherwise only the most recent are shown."}, "Show older editions");

					HTMLNode newIndexInput = optionsBox.addChild("div", new String[]{"class", "style"}, new String[]{"index", "display: inline-table;"}, "Add an index:");
						newIndexInput.addChild("br");
						newIndexInput.addChild("div", "style", "display: inline-block; width: 50px;", "Name:");
						newIndexInput.addChild("input", new String[]{"name", "type", "class", "title", "value"}, new String[]{"addindexname", "text", "index", "Name of the bookmark, this will appear in the list to the left", addindexname});
						newIndexInput.addChild("br");
						newIndexInput.addChild("div", "style", "display: inline-block; width: 50px;", "URI:");
						newIndexInput.addChild("input", new String[]{"name", "type", "class", "title", "value"}, new String[]{"addindexuri", "text", "index", "URI or path of index to add to bookmarks, including the main index filename at the end of a Freenet uri will help Library not to block in order to discover the index type.", addindexuri});
						newIndexInput.addChild("br");
						newIndexInput.addChild("input", new String[]{"name", "type", "value"}, new String[]{"addbookmark", "submit", "Add Bookmark"});
		}else
			searchDiv.addChild("#", "No PluginRespirater, so Form cannot be displayed");
		return searchDiv;
	}


	/**
	 * Draw progress box with bars
	 *
	 * // @param search
	 * // @param indexuri
	 * // @param request request to get progress from
	 *
	 * @return an {@link HTMLNode} representing a progress box
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
							.addChild(progressBar(search, true));
		return progressDiv;
	}

	/**
	 * Put an error on the page, under node, also draws a big grey box around
	 * the error, unless it is an InvalidSearchException in which case it just shows the description
	 */
	public static void addError(HTMLNode node, Throwable error, StringBuilder messages){
		if(messages != null &&
				(error instanceof InvalidSearchException || error instanceof TaskAbortException) && !logMINOR){	// Print description
			messages.append(error.getMessage()+"\n");
			// TODO if there is a cause there should be some way to view this if needed for debugging
		}else{	// Else print stack trace
			HTMLNode error1 = node.addChild("div", "style", "padding:10px;border:5px solid gray;margin:10px", error.toString());
			for (StackTraceElement ste : error.getStackTrace()){
				error1.addChild("br");
				error1.addChild("#", " -- "+ste.toString());
			}
			if (error.getCause()!=null)
				addError(error1, error.getCause(), messages);
		}
	}


	/**
	 * Draw progress bars and describe progress, CompositeProgess are drawn as a table with each row containing a subProgress
	 * @param progress The progress to represent
	 * @return an {@link HTMLNode} representing a progress bar
	 */
	public static HTMLNode progressBar(Progress progress, boolean canFail) throws TaskAbortException {
		synchronized (progress){
			if (progress instanceof CompositeProgress && ((CompositeProgress) progress).getSubProgress()!=null && ((CompositeProgress) progress).getSubProgress().iterator().hasNext()){
				// Put together progress bars for all the subProgress
				HTMLNode block = new HTMLNode("#");
				block.addChild("tr").addChild("td", "colspan", "6", progress.getSubject() + " : "+progress.getStatus());
				TaskAbortException firstError = null;
				boolean anySuccess = false;
				if (canFail && progress instanceof Search) {
					if(!(((Search)progress).innerCanFailAndStillComplete()))
						canFail = false;
				} else canFail = false;
				if (((CompositeProgress) progress).getSubProgress() != null)
					for (Progress progress1 : ((CompositeProgress) progress).getSubProgress()) {
						try {
							block.addChild(progressBar(progress1, canFail));
							anySuccess = true;
						} catch (TaskAbortException e) {
							if(!canFail) throw e;
							if(firstError == null) firstError = e;
							block.addChild("tr").addChild("td", "colspan", "6", progress1.getSubject() + " : "+L10nString.getString("failed") + " : "+e.getMessage());
						}
					}
				if(firstError != null && !anySuccess)
					throw firstError;
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

	public String getQuery() {
		return query;
	}
}
