package plugins.XMLLibrarian;

import java.security.MessageDigest;
import java.util.HashMap;

import freenet.client.FetchException;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLEncoder;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;



/**
 * XMLLibrarian is a modified version of the old librarian. It uses the Xml index files for
 * searching. In addition to searching in a single index, XMLLibrarian allows searching in multiple
 * indices at the same time. Folders containing set of indices can be created and any search on a
 * folder searches the string in all the indices in the folder.
 * 
 * The current configuration can be stored in an external file and reused later. The default file
 * for the same is XMLLibrarian.xml.
 * 
 *The index list of a particular folder can be saved in an external file and likewise imported from
 * an existing file. XMLLibrarian assumes that the index to be used is present at
 * DEFAULT_INDEX_SITE/index.xml .
 * 
 * @author swatigoyal
 * 
 */

public class XMLLibrarian implements FredPlugin, FredPluginHTTP, FredPluginVersioned, FredPluginRealVersioned, FredPluginThreadless {
	/**
	 * Default index site
	 */
	public static final String DEFAULT_INDEX_SITE = "USK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search/19/";
	/*
	 * Current configuration gets saved by default in the configfile. To Save the current
	 * configuration use "Save Configuration"
	 */
	private static int version = 21;
	private static final String plugName = "XMLLibrarian " + version;
    private HashMap<String, Progress> progressmap = new HashMap();
    private StringBuilder logs = new StringBuilder();

	public String getVersion() {
		return version + " r" + Version.getSvnRevision();
	}
	
	public long getRealVersion() {
		return version;
	}

	private PluginRespirator pr;

	public void terminate() {

	}

	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
        if(request.getPath().endsWith("logs"))
            return logs.toString();
                
		String search = request.getParam("search");
        if(request.getPath().endsWith("progress")){
            if (progressmap.containsKey(search))
                return progressmap.get(search).get(request.getParam("pushonupdate").equals("true"));
            else return "No asyncronous search for "+search+" found.";
        }else if(request.getPath().endsWith("result")){
            if (progressmap.containsKey(search))
                return progressmap.remove(search).getresult();
            else return "No asyncronous search for "+search+" found.";
        }else{
            String indexuri = request.isParameterSet("index") ? request.getParam("index") : DEFAULT_INDEX_SITE;
            logs.append("show progress = "+request.getParam("showprogress")+"<br />");
            boolean showprogress = (request.getParam("showprogress").equals("true"));

            return handleInner(request.getPath(), search, indexuri, showprogress);
        }
	}

	private void appendDefaultPageStart(StringBuilder out) {

		out.append("<HTML><HEAD><TITLE>"+plugName+"</TITLE></HEAD><BODY>");
	}

	private void appendDefaultPageEnd(StringBuilder out) {
		out.append("</CENTER></BODY></HTML>");
	}

	/**
	 * appendDefaultPostFields generates the main interface to the XMLLibrarian
	 * 
	 * @param out
	 * @param search
	 * @param index
	 */

	private void appendDefaultPostFields(StringBuilder out, String search, String index) {
		search = HTMLEncoder.encode(search);
		index = HTMLEncoder.encode(index);
		out.append("<form method=\"GET\"><table><tr>\n");
		out.append("<td rowspan=2 width=280><H1>"+plugName+"</H1></td>\n");
		out.append("<div style=\"visibility:hidden;\"><input type=submit name = \"find\" value=\"Find!\" TABINDEX=1/><input name=\"showprogress\" value=\"false\" /></div>\n");
		out.append("<td width=400><input type=\"text\" value=\"").append(search).append("\" name=\"search\" size=40/><input type=submit name = \"find\" value=\"Find!\" TABINDEX=1/><td rowspan=\"2\" id=\"librarian-info\">If you had JavaScript enabled, you would be able to see some status information for your search.</td></tr>\n");
		out.append("<tr><td>Index <input type=\"text\" name=\"index\" value=\"").append(index).append("\" size=50/>\n");
		out.append("</tr></table></form><script language=\"JavaScript\">document.forms[0].showprogress.value=\"true\";document.getElementById(\"librarian-info\").innerHTML=\"You have JavaScript enabled so you should be able to see live progress of your search, please note this does not currently function properly with simultanious searches.\"</script>\n\n");
	}

	/**
	 * Generates the interface to the XMLLibrarian and takes apropos action to an event.
	 * 
	 * @param request
	 */
	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		String search = request.getPartAsString("search", 80);
		String indexuri = request.isPartSet("index") ? request.getPartAsString("index", 200) : DEFAULT_INDEX_SITE;
		boolean showprogress = (request.getPartAsString("showprogress", 5).equals("true"));

		return handleInner(request.getPath(), search, indexuri, showprogress);
	}

	private String handleInner(String path, String search, String indexuri, boolean showprogress) {
		StringBuilder out = new StringBuilder();

		appendDefaultPageStart(out);
		appendDefaultPostFields(out, search, indexuri);

		try {
			if (indexuri.equals(""))
				out.append("Specify a valid index \n");
			else if(search.equals(""))
                out.append("Give a valid string to search ");
            else{
                Search.setup(pr, this);


                out.append("<table><tr><td colspan=\"2\"><span class=\"librarian-searching-for-header\">Searching for </span>\n");
                out.append("<span class=\"librarian-searching-for-target\"><b>"+HTMLEncoder.encode(search)+"</b></span> in index <i>"+HTMLEncoder.encode(indexuri)+"</i></td></tr>\n");
                
                if(showprogress){
                    out.append("<tr><td width=\"140\">&nbsp;&nbsp;Search status : </td><td><div id=\"librarian-search-status\"></div></td></tr></table>\n");
                    out.append("<p></p>\n\n");
                    out.append("<script language=\"JavaScript\">\n");
                    out.append("function getresult(){\n");
                    out.append("	var xmlHttp=new XMLHttpRequest();\n");
                    out.append("	xmlHttp.onreadystatechange = function(){\n");
                    out.append("		if(xmlHttp.readyState==4)\n");
                    out.append("                document.getElementById(\"librarian-search-results\").innerHTML=xmlHttp.responseText;\n");
                    out.append("    }\n");
                    out.append("	xmlHttp.open(\"GET\",\"/plugins/plugins.XMLLibrarian.XMLLibrarian/result?search="+search+"\",true);\n");
                    out.append("	xmlHttp.send(null);\n}\n");

                    out.append("function requestStatus(){\n");
                    out.append("	var xmlHttp=new XMLHttpRequest();\n");
                    out.append("	xmlHttp.onreadystatechange = function(){\n");
                    out.append("		if(xmlHttp.readyState==4){\n");
                    out.append("            if(xmlHttp.responseText.charAt(0)=='.'){\n");
                    out.append("                document.getElementById(\"librarian-search-status\").innerHTML=xmlHttp.responseText.substr(1);\n");
                    out.append("                requestStatus();\n");
                    out.append("            }else{\n");
                    out.append("                document.getElementById(\"librarian-search-status\").innerHTML=xmlHttp.responseText;\n");
                    out.append("                getresult();\n      }\n");
                    out.append("        }\n    }\n");
                    out.append("	xmlHttp.open(\"GET\",\"/plugins/plugins.XMLLibrarian.XMLLibrarian/progress?search="+search+"&pushonupdate=true\",true);\n");
                    out.append("	xmlHttp.send(null);\n}\n");
                    out.append("requestStatus();\n");
                    out.append("</script>\n");

                    // Set up progressing
                    Progress progress = new Progress("Searching for "+search);
                    pr.getHLSimpleClient().addGlobalHook(progress);
                    progressmap.put(search, progress);
                    Search.searchStrAsync(out, search, indexuri, progress);
                }else{
                    out.append("</table><p></p>\n\n");
                    Search.searchStr(out, search, indexuri);
                }
            }
		} catch (Exception e) {
			Logger.error(this,
			        "Searching for the word " + search + " in index " + indexuri + " failed " + e.toString(), e);
		}

		appendDefaultPageEnd(out);
		return out.toString();
	}


	public void runPlugin(PluginRespirator pr) {
		this.pr = pr;
	}

	private static String convertToHex(byte[] data) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < data.length; i++) {
			int halfbyte = (data[i] >>> 4) & 0x0F;
			int two_halfs = 0;
			do {
				if ((0 <= halfbyte) && (halfbyte <= 9))
					buf.append((char) ('0' + halfbyte));
				else
					buf.append((char) ('a' + (halfbyte - 10)));
				halfbyte = data[i] & 0x0F;
			} while (two_halfs++ < 1);
		}
		return buf.toString();
	}

	//this function will return the String representation of the MD5 hash for the input string 
	public static String MD5(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] b = text.getBytes("UTF-8");
			md.update(b, 0, b.length);
			byte[] md5hash = md.digest();
			return convertToHex(md5hash);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
