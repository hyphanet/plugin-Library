package plugins.XMLLibrarian;

import java.io.File;
import java.net.MalformedURLException;

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.FetchWaiter;
import freenet.client.FetchContext;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.node.RequestClient;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.api.Bucket;
import freenet.support.io.FileBucket;

/**
 * Utility class
 * 
 * @author  j16sdiz (1024D/75494252)
 */
public class Util {
    public static HighLevelSimpleClient hlsc;
    
	public static Bucket fetchBucket(String uri, PluginRespirator pluginRespirator, Progress progress) throws FetchException, MalformedURLException {
		// try local file first
		File file = new File(uri);
		if (file.exists() && file.canRead()) 
			return new FileBucket(file, true, false, false, false, false);

		// FreenetURI, try to fetch from freenet
		HighLevelSimpleClient hlsc = pluginRespirator.getHLSimpleClient();
		FreenetURI u = new FreenetURI(uri);
		FetchResult res;
		while (true) {
			try {
				res = fetch(u, progress);
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


    private static FetchResult fetch(FreenetURI uri, Progress progress) throws FetchException {
        if(uri == null) throw new NullPointerException();
		FetchContext context = progress.getFetchContext();
		FetchWaiter fw = new FetchWaiter();
		ClientGetter get = hlsc.fetch(uri, -1, progress, fw, context);
		return fw.waitForCompletion();
    }
}


