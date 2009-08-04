/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package plugins.Library.ui;

import plugins.Library.Main;
import plugins.Library.Library;
import plugins.Library.index.Request;
import plugins.Library.index.Request.RequestState;
import plugins.Library.search.Search;
import plugins.Library.index.TermPageEntry;

import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginHTTPException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;
import freenet.support.HTMLEncoder;
import freenet.l10n.L10n;
import freenet.support.Logger;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import plugins.Library.library.Index;
import plugins.Library.search.InvalidSearchException;
import plugins.Library.serial.TaskAbortException;

/**
 * Provides the HTML generation for the search page
 *
 * TODO tidy this huge mess
 * TODO password so embedded search has different access
 *
 * @author MikeB
 */
public class WebUI {
	static String plugName;
	static String httpPath = "/plugins/"+ Main.class.getName();
	static Library library;

	public static void setup(Library library){
		WebUI.library = library;
		WebUI.plugName = library.getPlugName();
	}


	/**
	 * Decide what to do depending on the request
	 * FIXME needs form password to secure from freesites
	 * 
	 * @param request
	 * @return String of generated HTML to be sent to browser
	 * @throws freenet.pluginmanager.PluginHTTPException
	 */
	public static String handleHTTPGet(HTTPRequest request) throws PluginHTTPException{
		String searchstring = request.getParam("search");
		String indexuri = "";
		if(request.isParameterSet("index")){
			// Get all indexes supplied
			String[] indexes = request.getMultipleParam("index");
			for (String string : indexes)
				indexuri += string + " ";
		}else
			 indexuri = Library.DEFAULT_INDEX_SITE;
		indexuri = indexuri.trim();

		// update of progress and results in xml for ajax update
		if(request.getPath().endsWith("xml"))
				return progressxml(searchstring,indexuri, "on".equals(request.getParam("showold")));

		// displays all indexes for debugging them
		if(request.getPath().endsWith("debug")){
			return WebUI.debugpage();
		}
		if(request.getPath().endsWith("listSearches"))
			return WebUI.listSearches();

		// If no search is specified show the default page
		if(searchstring == null || searchstring.equals("")){
			return WebUI.searchpage(indexuri, request.isParameterSet("js"));
		// Full main searchpage
		}else{
			Search searchobject = null;
			try{
				//get Search object
				searchobject = Search.startSearch(searchstring, indexuri);

				// generate HTML for search object and set it to refresh
				return searchpage(searchobject, indexuri, true, request.isParameterSet("js"), "on".equals(request.getParam("showold")), null);
			}catch(InvalidSearchException e){
				// Show page with exception
				return searchpage(searchobject, indexuri, false, false, false, e);
			}catch(RuntimeException e){
				// Show page with exception
				return searchpage(searchobject, indexuri, false, false, false, e);
			}
		}
	}

	public static String handleHTTPPost(HTTPRequest request) throws PluginHTTPException{
        return searchpage(null, false);
    }



	/**
	 * Build an empty search page with no refresh
	 * @param indexuri an index to put in the index box
	 * @param js whether js is known to be enabled
	 **/
	public static String searchpage(String indexuri, boolean js){
		return searchpage(null, indexuri, false, js, false, null);
	}


    /**
     * Build a search page for search in it's current state
	 * @param request the request this page should be built to show the progress of
	 * @param indexuri the index to show in the index box
	 * @param refresh a preference as to whether this page should refresh, refresh is switched off in the event of an error or the request being finished
	 * @param js whether js is known to be enabled
	 * @param showold whether old SSK versions should be shown
	 * @param e any exception which should be reported on the page
     **/
    public static String searchpage(Search request, String indexuri, boolean refresh, boolean js, boolean showold, Exception e){
		// Don't refresh if there is no request option, if it is finished or there is an error to show
		if(request==null || "".equals(request.getQuery()) || request.isDone() || e!=null)
			refresh = false;


        // Show any errors
		HTMLNode errorDiv = new HTMLNode("div", "id", "errors");
        if (e != null){
            addError(errorDiv, e);
		}

		// encode parameters
		String search = "";
		try{
			search = request !=null ? request.getQuery() : "";
			if(indexuri == null || indexuri.equals(""))
				indexuri = request !=null  ? HTMLEncoder.encode(request.getIndexURI()) : Library.DEFAULT_INDEX_SITE;
		}catch(RuntimeException exe){
			addError(errorDiv, exe);
		}

		// Start of page
		HTMLNode pageNode = new HTMLNode.HTMLDoctype("html", "-//W3C//DTD XHTML 1.1//EN");
		HTMLNode htmlNode = pageNode.addChild("html", "xml:lang", L10n.getSelectedLanguage().isoCode);
			htmlNode.addChild(searchHead(plugName, search, refresh && !js));

		HTMLNode bodyNode = htmlNode.addChild("body");

        // Start of body
		bodyNode.addChild(searchBox(search, indexuri, js, showold));
		bodyNode.addChild("br");
		// show errors
		bodyNode.addChild(errorDiv);

        // If showing a search
        if(request != null){
			// show progress
			bodyNode.addChild(progressBox(search, indexuri, request));
			bodyNode.addChild("p");

            // If search is complete show results
            if (request.getState()==RequestState.FINISHED)
				try{
					bodyNode.addChild(resultNodeGrouped(request, showold, js));
				}catch(TaskAbortException ex){
					addError(errorDiv, ex);
				}catch(RuntimeException ex){
					addError(errorDiv, ex);
				}
			else
				bodyNode.addChild("div", "id", "results").addChild("#");
        }

		// Add scripts, TODO put in separate file
		bodyNode.addChild("script", "type", "text/javascript").addChild("%", script(refresh,js, search, indexuri, showold));

		return pageNode.generate();
    }

	/**
	 * Return a HTMLNode for this result
	 * @param showold whether to display results from older SSK versions
	 * @param js whether js can be used to display results
	 */
	public static HTMLNode resultNodeGrouped(Request<Collection<TermPageEntry>> request, boolean showold, boolean js) throws TaskAbortException {
		// Output results
		int results = 0;
		// Loop to separate results into SSK groups
		HTMLNode resultsNode = new HTMLNode("div", "id", "results");
		HashMap<String, SortedMap<Long, Set<TermPageEntry>>> groupmap = new HashMap();
		Iterator<TermPageEntry> it = request.getResult().iterator();
		while(it.hasNext()){
			TermPageEntry o = it.next();
			// Get the key and name
			FreenetURI uri = o.getURI();
			Long uskVersion=Long.MIN_VALUE;
			// convert usk's
			if(uri.isSSKForUSK()){
				uri = uri.uskForSSK();
				// Get the USK edition
				uskVersion = uri.getEdition();
			}
			// Get the site base name, key + documentname - uskversion
			String sitebase = uri.setMetaString(null).setSuggestedEdition(0).toString().replaceFirst("/0", "");
			Logger.minor(WebUI.class, sitebase);
			// Add site
			if(!groupmap.containsKey(sitebase))
				groupmap.put(sitebase, new TreeMap<Long, Set<TermPageEntry>>());
			TreeMap<Long, Set<TermPageEntry>> sitemap = (TreeMap<Long, Set<TermPageEntry>>)groupmap.get(sitebase);
			// Add Edition
			if(!sitemap.containsKey(uskVersion))
				sitemap.put(uskVersion, new HashSet());
			// Add page
			sitemap.get(uskVersion).add(o);
		}
		// Loop over keys
		Iterator<String> it2 = groupmap.keySet().iterator();
		while (it2.hasNext()) {
			String keybase = it2.next();
			SortedMap<Long, Set<TermPageEntry>> siteMap = groupmap.get(keybase);
			HTMLNode siteNode = resultsNode.addChild("div", "style", "padding: 6px;");
			// Create a block for old versions of this SSK
			HTMLNode siteBlockOldOuter = siteNode.addChild("div", new String[]{"id", "style"}, new String[]{"result-hiddenblock-"+keybase, (!showold?"display:none":"")});
			// put title on block if it has more than one version in it
			if(siteMap.size()>1)
				siteBlockOldOuter.addChild("a", new String[]{"onClick", "name"}, new String[]{"toggleResult('"+keybase+"')", keybase}).addChild("h3", keybase.replaceAll("\\b.*/(.*)", "$1"));
			// inner block for old versions to be hidden
			HTMLNode oldEditionContainer = siteBlockOldOuter.addChild("div", new String[]{"class", "style"}, new String[]{"result-hideblock", "border-left: thick black;"});
			// Loop over all editions in this site
			Iterator<Long> it3 = siteMap.keySet().iterator();
			while(it3.hasNext()){
				Long version = it3.next();
				boolean newestVersion = !it3.hasNext();
				if(newestVersion)	// put older versions in block, newest outside block
					oldEditionContainer = siteNode;
				HTMLNode versionCell;
				HTMLNode versionNode;
				if(siteMap.get(version).size()>1||siteMap.size()>1){
					// table for this version
					versionNode = oldEditionContainer.addChild("table", new String[]{"class", "width", "border", "cellspacing", "cellpadding"}, new String[]{"librarian-result", "95%", "0px 8px", "0", "0",});
					HTMLNode grouptitle = versionNode.addChild("tr").addChild("td", new String[]{"padding", "colspan"}, new String[]{"0", "3"});
					grouptitle.addChild("h4", "style", "display:inline; padding-top: 5px; color:"+(newestVersion?"black":"darkGrey"), keybase.replaceAll("\\b.*/(.*)", "$1")+(version.longValue()>=0 ? "-"+version.toString():""));
					// Put link to show hidden older versions block if necessary
					if(newestVersion && !showold && js && siteMap.size()>1)
						grouptitle.addChild("a", new String[]{"href", "onClick"}, new String[]{"#"+keybase, "toggleResult('"+keybase+"')"}, "       ["+(siteMap.size()-1)+" older matching versions]");
					HTMLNode versionrow = versionNode.addChild("tr");
					versionrow.addChild("td", "width", "8px");
					// draw black line down the side of the version
					versionrow.addChild("td", new String[]{"bgcolor", "width"}, new String[]{"black", "2px"});

					versionCell=versionrow.addChild("td", "style", "padding-left:15px");
				}else
					versionCell = oldEditionContainer;
				// loop over each result in this version
				Iterator<TermPageEntry> it4 = siteMap.get(version).iterator();
				while(it4.hasNext()){
					TermPageEntry u = it4.next();
					FreenetURI uri = u.getURI();
					String showtitle = u.getTitle();
					String showurl = uri.toShortString();
					if (showtitle.trim().length() == 0 || showtitle.equals("not available")) {
						showtitle = showurl;
					}
					String realurl = "/" + uri.toString();
					HTMLNode pageNode = versionCell.addChild("div", new String[]{"class", "style"}, new String[]{"result-title", ""});
					pageNode.addChild("a", new String[]{"href", "class", "style", "title"}, new String[]{realurl, "result-title", "color: " + (newestVersion ? "Blue" : "LightBlue"), uri.toString()}, showtitle);
					// create usk url
					if (uri.isSSKForUSK()) {
						String realuskurl = "/" + uri.uskForSSK().toString();
						pageNode.addChild("a", new String[]{"href", "class"}, new String[]{realuskurl, "result-uskbutton"}, "[ USK ]");
					}
					pageNode.addChild("br");
					pageNode.addChild("a", new String[]{"href", "class", "style"}, new String[]{realurl, "result-url", "color: " + (newestVersion ? "Green" : "LightGreen")}, showurl);
					results++;
				}
			}
		}
		resultsNode.addChild("p").addChild("span", "class", "librarian-summary-found", "Found"+results+"results");
		return resultsNode;
    }

	/**
	 * Create the head node
	 * @param plugName
	 * @param search search query to put in the title
	 * @param refresh whether to set the page to refresh
	 * @return head node
	 */
	private static HTMLNode searchHead(String plugName, String search, boolean refresh){
		String title = plugName;
		if(search != null && !search.equals("") )
			title = "\"" + search + "\" - "+plugName;

		HTMLNode headNode = new HTMLNode("head");
		if(refresh)
            headNode.addChild("meta", new String[] { "http-equiv", "content" }, new String[] { "refresh", "1" });
		headNode.addChild("meta", new String[] { "http-equiv", "content" }, new String[] { "Content-Type", "text/html; charset=utf-8" });
		headNode.addChild("title", title);
		// Stylesheet
		headNode.addChild("link", new String[]{"title", "rel", "type", "href"}, new String[]{"clean-dropdown", "stylesheet", "text/css", "/static/themes/clean-dropdown/theme.css"});
		headNode.addChild("style").addChild("%",
				"body {font-family:sans-serif;\nbackground:white;}\n" +
				".result-sitename {color:black; font-weight:bold}\n" +
				".result-table { border-spacing : 5px; }\n" +
				".result-url {color:green; font-size:small; padding-left:15px}\n" +
				".result-uskbutton {color: #480000; font-variant: small-caps; font-size: small; padding-left: 20px}\n" +
				".progress-table {border-spacing:10px 0px;}\n" +
				".progress-bar-outline { width:300px; border:1px solid grey; height : 20px;}\n" +
				".progress-bar-inner-final { background-color: red; height:15px; z-index:-1}\n" +
				".progress-bar-inner-nonfinal { background-color: pink; height:15px; z-index:-1}\n" +
				"div#navbar { background-color: white; border : none; } \n" +
				"div#navbar ul { text-align : left; }\n" +
				"th, td { border: none; padding: 0; }\n" +
				"div#navbar ul li:hover ul, div#navbar ul li ul:hover { top: 1.1em; border: 1px solid #666633; background-color: #CCFFBB; }\n" +
				"h1, h2 { font-size: xx-large; font-weight: bold; }\n" +
				"input.index { font-size: 0.63em; size: 25; }\n" +
				"li.index {  }\n" +
				""
				);
		return headNode;
	}

	/**
	 * Create search form
	 * @param search already started
	 * @param indexuri
	 * @param js whether js has been detected
	 * @param showold
	 * @return
	 */
	private static HTMLNode searchBox(String search, String indexuri, boolean js, boolean showold){
		// Put all bookmarked indexes being used into a list and leave others in a string
		String[] indexes = indexuri.split("[ ;]");
		Set<String> allbookmarks = library.bookmarkKeys();
		ArrayList<String> usedbookmarks = new ArrayList();
		indexuri = "";
		for (String string : indexes) {
			if(string.startsWith(Library.BOOKMARK_PREFIX)
					&& allbookmarks.contains(string.substring(Library.BOOKMARK_PREFIX.length())))
				usedbookmarks.add(string);
			else
				indexuri += string + " ";
		}
		indexuri.trim();


		HTMLNode searchDiv = new HTMLNode("div", "id", "searchbar");
		HTMLNode searchForm = searchDiv.addChild("form", new String[]{"name", "method", "action"}, new String[]{"searchform", "GET", httpPath});
			HTMLNode searchTable = searchForm.addChild("table", "width", "100%");
				HTMLNode searchTop = searchTable.addChild("tr");
					HTMLNode titleCell = searchTop.addChild("td", new String[]{"rowspan","width"},new String[]{"3","120"});
						titleCell.addChild("H1", plugName);
					HTMLNode searchcell = searchTop.addChild("td", "width", "400");
						searchcell.addChild("input", new String[]{"name", "size", "type", "value"}, new String[]{"search", "40", "text", search});
						searchcell.addChild("input", new String[]{"name", "type", "value", "tabindex"}, new String[]{"find", "submit", "Find!", "1"});
						if(js)
							searchcell.addChild("input", new String[]{"type","name"}, new String[]{"hidden","js"});

				HTMLNode navList = searchTable.addChild("tr")
					.addChild("td")
					.addChild("div", "id", "navbar")
						.addChild("ul", "id", "navlist");
							HTMLNode newIndexInput = navList.addChild("li", new String[]{"class", "style"}, new String[]{"index", "display: inline-table;"});
								newIndexInput.addChild("input", new String[]{"type", "class"}, new String[]{"text", "index"});
								newIndexInput.addChild("br");
								newIndexInput.addChild("input", new String[]{"name", "type", "value", "class"}, new String[]{"index", "text", indexuri, "index"});
							HTMLNode subnavoptions = navList.addChild("li", "style", "display: inline-block; top: 10px; position: relative;", "Options")
								.addChild("ul", "class", "subnavlist");
									subnavoptions.addChild("li")
										.addChild("input", new String[]{"type"}, new String[]{"hidden"}, "Group SSK Editions");
									subnavoptions.addChild("li")
										.addChild("input", new String[]{"name", "type", showold?"checked":"size"}, new String[]{"showold", "checkbox", "1"}, "Show older editions");
									subnavoptions.addChild("li")
										.addChild("input", new String[]{"type"}, new String[]{"hidden"}, "Sort by relevence");
							HTMLNode entryindexes = navList.addChild("li", "style", "display: inline-block; top: 10px; position: relative;");
								entryindexes.addChild("input", "type", "submit");
								// SHows the list of bookmarked indexes TODO show descriptions on mouseover ??
								HTMLNode subnavindexes = entryindexes.addChild("ul", "class", "subnavlist", "Select indexes");
								for (String bm : library.bookmarkKeys()){
									searchDiv.addChild("%", "<!-- Checking for bm="+bm+" in \""+indexuri+"\" -->");
									subnavindexes.addChild("li")
										.addChild("input", new String[]{"type", "name", "value", (usedbookmarks.contains(Library.BOOKMARK_PREFIX+bm) ? "checked" : "size" )}, new String[]{"checkbox", "index", Library.BOOKMARK_PREFIX+bm, "1" } , bm);
								}

									
									
						/*
					.addChild("td", L10nString.getString("Index"))
						.addChild("input", new String[]{"name", "type", "value", "size"}, new String[]{"index", "text", indexuri, "40"});
				searchTable.addChild("tr")
					.addChild("td", L10nString.getString("ShowOldVersions"))
						.addChild("input", new String[]{"name", "type", showold?"checked":"size"}, new String[]{"showold", "checkbox", showold?"checked":"1"});
						 * */
		return searchDiv;
	}

	private static String debugpage() {
		HTMLNode debugpage = new HTMLNode("HTML");
		HTMLNode bodynode = debugpage.addChild("body");
		for(Index i : library.getAllIndices()){
			HTMLNode indexnode = bodynode.addChild("p");
			indexnode.addChild("#",i.toString());
		}
		return debugpage.generate();
	}

	/**
	 * Draw progress box with bars
	 * @param search
	 * @param indexuri
	 * @param request request to get progress from
	 * @return
	 */
	private static HTMLNode progressBox(String search, String indexuri, Request request){
			HTMLNode progressDiv = new HTMLNode("div", "id", "progress");
            // Search description
			HTMLNode progressTable = progressDiv.addChild("table", "width", "100%");
				HTMLNode searchingforCell = progressTable.addChild("tr")
					.addChild("td");
						searchingforCell.addChild("#", L10nString.getString("Searching-for"));
						searchingforCell.addChild("span", "class", "librarian-searching-for-target")
							.addChild("b", search);
						searchingforCell.addChild("#", L10nString.getString("in-index"));
						searchingforCell.addChild("i", indexuri);


				// Search status
				HTMLNode statusRow = progressTable.addChild("tr");
					statusRow.addChild("td")
							.addChild("div", "id", "librarian-search-status")
							.addChild("table", new String[]{"id", "class"}, new String[]{"progress-table", "progress-table"})
							.addChild(progressBar(request));
		return progressDiv;
	}

	/**
	 * Draw progress bars and describe progress
	 * @param request
	 * @return
	 */
	private static HTMLNode progressBar(Request request) {
		HTMLNode bar;
		// If it doesn't have subrequests, draw it's progress
		if(request.getSubRequests()==null){
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
		}else if(request.getSubject().matches(".+%.+[ ;].+")){
			bar = new HTMLNode("tbody");
			Iterator it=request.getSubRequests().iterator();
			while( it.hasNext()){
				Request r = (Request)it.next();
				HTMLNode indexrow = bar.addChild("tr");
				indexrow.addChild("td", r.getSubject().split("%")[1]);
				indexrow.addChild("td").addChild("table", "class", "progress-table").addChild(progressBar((Request)r));
			}
		// get progress for subrequests
		}else{
			bar = new HTMLNode("#");
			Iterator it=request.getSubRequests().iterator();
			while( it.hasNext()){
				Request r = (Request)it.next();
				bar.addChild(progressBar((Request)r));
			}
		}
		return bar;
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

	/**
	 * returns scripts
	 * @param refresh whether page is set to refresh
	 * @param js whther js is detected
	 * @param searchquery
	 * @param indexuri
	 * @param showold whether to show old versions of SSK's
	 * @return
	 */
	private static String script(boolean refresh, boolean js, String searchquery, String indexuri, boolean showold){
		return  (refresh&&!js) ?
					"var loc = new String(window.location);\n" +
					"if(loc.match('\\\\?'))" +
					"	window.location=loc+'&js';\n" +
					"else\n" +
					"	window.location=loc+'?js';\n"
				:
					"\n" +
					"var url = '"+httpPath+"/xml?search=" +searchquery+"&index="+indexuri+"&showold="+(showold?"on":"off")+"';\n" +
					"var xmlhttp;\n" +
					"\n" +
					"function getProgress(){\n" +
					"	xmlhttp = new XMLHttpRequest();\n" +
					"	xmlhttp.onreadystatechange=xmlhttpstatechanged;\n" +
					"	xmlhttp.open('GET', url, true);\n" +
					"	xmlhttp.send(null);\n" +
					"}\n" +
					"\n" +
					"function xmlhttpstatechanged(){\n" +
					"	if(xmlhttp.readyState==4){\n" +
					"		var parser = new DOMParser();\n" +
					"		var resp = parser.parseFromString(xmlhttp.responseText, 'application/xml').documentElement;\n" +
					"		document.getElementById('librarian-search-status').innerHTML=" +
								"resp.getElementsByTagName('progress')[0].textContent;\n" +
					"		if(resp.getElementsByTagName('progress')[0].attributes.getNamedItem('RequestState').value=='FINISHED')\n" +
					"			document.getElementById('results').innerHTML=" +
									"resp.getElementsByTagName('result')[0].textContent;\n" +
					"		else if(resp.getElementsByTagName('progress')[0].attributes.getNamedItem('RequestState').value=='ERROR')\n" +
					"			document.getElementById('errors').innerHTML+=" +
									"resp.getElementsByTagName('error')[0].textContent;\n" +
					"		else\n" +
					"			var t = setTimeout('getProgress()', 1000);\n" +
					"	}\n" +
					"}\n" +
					"getProgress();\n" +
					"\n" +
					"function toggleResult(key){\n" +
					"	var togglebox = document.getElementById('result-hiddenblock-'+key);\n" +
					"	if(togglebox.style.display == 'block')\n" +
					"		togglebox.style.display = 'none';\n" +
					"	else\n" +
					"		togglebox.style.display = 'block';\n" +
					"}\n";
	}

	/**
	 * Return progress and results on a request in xml format for ajax
	 * @param searchquery
	 * @param indexuri
	 * @param showold
	 * @return
	 */
	static String progressxml(String searchquery, String indexuri, boolean showold) {
		HTMLNode resp = new HTMLNode("pagecontent");
		String progress;
		Search search = Search.getSearch(searchquery, indexuri);
		// If search is happening, return it's progress
		if(search!=null){
			HTMLNode progresstable = new HTMLNode("table", new String[]{"id", "class"}, new String[]{"progress-table", "progress-table"});
				progresstable.addChild(progressBar(search));
			progress = progresstable.generate();
		}else
			progress = "No search for this, something went wrong";		// FIXME this came up again ??
		// If it's finished, return it's results
		if(search != null && search.getState()==RequestState.FINISHED)
			try {
				resp.addChild("result", WebUI.resultNodeGrouped(Search.getSearch(searchquery, indexuri), showold, true).generate());
				resp.addChild("progress", "RequestState", "FINISHED", "Search complete");
			} catch (TaskAbortException ex) {
				addError(resp.addChild("error", "RequestState",  "ERROR"), ex.getCause());
			} catch (RuntimeException ex) {
				addError(resp.addChild("error", "RequestState",  "ERROR"), ex);
			}
		else
			resp.addChild("progress", "RequestState",  (search==null)?"":search.getState().toString(), progress);
		return "<?xml version='1.0' encoding='ISO-8859-1'?>\n"+resp.generate();
	}

	public static String listSearches(){
		HTMLNode searchlistpage = new HTMLNode("HTML");
		HTMLNode bodynode = searchlistpage.addChild("body");
		for(Search s : Search.getAllSearches().values()){
			HTMLNode searchnode = bodynode.addChild("p");
			searchnode.addChild("#",s.toString());
		}
		return searchlistpage.generate();
	}
}

