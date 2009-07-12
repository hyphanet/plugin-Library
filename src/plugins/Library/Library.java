package plugins.Library;

import plugins.Library.Index;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;
import java.security.MessageDigest;
import plugins.Library.interfaces.WebUI;


/**
 * Library class is the api for others to use search facilities, it is used by the interfaces
 * @author MikeB
 */
public class Library implements FredPlugin, FredPluginHTTP, FredPluginVersioned, FredPluginRealVersioned, FredPluginThreadless {
	// Library functions


	/**
	 * Find the specified index and start a find request on it for the specified term
	 */
	public static Request findTerm(String indexid, String term) throws Exception{
		Index index = getIndex(indexid);
		Logger.minor(Library.class, "Finding term: "+term);
		Request request = index.find(term);
		return request;
	}
	
	
	/**
	 * Gets an index using its id in the form {type}:{uri} <br />
	 * known types are xml, bookmark
	 * @param indexid
	 * @return Index object
	 * @throws plugins.XMLLibrarian.InvalidSearchException
	 */
	public static Index getIndex(String indexid) throws InvalidSearchException{
		Logger.minor(Library.class, "Getting Index: "+indexid);
		return Index.getIndex(indexid);
	}




	public static final String DEFAULT_INDEX_SITE = "bookmark:freenetindex";
	private static int version = 1;
	private static final String plugName = "(Library " + version+")";
	private PluginRespirator pr;


	// FredPluginVersioned
	public String getVersion() {
		return version + " r" + Version.getSvnRevision();
	}

	// FredPluginRealVersioned
	public long getRealVersion() {
		return version;
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
		this.pr = pr;
        Search.setup();
		WebUI.setup(plugName);
		Index.setup(pr);
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
}
