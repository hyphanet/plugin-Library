package plugins.XMLLibrarian;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import freenet.client.FetchException;
import freenet.client.events.ClientEventListener;
import freenet.client.HighLevelSimpleClient;
import freenet.client.events.ClientEvent;
import freenet.client.async.ClientContext;
import com.db4o.ObjectContainer;
import freenet.node.RequestStarter;
import freenet.support.HTMLEncoder;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;
import freenet.support.HTMLNode;
import freenet.pluginmanager.PluginRespirator;


/**
 * Performs searches, both async & sync
 * @author MikeB
 */
public class Search extends Thread implements ClientEventListener{
	static private XMLLibrarian xl;
	String search;
	Index index;
	// For progress:
	// Each Search has it's own HLSC to fetch indexes
	private HighLevelSimpleClient hlsc;
	private boolean retrieved;
	private boolean complete;
	private String msg;
	private String result;
	private String eventDescription;
	private HashSet<Search> subsearches;
	private static HashMap<String, Search> allsearches = new HashMap<String, Search>();
	private Exception error;
	private List<URIWrapper> resultList = null;

	/**
	 * Creates Search instance, can take a list of indexes separated by spaces
	 * 
	 * @param search string query
	 * @param indexuri Index(s) to be used for this search, separated by spaces
	 * @throws InvalidSearchException if the search is invalid
	 **/
	private Search(String search, String indexuri) throws InvalidSearchException{
		// check search term is valid
		if (search == null || search.equals("")) {
			throw new InvalidSearchException("Search string cannot be empty");
		}
		
		ArrayList<Index> indices = Index.getIndices(indexuri, xl.getPluginRespirator());
		if(indices.size() == 1){
			this.index = indices.get(0);
			subsearches = splitQuery(search, index);
		}else{
			this.index = null;
			subsearches = new HashSet<Search>();
			for(Index index : indices)
				subsearches.add(new Search(search, index));
		}
		
		
		Logger.normal(xl, "Search " + search + " in " + indexuri);
		
		this.search = search;
		
		
		retrieved = false;
		complete = false;
		msg = "Searching for "+HTMLEncoder.encode(search) + " in " + indices.size() + " indexes";
		
		// Probably dont need this
		hlsc = xl.getPluginRespirator().getNode().clientCore.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS);
		hlsc.addEventHook(this);
	
		allsearches.put(search + indexuri, this);
	}

	/**
	 * Creates Search instance
	 * 
	 * @param search string query
	 * @param index Index to be used for this search
	 * @throws InvalidSearchException if the search is invalid
	 **/
	private Search(String search, Index index) throws InvalidSearchException{
		// check search term is valid
		if (search.equals("")) {
			throw new InvalidSearchException("Search string cannot be empty");
		}
		
		this.search = search;
		this.index = index;
		subsearches = splitQuery(search, index);
		
		retrieved = false;
		complete = false;
		msg = "Searching for "+HTMLEncoder.encode(search);
		
		// Probably dont need this
		hlsc = xl.getPluginRespirator().getNode().clientCore.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS);
		hlsc.addEventHook(this);
	}
	
	/**
	 * Splits query into multiple searches, will be used for advanced queries
	 * 
	 * @return Set of subsearches or null if theres only one search
	 */
	private HashSet<Search> splitQuery(String query, Index index) throws InvalidSearchException{
		String[] searchWords = query.split("[^\\p{L}\\{N}]+");
		if (searchWords.length < 2)
			return null; // Just one search term
		
		HashSet<Search> searches = new HashSet<Search>(searchWords.length);
		for ( String searchtoken : searchWords)
			searches.add(new Search(searchtoken, index));
		return searches;
	}


	/**
	 * Sets the parent plugin to be used for logging & plugin api
	 * @param xl Parent plugin
	 */
	public static void setup(XMLLibrarian xl){
		Search.xl = xl;
		Search.allsearches = new HashMap<String, Search>();
	}
	
	
	/**
	 * Creates a search instance for a single index, starts and returns it
	 *
	 * @param search string to be searched
	 * @param indexuri URI of index(s) to be used
	 * @throws InvalidSearchException if any part of the search is invalid
	 */
	public static Search startSearch(String search, String indexuri) throws InvalidSearchException{
		search = search.toLowerCase();
        
		// See if the same search exists
		if (allsearches.containsKey(search + indexuri))
			return allsearches.get(search + indexuri);


		// create search
		Search searcher = new Search(search, indexuri);
		// start search
		searcher.start();
		return searcher;
	}

    /**
     * Start search
     */
    public void run(){
		try {
			// TODO if there are subsearches start them & wait for updates, else ...
			
			
			// Perform for one search on one index
			resultList = index.search(this);
            done("Complete.");
		} catch (Exception e) {
            error(e);
			Logger.error(xl, "Could not complete search for " + search + " in " + index + e.toString(), e);
			e.printStackTrace();
		}
    }
    
    public String getQuery(){
        return search;
    }
	
	public Index getIndex(){
		return index;
	}
    
    // All these moved from Progress
    
    public synchronized void setprogress(String _msg){
        msg = _msg;
        retrieved = false;
		this.notifyAll();
    }
    
    public HighLevelSimpleClient getHLSC(){
        return hlsc;
    }
    
    public synchronized void done(String msg){
        done(msg, null);
    }
    public synchronized void done(String msg, String result){
		retrieved = false;
		complete = true;
		this.msg = msg;
		this.result = (result==null) ? "" : result;
		this.notifyAll();
    }
	
	public synchronized void error(Exception e){
		retrieved = false;
		complete = true;
		this.msg = "Error whilst performing asyncronous search : " + e.toString();
		error = e;
		this.notifyAll();
	}
	
	public Exception getError(){
		return error;
	}
    
    public boolean isdone(){
        return complete;
    }
	
	public boolean isSuccess(){
		return resultList != null;
	}

    // TODO better status format
    public String getprogress(String format){
        // probably best to do this with a lock
        // look for a progress update
        while(retrieved && !complete)   // whilst theres no new msg
            try{
                Thread.sleep(500);
            }catch(java.lang.InterruptedException e){

            }
        retrieved = true;
        
        String ed = eventDescription;
        if(ed == null) ed = "";
            if(format.equals("html")){
                if(complete){
                    return "<html><body>"+msg+" - <a href=\"/plugins/plugins.XMLLibrarian.XMLLibrarian?search="+search+"&index="+index+"\" target=\"_parent\">Click to reload &amp; view results</a><br />"+ed+"</body></html>";
                }else
                    return "<html><head><meta http-equiv=\"refresh\" content=\"1\" /></head><body>"+msg+"<br />"+ed+"</body></html>";
            }else if(format.equals("coded")){
                if(complete)
                    return msg+"<br />"+ed;
                else
                    return "."+msg+"<br />"+ed;
            }else
                    return msg+"<br />"+ed;
    }
	
	
    public synchronized void getprogress(HTMLNode node){
        // look for a progress update
		try{
			if(retrieved && !complete)
				this.wait();
		}catch(java.lang.InterruptedException ex){
			node.addChild("#", ex.toString());
			node.addChild("br");
		}
        retrieved = true;
        
        node.addChild("#", msg);
		node.addChild("br");
		node.addChild("#", eventDescription);
    }
	

    public String getresult(){
		// Output results
		int results = 0;
		StringBuilder out = new StringBuilder();
		out.append("<table class=\"librarian-results\"><tr>\n");
		Iterator<URIWrapper> it = resultList.iterator();
		try {
			while (it.hasNext()) {
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
				out
						.append("<p>\n<table class=\"librarian-result\" width=\"100%\" border=1><tr><td align=center bgcolor=\"#D0D0D0\" class=\"librarian-result-url\">\n");
				out.append("  <A HREF=\"").append(realurl).append("\" title=\"").append(o.URI).append("\">")
						.append(showtitle).append("</A>\n");
				out.append("</td></tr><tr><td align=left class=\"librarian-result-summary\">\n");
				out.append("</td></tr></table>\n");
				results++;
			}
		} catch (Exception e) {
			done("Could not display results for " + search + e.toString());
			Logger.error(xl, "Could not display search results for " + search + e.toString(), e);
		}
		out.append("</tr><table>\n");
		out.append("<p><span class=\"librarian-summary-found-text\">Found: </span><span class=\"librarian-summary-found-number\">")
				.append(results).append(" results</span></p>\n");
		done("Complete.", out.toString());
		
        return result;
    }
	

	public void getResult(HTMLNode node){
		// Output results
		int results = 0;
		
		HTMLNode resultTable = node.addChild("table", new String[]{"width", "class"}, new String[]{"95%", "librarian-results"});
		Iterator<URIWrapper> it = resultList.iterator();
		try {
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
		} catch (Exception e) {
			done("Could not display results for " + search + e.toString());
			Logger.error(xl, "Could not display search results for " + search + e.toString(), e);
		}
		node.addChild("p").addChild("span", "class", "librarian-summary-found", xl.getString("Found")+results+xl.getString("results"));
		done("Complete.", node.generate());
    }


    /**
     * Hears an event.
     * @param container The database context the event was generated in.
     * NOTE THAT IT MAY NOT HAVE BEEN GENERATED IN A DATABASE CONTEXT AT ALL:
     * In this case, container will be null, and you should use context to schedule a DBJob.
     **/
    public void receive(ClientEvent ce, ObjectContainer maybeContainer, ClientContext context){
        if(eventDescription != ce.getDescription()){
            eventDescription = ce.getDescription();
            retrieved = false;
        }
    }

    /**
     * Called when the EventProducer gets removeFrom(ObjectContainer).
     * If the listener is the main listener which probably called removeFrom(), it should do nothing.
     * If it's a tag-along but request specific listener, it may need to remove itself.
     */
	public void onRemoveEventProducer(ObjectContainer container){

    }
}
