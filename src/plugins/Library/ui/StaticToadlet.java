package plugins.Library.ui;

import java.io.IOException;
import java.net.URI;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.api.HTTPRequest;
import plugins.Library.Library;


/**
 * Encapsulates a WebPage in a Toadlet
 * @author MikeB
 */
class StaticToadlet extends Toadlet {
	
	StaticToadlet(HighLevelSimpleClient client) {
		super(client);
	}

	/**
	 * Get the path to this page
	 * @return
	 */
	@Override
	public String path() {
		return "/library/static/";
	}

	@Override
	public String supportedMethods() {
		return "GET";
	}

	@Override
	public void handleGet(URI uri, final HTTPRequest httprequest, final ToadletContext ctx)
	throws ToadletContextClosedException, IOException, RedirectException {
		ClassLoader origClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(Library.class.getClassLoader());
		try {
			StaticPage page = StaticPage.valueOf(httprequest.getPath().substring(path().length()));
			if (page == null)
				this.sendErrorPage(ctx, 404, "Not found", "Could not find " + httprequest.getPath().substring(path().length()) + " in " + path());
			Integer request = httprequest.getIntParam("request");		// Make sure String aren't allowed here
			boolean showold = httprequest.isParameterSet("showold");
			this.writeReply(ctx, 200, page.mimetype, "Success", page.content.replaceAll("\\$request", request.toString()).replaceAll("\\$showold", showold ? "showold=on":""));
		} finally {
			Thread.currentThread().setContextClassLoader(origClassLoader);
		}
	}



	public enum StaticPage {
		scriptjs("application/javascript",
			"var url = '/library/xml/?request=$request&$showold';\n" +
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
			"		var resp = xmlhttp.responseXML;\n" +
			"		var progresscontainer = document.getElementById('librarian-search-status');\n" +
			"		progresscontainer.replaceChild(resp.getElementsByTagName('progress')[0].cloneNode(true), progresscontainer.getElementsByTagName('table')[0]);\n" +
			"		if(resp.getElementsByTagName('progress')[0].attributes.getNamedItem('RequestState').value=='FINISHED')\n" +
			"			document.getElementById('results').appendChild(" +
							"resp.getElementsByTagName('result')[0].cloneNode(true));\n" +
			"		else if(resp.getElementsByTagName('progress')[0].attributes.getNamedItem('RequestState').value=='ERROR')\n" +
			"			document.getElementById('errors').appendChild(" +
							"resp.getElementsByTagName('error')[0].cloneNode(true));\n" +
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
			"}\n"
		),



		detectjs("application/javascript",
			"window.location='/library/?js&request=$request';\n"
		),



		stylecss("text/css",
			//"body {font-family:sans-serif;\nbackground:white;}\n" +
			"table.librarian-result { width: 95%; border: 0px 8px; cellspacing: 0; cellpadding 0; }\n" +
			"#result { padding-left: left; }\n" +
			".result-group { padding : 0px; padding-bottom: 6px; }\n" +
			".result-grouptitle { font-size: large; font-weight: bold; margin-bottom: 0px; margin-top: 6px;  }\n" +
			".result-editiontitle-new { display:inline; padding-top: 5px; font-size: medium; color: black; }\n" +
			".result-editiontitle-old { display:inline; padding-top: 5px; font-size: medium; color: darkGrey }\n" +
			".result-entry { margin-bottom: 10px; }\n" +
			".result-sitename {color:black; font-weight:bold}\n" +
			".result-table { border-spacing : 5px; }\n" +
			"a.result-title-new:link { color: Blue } \n" +
			"a.result-title-new:visited { color: Purple } \n" +
			"a.result-title-old:link { color: LightBlue } \n" +
			"a.result-title-old:visited { color: Orchid } \n" +
			"a.result-url-new {color:Green; font-size:small; padding-left:15px}\n" +
			"a.result-url-old {color:LightGreen; font-size:small; padding-left:15px}\n" +
			"a.result-uskbutton-new {color: #480000; font-variant: small-caps; font-size: small; padding-left: 20px}\n" +
			"a.result-uskbutton-old {color: #996666; font-variant: small-caps; font-size: small; padding-left: 20px}\n" +
			".progress-table {border-spacing:10px 0px;}\n" +
			"table.progress-table td { padding: 5px 15px; }\n" +
			"td	.progress-bar-outline { padding: 0px 3px; width:300px; border:1px solid grey; height : 20px; border-spacing: 0px; }\n" +
			"div.progress-bar-inner-final { background-color: red; height:20px; z-index:-1; border-spacing: 0px; }\n" +
			"div.progress-bar-inner-nonfinal { background-color: pink; height:20px; z-index:-1; border-spacing: 0px; }\n" +
			//"div#navbar { background-color: white; border : none; } \n" +
			//"div#navbar ul { text-align : left; }\n" +
			//"th, td { border: none; padding: 0; }\n" +
			//"div#navbar ul li:hover ul, div#navbar ul li ul:hover { top: 1.1em; border: 1px solid #666633; background-color: #CCFFBB; }\n" +
			//"h1, h2 { font-size: xx-large; font-weight: bold; }\n" +
			"input.index { font-size: 0.63em; size: 25; height:1.1em }\n" +
			"li.index {  }\n" +
			"td.sskeditionbracket { background: black; width: 2px; padding: 0px; }\n" +
			"ul.index-bookmark-list { list-style: none; }\n" +
			"ul.options-list { list-style-type: none; margin-top: none; }\n" +
			"" +
			"div#content th, td { border: none; }\n" +
			"div.authorization-box { background: darksalmon; border: 1px solid darkred; padding: 8px; }\n" +
			"div.authorization-box h1 { font-size: 16pt; font-weight: bold; margin-top: 0px; }\n" +
			"div.authorization-box a:link { color: blue; }\n"
		);


		String mimetype;
		String content;
		StaticPage(String mime, String content){
			mimetype = mime;
			this.content = content;
		}
	}
}
