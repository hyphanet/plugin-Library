package plugins.XMLLibrarian;

import freenet.support.HTMLEncoder;




public class WebUI{
	static String plugName;
	
	public static void setup(String plugName){
		WebUI.plugName = plugName;
	}

    private static void appendStatusDisplay(StringBuilder out, Search searchobject){
        out.append("<tr><td width=\"140\">Search status : </td><td><div id=\"librarian-search-status\">"+searchobject.getprogress("plain")+"</div></td></tr></table>\n");
        out.append("<p></p>\n\n");
    }

	/**
	 * Build an empty search page
	 **/
	public static String searchpage(){
		return searchpage(null, false, null);
	}
	
    /**
     * Build a search page for search in it's current state
     **/
    public static String searchpage(Search searchobject, boolean refresh, Exception e){
        StringBuilder out = new StringBuilder();

        String search = searchobject !=null ? HTMLEncoder.encode(searchobject.getQuery()) : "";
        String indexuri = searchobject !=null ? HTMLEncoder.encode(searchobject.getIndex().getIndexURI()) : "";

		if(searchobject.isSuccess())
			refresh = false;

        out.append("<HTML><HEAD><TITLE>"+plugName+"</TITLE>\n");
        out.append("<!-- indexuri=\""+ indexuri +"\" search=\""+search+"\" searchobject="+searchobject);
        if(searchobject != null)
            out.append("done="+searchobject.isdone());
        out.append(" -->");

        if(searchobject != null && e==null && refresh)
            out.append("<meta http-equiv=\"refresh\" content=\"1\" />\n");
        out.append("</HEAD><BODY>\n");

        // Start of body
		out.append("<form method=\"GET\"><table width=\"100%\">\n");
		out.append("    <tr><td rowspan=2 width=\"300\"><H1>"+plugName+"</H1></td>\n");
		out.append("        <td width=400><input type=\"text\" value=\"").append(search).append("\" name=\"search\" size=40/>\n");
		out.append("            <input type=submit name=\"find\" value=\"Find!\" TABINDEX=1/></td></tr>\n");
		out.append("    <tr><td>Index <input type=\"text\" name=\"index\" value=\"").append(indexuri).append("\" size=40/>\n");
		out.append("</tr></table></form>\n");

        // Show any errors
        if (e != null)
            out.append(HTMLEncoder.encode(e.toString()));
		if (searchobject.getError() != null)
			out.append(HTMLEncoder.encode(searchobject.getError().toString()));

        // If showing a search
        if(searchobject != null){
            // Search description
            out.append("<table width=\"100%\"><tr><td colspan=\"2\"><span class=\"librarian-searching-for-header\">Searching for </span>\n");
            out.append("<span class=\"librarian-searching-for-target\"><b>"+HTMLEncoder.encode(search)+"</b></span> in index <i>"+HTMLEncoder.encode(indexuri)+"</i></td></tr></table>\n");

            // Search status
            appendStatusDisplay(out, searchobject);

            // If search is complete show results
            if (searchobject.isdone())
				try{
					out.append(searchobject.getresult());
				}catch(Exception ex){
					out.append(HTMLEncoder.encode(ex.toString()));
				}
        }

		out.append("</CENTER></BODY></HTML>");
		return out.toString();
    }
}

