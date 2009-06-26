package plugins.XMLLibrarian;

import plugins.XMLLibrarian.interfaces.L10nString;
import java.security.MessageDigest;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.api.HTTPRequest;
import freenet.pluginmanager.FredPluginL10n;
import freenet.l10n.L10n.LANGUAGE;

import plugins.XMLLibrarian.interfaces.WebUI;



public class XMLLibrarian implements FredPlugin, FredPluginHTTP, FredPluginVersioned, FredPluginRealVersioned, FredPluginThreadless, FredPluginL10n {
	public static final String DEFAULT_INDEX_SITE = "bookmark:wanna19";
	private static int version = 22;
	private static final String plugName = "XMLLibrarian " + version;
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
			return WebUI.searchpage(null, null, false, e);
		}
	}
    
	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException{
        return WebUI.handleHTTPPost(request);
    }


	// FredPlugin
	public void runPlugin(PluginRespirator pr) {
		this.pr = pr;
        Search.setup(this);
		WebUI.setup(this, plugName);
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
	
	
	
	// FredPluginL10n
	public String getString(String key){
		return L10nString.getString(key);
	}
	
	public void setLanguage(LANGUAGE newLanguage){
		L10nString.setLanguage(newLanguage);
	}
}
