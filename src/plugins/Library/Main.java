/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package plugins.Library;

import freenet.l10n.L10n.LANGUAGE;
import plugins.Library.search.Search;
import plugins.Library.ui.WebUI;

import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginAPI;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.api.HTTPRequest;

import java.security.MessageDigest;
import plugins.Library.ui.WebInterface;


/**
 * Library class is the api for others to use search facilities, it is used by the interfaces
 * @author MikeB
 */
public class Main implements FredPlugin, FredPluginHTTP, FredPluginVersioned,
		FredPluginRealVersioned, FredPluginThreadless, FredPluginAPI, FredPluginL10n {
	private static PluginRespirator pr;
	private Library library;
	private WebInterface webinterface;


	// FredPluginVersioned
	public String getVersion() {
		return library.getVersion() + " r" + Version.getSvnRevision();
	}

	// FredPluginRealVersioned
	public long getRealVersion() {
		return library.getVersion();
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
		library = new Library(pr);
		Main.pr = pr;
        Search.setup(library);
		WebUI.setup(library);
		webinterface = new WebInterface(library, pr);
		webinterface.load();
	}

	public void terminate() {
		webinterface.unload();
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

	/**
	 * @return the Library API for other plugins
	 */
	public Object getPluginAPI() {
		return library;
	}

	public String getString(String key) {
		return key;
	}

	public void setLanguage(LANGUAGE newLanguage) {
		
	}
}
