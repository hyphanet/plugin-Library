
package plugins.Library.fcp;

import freenet.pluginmanager.PluginNotFoundException;
import freenet.support.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import plugins.Library.Library;
import plugins.Library.util.InvalidSearchException;
import plugins.Library.util.Request;

/**
 * The methods exposed by Library over FCP
 *
 * @author MikeB
 */
public class FCPExposedMethods{
	static HashMap<Integer, Request> requests = new HashMap();
	
	static private Integer requestnumber(Request req){
		int n = 0;
		while(requests.get(n)!=null)
			n++;
		requests.put(n, req);
		return n;
	}





	
	//////// Exposed methods from here, only classes in FCPRequestHandler.ParameterTypes should be used for parameters and return types

	/**
	 * @return the version number of Library
	 * @throws freenet.pluginmanager.PluginNotFoundException if Library is not loaded
	 */
	static public Integer getVersion() {
		Logger.normal(FCPExposedMethods.class, "get version from library");
		return 0;
	}

	/**
	 * Find a single term in a single index
	 * @param indexid
	 * @param term
	 * @return id of the Request
	 * @throws java.lang.Exception
	 * @throws freenet.pluginmanager.PluginNotFoundException if Library is not loaded
	 */
	static public Integer findTerm(String indexid, String term) throws Exception {
		return requestnumber(Library.findTerm(indexid, term));
	}

	/**
	 * Adds a page and it's meta data to the Library
	 * @param uri of this page
	 * @param title of this page
	 * @param meta key-value pairs of meta data about this page
	 * @return id of page for modification
	 */
	static public Integer addPage(String uri, String title, Map<String, String> meta) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	static public Set getResults(Integer request) throws InvalidSearchException {
		return requests.get(request).getResult();
	}
}
