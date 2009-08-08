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
import freenet.support.HTMLNode;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;
import plugins.Library.Library;


/**
 * Encapsulates a WebPage in a Toadlet
 * @author MikeB
 */
class PageToadlet extends Toadlet {
	private NodeClientCore core;
	private WebPage webpage;
	private final Library library;


	PageToadlet(HighLevelSimpleClient client, Library library, NodeClientCore core, WebPage webpage) {
		super(client);
		this.core = core;
		this.webpage = webpage;
		this.library = library;
	}

	/**
	 * Get the path to this page
	 * @return
	 */
	@Override
	public String path() {
		return webpage.path();
	}

	/**
	 * The name of this page
	 * @return
	 */
	public String name() {
		return webpage.name();
	}

	@Override
	public String supportedMethods() {
		return webpage.supportedMethods();
	}

	@Override
	public void handleGet(URI uri, final HTTPRequest request, final ToadletContext ctx) 
	throws ToadletContextClosedException, IOException, RedirectException {
		ClassLoader origClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(Library.class.getClassLoader());
		try {
			PageNode p = ctx.getPageMaker().getPageNode(library.plugName, ctx);
			// Style
			p.headNode.addChild("link", new String[]{"rel", "href", "type"} , new String[]{"stylesheet", path() + "static/stylecss", "text/css"});
			HTMLNode pageNode = p.outer;
			HTMLNode contentNode = p.content;
			// Make a clone of the webpage so state information doesnt stay between requests
			WebPage page = webpage.clone();
			MultiValueTable<String, String> headers = new MultiValueTable();
			// process the request
			page.processGetRequest(request);
			page.writeContent(contentNode, headers);
			// write reply
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
		// If the form password is incorrect, redirect to this page
		if((formPassword == null) || !formPassword.equals(core.formPassword)) {
			MultiValueTable<String,String> headers = new MultiValueTable<String,String>();
			headers.put("Location", path());
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			return;
		}

		try {
			PageNode p = ctx.getPageMaker().getPageNode(library.plugName, ctx);
			HTMLNode pageNode = p.outer;
			HTMLNode contentNode = p.content;
			// Make a clone of the webpage so state information doesnt stay between requests
			WebPage page = webpage.clone();
			MultiValueTable<String, String> headers = new MultiValueTable();
			// write reply
			page.processPostRequest(request, contentNode, formPassword.equals(core.formPassword));
			page.writeContent(contentNode, headers);
			// write reply
			writeHTMLReply(ctx, 200, "OK", headers, pageNode.generate());
		} finally {
			Thread.currentThread().setContextClassLoader(origClassLoader);
		}
	}

	String menu() {
		return webpage.menu();
	}
}
