/**
 * Web reuqest handlers
 * 
 * @author j16sdiz (1024D/75494252)
 */
package plugins.Library.ui;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageMaker;
import freenet.clients.http.ToadletContainer;
import freenet.node.NodeClientCore;
import freenet.pluginmanager.PluginRespirator;
import plugins.Library.Library;
import plugins.Library.Main;


public class WebInterface {
	private PageMaker pageMaker;
	private PageToadlet[] toadlets;
	private final ToadletContainer toadletContainer;
	private final HighLevelSimpleClient client;
	private final NodeClientCore core;
	private Library library;
	private final PluginRespirator pr;

	/**
	 * @param spider
	 * @param client 
	 */
	public WebInterface(Library library, PluginRespirator pr) {
		this.library = library;

		pageMaker = pr.getPageMaker();
		this.toadletContainer = pr.getToadletContainer();
		this.client = pr.getHLSimpleClient();
		this.core = pr.getNode().clientCore;
		this.pr = pr;
	}

	/**
	 * Load the Library interface into the FProxy interface
	 */
	public void load() {
		//pageMaker.addNavigationCategory("/library/", "Library", "Library", new Main());

		toadlets  = new PageToadlet[]{
			new PageToadlet(client, library, core, new MainPage(library, pr)),
		};

		for (PageToadlet toadlet : toadlets) {
			toadletContainer.register(toadlet, toadlet.menu(), toadlet.path(), true, toadlet.name(), toadlet.name(), true, null );
		}
		toadletContainer.register(new StaticToadlet(client), null, "/library/static/", true, false);
		toadletContainer.register(new ProgressPageToadlet(client, library, pr), null, "/library/xml/", true, false);
		
	}

	/**
	 * UNload the Library interface form the FProxy interface
	 */
	public void unload() {
		for (PageToadlet pageToadlet : toadlets) {
			toadletContainer.unregister(pageToadlet);
			pageMaker.removeNavigationLink(pageToadlet.menu(), pageToadlet.name());
		}
		//pageMaker.removeNavigationCategory("Library");
	}
}
