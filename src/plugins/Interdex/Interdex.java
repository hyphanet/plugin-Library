/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex;

import freenet.client.FetchException;
import freenet.keys.FreenetURI;
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
** @author infinity0
*/
public class Interdex implements FredPlugin, FredPluginHTTP, FredPluginVersioned, FredPluginRealVersioned, FredPluginThreadless {

	private static int version = 2;
	private static final String plugName = "Interdex " + version;
	private PluginRespirator pr;

	public String getVersion() {
		return version + " r" + Version.getSvnRevision();
	}

	public long getRealVersion() {
		return version;
	}

	public void terminate() {
		// pass
	}

	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		return request.toString() + "<br />Hi<br />";
		//return request.toString();
	}

	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		return request.toString();
	}

	public void runPlugin(PluginRespirator pr) {
		this.pr = pr;
	}

}
