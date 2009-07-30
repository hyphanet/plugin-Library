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
			Integer request = httprequest.getIntParam("request");
			boolean showold = httprequest.isParameterSet("showold");
			this.writeReply(ctx, 200, page.mimetype, "Success", page.content.replaceAll("\\$request", request.toString()).replaceAll("\\$showold", showold ? "showold=on":""));
		} finally {
			Thread.currentThread().setContextClassLoader(origClassLoader);
		}
	}



	enum StaticPage {
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
			"}\n"
		),



		detectjs("application/javascript",
			"window.location='/library/?js&request=$request;\n"
		),



		stylecss("text/css",
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
			"li.index {  }\n"
		);


		String mimetype;
		String content;
		StaticPage(String mime, String content){
			mimetype = mime;
			this.content = content;
		}
	}
}
