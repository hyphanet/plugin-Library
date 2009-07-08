package plugins.XMLLibrarian.xmlindex;

import java.io.File;
import java.net.MalformedURLException;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.keys.FreenetURI;
import freenet.support.api.Bucket;
import freenet.support.io.FileBucket;

/**
 * Utility class
 * 
 * @author  j16sdiz (1024D/75494252)
 */
public class Util {
	
	public static Bucket fetchBucket(String uri, HighLevelSimpleClient hlsc) throws FetchException, MalformedURLException {
		// try local file first
		File file = new File(uri);
		if (file.exists() && file.canRead()) 
			return new FileBucket(file, true, false, false, false, false);
		else if (hlsc==null)
			throw new NullPointerException("No client or file "+uri+" found");
		
		// FreenetURI, try to fetch from freenet
		FreenetURI u = new FreenetURI(uri);
		FetchResult res;
		while (true) {
			try {
				res = hlsc.fetch(u);
				break;
			} catch (FetchException e) {
				if (e.newURI != null) {
					u = e.newURI;
					continue;
				} else
					throw e;
			}
		}

		return res.asBucket();
	}
	
	public static boolean isValid(String uri) {
		// try local file first
		File file = new File(uri);
		if (!file.exists() || !file.canRead()) {
			// FreenetURI, try to fetch from freenet
			try{
				FreenetURI u = new FreenetURI(uri);
			}catch(MalformedURLException e){
				return false;
			}
		}
		return true;
	}
}


