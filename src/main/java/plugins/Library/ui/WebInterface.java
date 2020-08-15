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
	private final ToadletContainer toadletContainer;
	private final HighLevelSimpleClient client;
	private final NodeClientCore core;
	private Library library;
	private final PluginRespirator pr;
	private MainPageToadlet pluginsToadlet;
	private MainPageToadlet mainToadlet;
	private StaticToadlet staticToadlet;

	/**
	 * // @param spider
	 * // @param client
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

		mainToadlet = new MainPageToadlet(client, library, core, pr);
		toadletContainer.register(mainToadlet, mainToadlet.menu(), mainToadlet.path(), true, mainToadlet.name(), mainToadlet.name(), true, null );

		// Ive just realised that the form filter allows access to /plugins/... so /library/ wont be allowed, this is a temporary Toadlet untilthere is a whitelist for formfilter and /library is on it TODO put /library on formfilter whitelist
		pluginsToadlet = new MainPageToadlet(client, library, core, pr);
		toadletContainer.register(pluginsToadlet, null, "/plugins/plugin.Library.FreesiteSearch", true, null, null, true, null );
		staticToadlet = new StaticToadlet(client);
		toadletContainer.register(staticToadlet, null, "/library/static/", true, false);

	}

	/**
	 * UNload the Library interface form the FProxy interface
	 */
	public void unload() {
		toadletContainer.unregister(mainToadlet);
		pageMaker.removeNavigationLink(mainToadlet.menu(), mainToadlet.name());
		toadletContainer.unregister(pluginsToadlet);
		toadletContainer.unregister(staticToadlet);
	}
}
