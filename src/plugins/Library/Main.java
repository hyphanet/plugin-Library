/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package plugins.Library;

import plugins.Library.Library;
import plugins.Library.fcp.FCPRequestHandler;
import plugins.Library.search.Search;
import plugins.Library.ui.WebUI;

import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.api.HTTPRequest;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

import java.security.MessageDigest;


/**
 * Library class is the api for others to use search facilities, it is used by the interfaces
 * @author MikeB
 */
public class Main implements FredPlugin, FredPluginHTTP, FredPluginVersioned,
		FredPluginRealVersioned, FredPluginThreadless, FredPluginFCP {
	private static PluginRespirator pr;
	private String plugName = "Library " + Library.getVersion();


	// FredPluginVersioned
	public String getVersion() {
		return Library.getVersion() + " r" + Version.getSvnRevision();
	}

	// FredPluginRealVersioned
	public long getRealVersion() {
		return Library.getVersion();
	}



	// FredPluginHTTP
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException{
		try{
			return WebUI.handleHTTPGet(request);
		}catch(Exception e){
			return WebUI.searchpage(null, null, false, request.isParameterSet("js"), false, e);
		}
	}

	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException{
        return WebUI.handleHTTPPost(request);
    }


	// FredPlugin
	public void runPlugin(PluginRespirator pr) {
		Main.pr = pr;
        Search.setup();
		WebUI.setup(plugName);
		Library.setup(pr);
	}

	public void terminate() {
	}



	private static String convertToHex(byte[] data) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < data.length; i++) {
			int halfbyte = (data[i] >>> 4) & 0x0F;
			int two_halfs = 0;
			do {
				if ((0 <= halfbyte) && (halfbyte <= 9))
					buf.append((char) ('0' + halfbyte));
				else
					buf.append((char) ('a' + (halfbyte - 10)));
				halfbyte = data[i] & 0x0F;
			} while (two_halfs++ < 1);
		}
		return buf.toString();
	}

	//this function will return the String representation of the MD5 hash for the input string
	public static String MD5(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] b = text.getBytes("UTF-8");
			md.update(b, 0, b.length);
			byte[] md5hash = md.digest();
			return convertToHex(md5hash);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// FredPluginFCP
	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		FCPRequestHandler.handle(replysender, params, data, accesstype);
	}
}
