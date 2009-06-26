package plugins.XMLLibrarian.interfaces;

import freenet.pluginmanager.PluginHTTPException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;
import freenet.support.HTMLEncoder;
import freenet.l10n.L10n;

import plugins.XMLLibrarian.Index;
import plugins.XMLLibrarian.Request;
import plugins.XMLLibrarian.Search;
import plugins.XMLLibrarian.XMLLibrarian;


/**
 * Provides the HTML generation for the search page
 * @author MikeB
 */
public class WebUI{
	static String plugName;
	static XMLLibrarian xl;
	
	public static void setup(XMLLibrarian xl, String plugName){
		WebUI.plugName = plugName;
		WebUI.xl = xl;
	}


	/**
	 * Decide what to do depending on the request
	 * @param request
	 * @return String of generated HTML to be sent to browser
	 * @throws freenet.pluginmanager.PluginHTTPException
	 */
	public static String handleHTTPGet(HTTPRequest request) throws PluginHTTPException{
		String searchstring = request.getParam("search");
		String indexuri = request.isParameterSet("index") ? request.getParam("index") : XMLLibrarian.DEFAULT_INDEX_SITE;
		
//		if(request.getPath().endsWith("progress")){ // mini progress display for use with JS
//			if (Search.hasSearch(searchstring, indexuri))
//				return Search.getSearch(searchstring, indexuri).getprogress("format");
//			else
//				return "No asyncronous search for "+HTMLEncoder.encode(searchstring)+" found.";
//
//		}else if(request.getPath().endsWith("result")){ // just get result for JS
//			if (Search.hasSearch(searchstring, indexuri)){
//				return Search.getSearch(searchstring, indexuri).getresult();
//			}else return "No asyncronous search for "+HTMLEncoder.encode(searchstring)+" found.";
//
//		}else
		if(request.getPath().endsWith("debug")){
			return WebUI.debugpage();
		}
		if(request.getPath().endsWith("purgeSearches")){
			Search.purgeSearches();
			return WebUI.searchpage(indexuri);
		}
		if(request.getPath().endsWith("listSearches"))
			return WebUI.listSearches();

		if(searchstring == null || searchstring.equals("")){   // no search main
			// generate HTML and set it to no refresh
			return WebUI.searchpage(indexuri);
			
		}else{  // Full main searchpage
			Search searchobject = null;
			
			try{
				//get Search object
				searchobject = Search.startSearch(searchstring, indexuri);
				
				// generate HTML for search object and set it to refresh
				return searchpage(searchobject, indexuri, true, null);
			}catch(Exception e){
				return searchpage(searchobject, indexuri, true, e);
			}
		}
	}
    
	public static String handleHTTPPost(HTTPRequest request) throws PluginHTTPException{
        return searchpage(null);
    }



	/**
	 * Build an empty search page with no refresh
	 * @param indexuri an index to put in the index box
	 **/
	public static String searchpage(String indexuri){
		return searchpage(null, indexuri, false, null);
	}
	
	
    /**
     * Build a search page for search in it's current state
	 * @param request the request this page should be built to show the progress of
	 * @param indexuri the index to show in the index box
	 * @param refresh a preference as to whether this page should refresh, refresh is switched off in the event of an error or the request being finished
	 * @param e any exception which should be reported on the page
     **/
    public static String searchpage(Search request, String indexuri, boolean refresh, Exception e){
		if(request==null || "".equals(request.getQuery()) || request.isFinished() || e!=null)
			refresh = false;
			
		
        // Show any errors
		HTMLNode errorDiv = new HTMLNode("div", "id", "errors");
        if (e != null){
            addError(errorDiv, e);
		}
		if (request != null && request.getRequestStatus() == Request.RequestStatus.ERROR)
			addError(errorDiv, request.getError());
			
		String search = "";
		try{
			search = request !=null ? HTMLEncoder.encode(request.getQuery()) : "";
			if(indexuri == null || indexuri.equals(""))
				indexuri = request !=null  ? HTMLEncoder.encode(request.getIndexURI()) : XMLLibrarian.DEFAULT_INDEX_SITE;
		}catch(Exception exe){
			addError(errorDiv, exe);
		}

		String title = plugName;
		if(search != null && !search.equals("") && indexuri != null && !indexuri.equals(""))
			title = "\"" + search + "\" - "+plugName;

			
			
		HTMLNode pageNode = new HTMLNode.HTMLDoctype("html", "-//W3C//DTD XHTML 1.1//EN");
		HTMLNode htmlNode = pageNode.addChild("html", "xml:lang", L10n.getSelectedLanguage().isoCode);
		HTMLNode headNode = htmlNode.addChild("head");
		if(refresh)
            headNode.addChild("meta", new String[] { "http-equiv", "content" }, new String[] { "refresh", "1" });
		headNode.addChild("meta", new String[] { "http-equiv", "content" }, new String[] { "Content-Type", "text/html; charset=utf-8" });
		headNode.addChild("title", title);
		//headNode.addChild("link", new String[] { "rel", "href", "type", "title" }, new String[] { "stylesheet", "/static/themes/" + theme.code + "/theme.css", "text/css", theme.code });
		
		HTMLNode bodyNode = htmlNode.addChild("body");
		


        // Start of body
		HTMLNode searchDiv = bodyNode.addChild("div", "id", "searchbar");
		HTMLNode searchForm = searchDiv.addChild("form", new String[]{"method", "action"}, new String[]{"GET", "plugins.XMLLibrarian.XMLLibrarian"});
			HTMLNode searchTable = searchForm.addChild("table", "width", "100%");
				HTMLNode searchTop = searchTable.addChild("tr");
					HTMLNode titleCell = searchTop.addChild("td", new String[]{"rowspan","width"},new String[]{"2","120"});
						titleCell.addChild("H1", plugName);
						titleCell.addChild(searchList());
					HTMLNode searchcell = searchTop.addChild("td", "width", "400");
						searchcell.addChild("input", new String[]{"name", "size", "type", "value"}, new String[]{"search", "40", "text", search});
						searchcell.addChild("input", new String[]{"name", "type", "value", "tabindex"}, new String[]{"find", "submit", "Find!", "1"});
				
				searchTable.addChild("tr")
					.addChild("td", xl.getString("Index"))
						.addChild("input", new String[]{"name", "type", "value", "size"}, new String[]{"index", "text", indexuri, "40"});

		bodyNode.addChild("br");
		
		bodyNode.addChild(errorDiv);


        // If showing a search
        if(request != null){
			HTMLNode progressDiv = bodyNode.addChild("div", "id", "progress");
            // Search description
			HTMLNode progressTable = progressDiv.addChild("table", "width", "100%");
				HTMLNode searchingforCell = progressTable.addChild("tr")
					.addChild("td", "colspan", "2");
						searchingforCell.addChild("#", xl.getString("Searching-for"));
						searchingforCell.addChild("span", "class", "librarian-searching-for-target")
							.addChild("b", search);
						searchingforCell.addChild("#", xl.getString("in-index"));
						searchingforCell.addChild("i", indexuri);
			

				// Search status
				HTMLNode statusRow = progressTable.addChild("tr");
					statusRow.addChild("td", "width", "140", xl.getString("Search-status"));
					statusRow.addChild("td")
							.addChild(buildProgressNode(request));
			
			bodyNode.addChild("p");

            // If search is complete show results
            if (request.hasResult())
				try{
					bodyNode.addChild(request.getResultNode());
				}catch(Exception ex){
					addError(errorDiv, ex);
				}
        }

		return pageNode.generate();
    }

	private static String debugpage() {
		HTMLNode debugpage = new HTMLNode("HTML");
		HTMLNode bodynode = debugpage.addChild("body");
		for(Index i : Index.getAllIndices()){
			HTMLNode indexnode = bodynode.addChild("p");
			indexnode.addChild("#",i.toString());
		}
		return debugpage.generate();
	}

	/**
	 * Build a node about the status of a request
	 * @return
	 */
	private static HTMLNode buildProgressNode(Request request) {
		HTMLNode node = new HTMLNode("div", "id", "librarian-search-status");
		node.addChild("p", "Status : "+request.getSubject()+"  "+request.getRequestStatus()+", Stage: "+request.getSubStage()+"/"+request.getSubStageCount()+", Blocks:"+request.getNumBlocksCompleted()+"/"+request.getNumBlocksTotal());
		if(request.getSubRequests()!=null)
			for(Object r : request.getSubRequests())
				node.addChild("p", " Status : "+((Request)r).getSubject()+"  "+((Request)r).getRequestStatus()+", Stage: "+((Request)r).getSubStage()+"/"+((Request)r).getSubStageCount()+", Blocks:"+((Request)r).getNumBlocksCompleted()+"/"+((Request)r).getNumBlocksTotal());
		return node;
	}

	/**
	 * Put an error on the page, under node, also draws a big grey box around the error
	 */
	public static void addError(HTMLNode node, Throwable error){
		HTMLNode error1 = node.addChild("div", "style", "padding:10px;border:5px solid gray;margin:10px", error.toString());
		for (StackTraceElement ste : error.getStackTrace()){
			error1.addChild("br");
			error1.addChild("#", " -- "+ste.toString());
		}
		if(error.getCause()!=null)
			addError(error1, error.getCause());
	}

	public static HTMLNode searchList(){
		HTMLNode node = new HTMLNode("div");
		node.addChild("#",Search.getAllSearches().size()+" searches open ");
		node.addChild("a", "href", "plugins.XMLLibrarian.XMLLibrarian/purgeSearches", "[purge]");
		return node;
	}

	public static String listSearches(){
		HTMLNode searchlistpage = new HTMLNode("HTML");
		HTMLNode bodynode = searchlistpage.addChild("body");
		for(String s : Search.getAllSearches().keySet()){
			HTMLNode searchnode = bodynode.addChild("p");
			searchnode.addChild("#",s);
		}
		return searchlistpage.generate();
	}
}

