package plugins.XMLLibrarian;

import java.security.MessageDigest;
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
import freenet.pluginmanager.FredPluginL10n;
import freenet.l10n.L10n;
import freenet.l10n.L10n.LANGUAGE;




/**
 * XMLLibrarian is a modified version of the old librarian. It uses the Xml index files for
 * searching. In addition to searching in a single index, XMLLibrarian allows searching in multiple
 * indices at the same time. Folders containing set of indices can be created and any search on a
 * folder searches the string in all the indices in the folder.
 * 
 * The current configuration can be stored in an external file and reused later. The default file
 * for the same is XMLLibrarian.xml.
 * 
 *The index list of a particular folder can be saved in an external file and likewise imported from
 * an existing file. XMLLibrarian assumes that the index to be used is present at
 * DEFAULT_INDEX_SITE/index.xml .
 * 
 * @author swatigoyal
 * 
 */

public class XMLLibrarian implements FredPlugin, FredPluginHTTP, FredPluginVersioned, FredPluginRealVersioned, FredPluginThreadless, FredPluginL10n {
	/**
	 * Default index site
	 */
	public static final String DEFAULT_INDEX_SITE = "USK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search/19/";
	/*
	 * Current configuration gets saved by default in the configfile. To Save the current
	 * configuration use "Save Configuration"
	 */
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
	
	public PluginRespirator getPluginRespirator(){
		return pr;
	}

	public void terminate() {
	}
	
	
	// FredPluginHTTP
    
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException{
		return WebUI.handleHTTPGet(request);
	}
    
    
	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException{
        return WebUI.handleHTTPPost(request);
    }



	public void runPlugin(PluginRespirator pr) {
		this.pr = pr;
        //Util.logs = logs;
        Util.hlsc = pr.getHLSimpleClient();
        Search.setup(this);
		WebUI.setup(this, plugName);
		Index.setup(this);
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
