package plugins.Library.ui;

import java.io.IOException;
import java.net.URI;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageNode;
import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.node.NodeClientCore;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;
import plugins.Library.Library;


/**
 * Encapsulates the MainPage in a Toadlet
 * @author MikeB
 */
class MainPageToadlet extends Toadlet {
	private NodeClientCore core;
	private final Library library;
	private final PluginRespirator pr;


	MainPageToadlet(HighLevelSimpleClient client, Library library, NodeClientCore core, PluginRespirator pr) {
		super(client);
		this.core = core;
		this.library = library;
		this.pr = pr;
	}


	@Override
	public void handleGet(URI uri, final HTTPRequest request, final ToadletContext ctx) 
	throws ToadletContextClosedException, IOException, RedirectException {
		ClassLoader origClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(Library.class.getClassLoader());
		try {
			PageNode p = ctx.getPageMaker().getPageNode(Library.plugName, ctx);
			// Style
			p.headNode.addChild("link", new String[]{"rel", "href", "type"} , new String[]{"stylesheet", path() + "static/style.css", "text/css"});
			HTMLNode pageNode = p.outer;
			HTMLNode contentNode = p.content;

			// process the request
			MainPage page = MainPage.processGetRequest(request);
			if(page == null)
				page = new MainPage(library, pr);
			MultiValueTable<String, String> headers = new MultiValueTable();
			page.writeContent(contentNode, headers);
			// write reply
			writeHTMLReply(ctx, 200, "OK", headers, pageNode.generate());
		} catch(RuntimeException e) {
			PageNode p = ctx.getPageMaker().getPageNode(Library.plugName, ctx);
			// Style
			p.headNode.addChild("link", new String[]{"rel", "href", "type"} , new String[]{"stylesheet", path() + "static/style.css", "text/css"});
			HTMLNode pageNode = p.outer;
			HTMLNode contentNode = p.content;
			MainPage errorpage = new MainPage(e, library, pr);
			MultiValueTable<String, String> headers = new MultiValueTable();
			errorpage.writeContent(contentNode, headers);
			writeHTMLReply(ctx, 200, "OK", headers, pageNode.generate());
		} finally {
			Thread.currentThread().setContextClassLoader(origClassLoader);
		}
	}
	
	@Override
	public void handlePost(URI uri, HTTPRequest request, final ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		ClassLoader origClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(Library.class.getClassLoader());

		String formPassword = request.getPartAsString("formPassword", 32);

		try {
			// Get the nodes of the page
			PageNode p = ctx.getPageMaker().getPageNode(Library.plugName, ctx);
			// Style
			p.headNode.addChild("link", new String[]{"rel", "href", "type"} , new String[]{"stylesheet", path() + "static/style.css", "text/css"});
			HTMLNode pageNode = p.outer;
			HTMLNode contentNode = p.content;
			
			MainPage page = MainPage.processPostRequest(request, contentNode, formPassword!=null && formPassword.equals(core.formPassword), library, pr);
			if(page==null)
				page = new MainPage(library,pr);
			// Process the request
			MultiValueTable<String, String> headers = new MultiValueTable();
			// write reply
			page.writeContent(contentNode, headers);
			// write reply
			writeHTMLReply(ctx, 200, "OK", headers, pageNode.generate());
		} catch(RuntimeException e) {
			PageNode p = ctx.getPageMaker().getPageNode(Library.plugName, ctx);
			// Style
			p.headNode.addChild("link", new String[]{"rel", "href", "type"} , new String[]{"stylesheet", path() + "static/style.css", "text/css"});
			HTMLNode pageNode = p.outer;
			HTMLNode contentNode = p.content;
			// makes a mainpage for showing errors
			MainPage errorpage = new MainPage(e, library, pr);
			MultiValueTable<String, String> headers = new MultiValueTable();
			errorpage.writeContent(contentNode, headers);
			writeHTMLReply(ctx, 200, "OK", headers, pageNode.generate());
		} finally {
			Thread.currentThread().setContextClassLoader(origClassLoader);
		}
	}
	
	
	
	@Override public String path() {
		return MainPage.path();
	}
	
	@Override public String supportedMethods() {
		return "GET, POST";
	}

	public String name() {
		return "WelcomeToadlet.searchFreenet";
	}

	public String menu() {
		return "FProxyToadlet.categoryBrowsing";
	}
}
