/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex;

import java.io.IOException;

import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;

/**
** @author infinity0
*/
public class Interdex implements FredPlugin, FredPluginHTTP, FredPluginFCP, FredPluginVersioned, FredPluginRealVersioned {

	private static final String plugName = "Interdex " + Version.version;
	private PluginRespirator pr;

	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		return "testing";
	}

	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		return "testing2";
	}

	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		return;
	}

	public void runPlugin(PluginRespirator p) {
		pr = p;
	}

	public void terminate() {
		// TODO kill all 'session handles'
		// TODO kill all requests
	}

	public String getVersion() {
		return Version.svnRevision;
	}

	public String getString(String key) {
		return key;
	}

	public long getRealVersion() {
		return Version.version;
	}

}
