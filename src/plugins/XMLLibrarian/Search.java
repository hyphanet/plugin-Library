package plugins.XMLLibrarian;

import java.util.List;
import java.util.Iterator;
import freenet.client.FetchException;
import freenet.support.HTMLEncoder;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;
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
        
        retrieved = false;
        complete = false;
        msg = "Searching for "+HTMLEncoder.encode(search);
        
        hlsc = xl.getPluginRespirator().getNode().clientCore.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS);
        hlsc.addEventHook(this);
        
        
        // TODO create subsearches for search tokens
    }

    /**
     * Sets the parent plugin to be used for logging & plugin api
     * @param xl Parent plugin
     */
    public static void setup(XMLLibrarian xl){
        Search.xl = xl;
    }
    
    
    /**
	 * Creates a search instance for a single index, starts and returns it
	 *
	 * @param search string to be searched
	 * @param indexuri URI of index to be used
     * @throws InvalidSearchException if any part of the search is invalid
	 */
	public static Search startSearch(String search, String indexuri) throws InvalidSearchException{
		search = search.toLowerCase();
        
        
        // get index
        Index index = new Index(indexuri);
        // create search
        Search searcher = new Search(search, index);
        // start search
        searcher.start();
        return searcher;
	}

    /**
     * Start search
     */
    public void run(){

		try {
			String[] searchWords = search.split("[^\\p{L}\\{N}]+");
			// Return results in order.
			List<URIWrapper> hs = null;
			/*
			 * search for each string in the search list only the common results to all words are
			 * returned as final result
			 */
			try {
                setprogress("Fetching index");
				index.fetch(progress);
                setprogress("Searching in index");
                //logs.append("Searching in index<br />\n");
				hs = index.search(searchWords, progress);
                setprogress("Formatting results");
			} catch (FetchException e) {
				progress.done("Could not fetch sub-index for " + HTMLEncoder.encode(search)
				        + " : " + HTMLEncoder.encode(e.getMessage()) + "\n");
				Logger.normal(xl, "Could not fetch sub-index for " + search + " in "
				        + indexuri + " : " + e.toString() + "\n", e);
                return;
			} catch (Exception e) {
				progress.done("Could not complete search for " + HTMLEncoder.encode(search) + " : " + HTMLEncoder.encode(e.toString())
				        + "\n" + HTMLEncoder.encode(String.valueOf(e.getStackTrace())));
				Logger.error(xl, "Could not complete search for " + search + "in " + indexuri + e.toString(), e);
                return;
			}
			// Output results
			int results = 0;
            StringBuilder out = new StringBuilder();
			out.append("<table class=\"librarian-results\"><tr>\n");
			Iterator<URIWrapper> it = hs.iterator();
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
				progress.done("Could not display results for " + search + e.toString());
				Logger.error(xl, "Could not display search results for " + search + e.toString(), e);
			}
			out.append("</tr><table>\n");
			out.append("<p><span class=\"librarian-summary-found-text\">Found: </span><span class=\"librarian-summary-found-number\">")
			        .append(results).append(" results</span></p>\n");
            progress.done("Complete.", out.toString());
		} catch (Exception e) {
            progress.done("Could not complete search for " + search + " in " + indexuri + e.toString());
			Logger.error(xl, "Could not complete search for " + search + " in " + indexuri + e.toString(), e);
			e.printStackTrace();
		}
    }
    
    public String getQuery(){
        return search;
    }
    
    // All these moved from Progress
    
    public void setprogress(String _msg){
        msg = _msg;
        retrieved = false;
    }
    
    public HighLevelSimpleClient getHLSC(){
        return hlsc;
    }
    
    public void done(String msg){
        done(msg, null);
    }
    public void done(String msg, String result){
        retrieved = false;
        complete = true;
        this.msg = msg;
        this.result = (result==null) ? "" : result;
    }
    
    public boolean isdone(){
        return complete;
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

    public String getresult(){
        while(!complete)   // whilst theres no results
            try{
                Thread.sleep(500);
            }catch(java.lang.InterruptedException e){

            }
        return result;
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
