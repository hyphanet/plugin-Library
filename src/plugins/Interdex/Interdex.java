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

import plugins.Interdex.util.*;
import plugins.Interdex.serl.*;
import plugins.Interdex.serl.Serialiser.*;
import plugins.Interdex.index.*;

import java.util.Random;
import java.util.TreeSet;
import java.util.SortedSet;

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

	public String rndStr() {
		return java.util.UUID.randomUUID().toString();
	}

	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>> test = new
		SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>(new Token(), 1024);

		IndexFileSerialiser f = new IndexFileSerialiser();
		test.setSerialiser(f.s, f.sv);

		Random rand = new Random();

		for (int i=0; i<4096; ++i) {
			String key = rndStr().substring(0,8);
			SortedSet<TokenEntry> entries = new TreeSet<TokenEntry>();
			int n = rand.nextInt(240) + 16;

			try {
				for (int j=0; j<n; ++j) {
					TokenEntry e = new TokenURIEntry(key, new FreenetURI("CHK@" + rndStr().replace('-', 'Z')));
					e.setRelevance((float)Math.random());
					entries.add(e);
				}
			} catch (java.net.MalformedURLException e) {
				return "malformed URL";
			}

			test.put(new Token(key), entries);
		}

		test.deflate();
		PushTask task = new PushTask(test);
		f.s.push(task);
		return request.toString() + "<br />Hi<br />" + task.meta.toString();
		//return request.toString();
	}

	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		return request.toString();
	}

	public void runPlugin(PluginRespirator pr) {
		this.pr = pr;
	}

}
