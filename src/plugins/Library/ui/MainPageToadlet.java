/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.ui;

import plugins.Library.Library;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageNode;
import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.node.NodeClientCore;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;

/**
 * Encapsulates the MainPage in a Toadlet
 * @author MikeB
 */
public class MainPageToadlet extends Toadlet {
	private NodeClientCore core;
	private final Library library;
	private final PluginRespirator pr;

	public MainPageToadlet(HighLevelSimpleClient client, Library library, NodeClientCore core, PluginRespirator pr) {
		super(client);
		this.core = core;
		this.library = library;
		this.pr = pr;
	}

	public void handleMethodGET(URI uri, final HTTPRequest request, final ToadletContext ctx)
	throws ToadletContextClosedException, IOException, RedirectException {
		ClassLoader origClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(Library.class.getClassLoader());
		try {
			// process the request
			String title = Library.plugName;
			MainPage page = MainPage.processGetRequest(request);
			if(page == null)
				page = new MainPage(library, pr);
			else {
				String query = page.getQuery();
				if(query != null && !query.isEmpty())
					title = query + " - " + Library.plugName;
			}
			PageNode p = ctx.getPageMaker().getPageNode(title, ctx);
			// Style
			p.headNode.addChild("link", new String[]{"rel", "href", "type"} , new String[]{"stylesheet", path() + "static/style.css", "text/css"});
			HTMLNode pageNode = p.outer;
			HTMLNode contentNode = p.content;

			MultiValueTable<String, String> headers = new MultiValueTable();
			page.writeContent(contentNode, headers);
			// write reply
			writeHTMLReply(ctx, 200, "OK", headers, pageNode.generate());
		} catch(RuntimeException e) {	// this way isnt working particularly well, i think the ctx only gives out one page maker
			Logger.error(this, "Runtime Exception writing main page", e);
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

	public void handleMethodPOST(URI uri, HTTPRequest request, final ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
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
			Logger.error(this, "Runtime Exception writing main page", e);
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

	public String name() {
		return "WelcomeToadlet.searchFreenet";
	}

	public String menu() {
		return "FProxyToadlet.categoryBrowsing";
	}
}
