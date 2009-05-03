package plugins.XMLLibrarian;

import java.security.MessageDigest;
import java.util.HashMap;

import freenet.client.FetchException;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginHTTPAdvanced;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLEncoder;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;
import freenet.support.MultiValueTable;



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
	private static int version = 22;
	private static final String plugName = "XMLLibrarian " + version;
    private HashMap<String, Progress> progressmap = new HashMap();
    //private StringBuilder logs = new StringBuilder();
	private PluginRespirator pr;

	public String getVersion() {
		return version + " r" + Version.getSvnRevision();
	}
	
	public long getRealVersion() {
		return version;
	}

	public void terminate() {

	}
    
    public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException
    {
        //if(request.getPath().endsWith("logs"))
        //    return logs.toString();
                
		String search = request.getParam("search");if(request.getPath().endsWith("progress")){
            if (progressmap.containsKey(search))
                return progressmap.get(search).get(request.getParam("format"));
            else
                return "No asyncronous search for "+HTMLEncoder.encode(search)+" found.";
        }else if(request.getPath().endsWith("result")){
//            logs.append("Requested result for "+HTMLEncoder.encode(search)+"<br />\n");
            if (progressmap.containsKey(search)){
                String result = progressmap.get(search).getresult();
                progressmap.remove(search);
//                logs.append("Returning result for "+HTMLEncoder.encode(search)+"<br />\n");
                return result;
            }else return "No asyncronous search for "+HTMLEncoder.encode(search)+" found.";
        }else{
            String indexuri = request.isParameterSet("index") ? request.getParam("index") : DEFAULT_INDEX_SITE;
//            logs.append("JStest = "+request.getParam("JStest")+"<br />");
            boolean jstest = (request.getParam("JStest").equals("true"));

            return handleInner(request.getPath(), search, indexuri, jstest);
        }
    }
    
	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException{
        String search = request.getPartAsString("search", 80);
		String indexuri = request.isPartSet("index") ? request.getPartAsString("index", 200) : DEFAULT_INDEX_SITE;
		boolean showprogress = (request.getPartAsString("JStest", 5).equals("true"));

		return handleInner(request.getPath(), search, indexuri, showprogress);
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
		out.append("<form method=\"GET\"><table width=\"100%\">\n");
		out.append("    <tr><td rowspan=2 width=\"300\"><H1>"+plugName+"</H1></td>\n");
		out.append("        <td width=400><input type=\"text\" value=\"").append(search).append("\" name=\"search\" size=40/>\n");
		out.append("            <input type=submit name = \"find\" value=\"Find!\" TABINDEX=1/></td></tr>\n");
		out.append("    <tr><td>Index <input type=\"text\" name=\"index\" value=\"").append(index).append("\" size=40/>\n");
		out.append("</tr></table><div height=0 style=\"visibility:hidden;\"><input type=submit name = \"find\" value=\"Find!\" TABINDEX=1/></div></form>\n");
        
    }
    
    private void appendStatusDisplay(StringBuilder out, String search, String indexuri)
    {
        out.append("<tr><td width=\"140\">Search status : </td><td><div id=\"librarian-search-status\"><noscript><iframe frameborder=0 width=640 height=60 src=\"/plugins/plugins.XMLLibrarian.XMLLibrarian/progress?search="+HTMLEncoder.encode(search)+"&format=html\"></iframe></noscript></div></td></tr></table>\n");
        out.append("<p></p>\n\n");
        out.append("<div id=\"librarian-search-results\"></div>");
        
        out.append("<script language=\"JavaScript\">\n");
        out.append("function getresult(){\n");
        out.append("	var xmlHttp=new XMLHttpRequest();\n");
        out.append("	xmlHttp.onreadystatechange = function(){\n");
        out.append("		if(xmlHttp.readyState==4)\n");
        out.append("                document.getElementById(\"librarian-search-results\").innerHTML=xmlHttp.responseText;\n");
        out.append("    }\n");
        out.append("	xmlHttp.open(\"GET\",\"/plugins/plugins.XMLLibrarian.XMLLibrarian/result?search="+HTMLEncoder.encode(search)+"\",true);\n");
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
        out.append("	xmlHttp.open(\"GET\",\"/plugins/plugins.XMLLibrarian.XMLLibrarian/progress?search="+HTMLEncoder.encode(search)+"&format=plain\",true);\n");
        out.append("	xmlHttp.send(null);\n}\n");
        out.append("document.getElementById(\"librarian-search-status\").innerHTML=\"\";\n");
        out.append("requestStatus();\n");
        out.append("</script>\n");
    }

	private String handleInner(String path, String search, String indexuri, boolean showprogress) {
		StringBuilder out = new StringBuilder();
        
        search = HTMLEncoder.encode(search);

		appendDefaultPageStart(out);
		appendDefaultPostFields(out, search, indexuri);

		try {
			if (indexuri.equals(""))
				out.append("Specify a valid index \n");
			else if(search.equals(""))
                out.append("Give a valid string to search ");
            else{
                if(!progressmap.containsKey(search))
                    Search.setup(pr, this);


                out.append("<table width=\"100%\"><tr><td colspan=\"2\"><span class=\"librarian-searching-for-header\">Searching for </span>\n");
                out.append("<span class=\"librarian-searching-for-target\"><b>"+HTMLEncoder.encode(search)+"</b></span> in index <i>"+HTMLEncoder.encode(indexuri)+"</i></td></tr>\n");
                
                
                appendStatusDisplay(out, search, indexuri);
                
                
                if(!progressmap.containsKey(search)){ // If identical search is not taking place
                    // Set up progressing
                    Progress progress = new Progress(search, indexuri, "Searching for "+HTMLEncoder.encode(search), pr);
                    progressmap.put(search, progress);
                    //Start search
                    Search.searchStrAsync(out, search, indexuri, progress);
                }else if (progressmap.get(search).isdone()){     // If search is conplete show results
                    out.append(progressmap.get(search).getresult());
                    progressmap.remove(search);
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
        //Util.logs = logs;
        Util.hlsc = pr.getHLSimpleClient();
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
