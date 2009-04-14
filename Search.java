package plugins.XMLLibrarian;

import java.util.List;
import java.util.Iterator;
import freenet.client.FetchException;
import freenet.support.HTMLEncoder;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;import freenet.pluginmanager.PluginRespirator;


/**
 * Performs searches, both async & sync
 * @author MikeB
 */
class Search extends Thread{
    static private PluginRespirator pr;
    static private XMLLibrarian xl;


    public static void setup(PluginRespirator pr, XMLLibrarian xl){
        Search.pr = pr;
        Search.xl = xl;
    }

	/**
	 * Searches for the string in the specified index. In case of a folder searches in all included
	 * indices
	 *
	 * @param out
	 * @param search
	 *            - string to be searched
	 * @param indexuri
	 * @param stylesheet
	 */
	 public static  void searchStr(StringBuilder out, String search, String indexuri) throws Exception {
		search = search.toLowerCase();
		if (search.equals("")) {
			out.append("Give a valid string to search\n");
			return;
		}
		try {
			// Get search result

			String[] searchWords = search.split("[^\\p{L}\\{N}]+");
			// Return results in order.
			List<URIWrapper> hs = null;
			/*
			 * search for each string in the search list only the common results to all words are
			 * returned as final result
			 */
			try {
				Index idx = new Index(indexuri, pr);
				idx.fetch();
				hs = idx.search(searchWords);
			} catch (FetchException e) {
				out.append("<p>Could not fetch sub-index for " + HTMLEncoder.encode(search)
				        + " : " + e.getMessage() + "</p>\n");
				Logger.normal(xl, "<p>Could not fetch sub-index for " + HTMLEncoder.encode(search) + " in "
				        + HTMLEncoder.encode(indexuri) + " : " + e.toString() + "</p>\n", e);
			} catch (Exception e) {
				out.append("<p>Could not complete search for " + HTMLEncoder.encode(search) + " : " + e.toString()
				        + "</p>\n");
				out.append(String.valueOf(e.getStackTrace()));
				Logger.error(xl, "Could not complete search for " + search + "in " + indexuri + e.toString(), e);
			}
			// Output results
			int results = 0;
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
				out.append("Could not display results for " + search + e.toString());
				Logger.error(xl, "Could not display search results for " + search + e.toString(), e);
			}
			out.append("</tr><table>\n");
			out
			        .append(
			                "<p><span class=\"librarian-summary-found-text\">Found: </span><span class=\"librarian-summary-found-number\">")
			        .append(results).append(" results</span></p>\n");
		} catch (Exception e) {
			Logger.error(xl, "Could not complete search for " + search + " in " + indexuri + e.toString(), e);
			e.printStackTrace();
		}
	}

    /**
	 * Searches for the string in the specified index. In case of a folder searches in all included
	 * indices
	 *
	 * @param out
	 * @param search
	 *            - string to be searched
	 * @param indexuri
	 */ // this function will be made to only return placeholder with XMLHTTPRequest callbacks,
    //      it will start a separate thread to search and all out.append's will act on progress
	public static void searchStrAsync(StringBuilder out, String search, String indexuri, Progress progress){
        //logs.append("Searching with progress on : "+search+"<br />");


        // check search term is valid
		search = search.toLowerCase();
		if (search.equals("")) {
			out.append("Give a valid string to search\n");
            //logs.append("Give a valid string to search<br />");
			return;
		}
        
        // need to put XMLHttp callback for loading results
        out.append("<div id=\"librarian-search-results\">\n</div>\n");

        Search searcher = new Search(search, indexuri, progress);
        searcher.start();

	}


    // Threaded section for async search
    String search;
    String indexuri;
    Progress progress;
    private Search(String search, String indexuri, Progress progress){
        this.search = search;
        this.indexuri = indexuri;
        this.progress = progress;
    }

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
				Index idx = new Index(indexuri, pr);
                progress.set("Fetching index");
                //logs.append("Fetching index<br />\n");
				idx.fetch(progress);
                progress.set("Searching in index");
                //logs.append("Searching in index<br />\n");
				hs = idx.search(searchWords, progress);
                progress.set("Formatting results");
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
}