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


public class WebInterface {
	private PageMaker pageMaker;
	private MainPageToadlet[] toadlets;
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

		toadlets  = new MainPageToadlet[]{
			new MainPageToadlet(client, library, core, pr),
		};

		for (MainPageToadlet toadlet : toadlets) {
			toadletContainer.register(toadlet, toadlet.menu(), toadlet.path(), true, toadlet.name(), toadlet.name(), true, null );
		}
		toadletContainer.register(new StaticToadlet(client), null, "/library/static/", true, false);
		
	}

	/**
	 * UNload the Library interface form the FProxy interface
	 */
	public void unload() {
		for (MainPageToadlet pageToadlet : toadlets) {
			toadletContainer.unregister(pageToadlet);
			pageMaker.removeNavigationLink(pageToadlet.menu(), pageToadlet.name());
		}
		//pageMaker.removeNavigationCategory("Library");
	}
}
