/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.fcp;

import freenet.pluginmanager.PluginNotFoundException;
import freenet.support.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import plugins.Library.Library;
import plugins.Library.search.InvalidSearchException;
import plugins.Library.search.Request;

/**
 * The methods exposed by Library over FCP, instructions for adding methods in the source
 *
 * @author MikeB
 */
class FCPExposedMethods {
	static HashMap<Integer, Request> requests = new HashMap();

	static private Integer requestnumber(Request req){
		int n = 0;
		while(requests.get(n)!=null)
			n++;
		requests.put(n, req);
		return n;
	}


	//////// Exposed methods from here, only classes in ParameterTypes should be used for parameters and return types
	//////// Other than that it should be ok, although exceptions wont be passed back properly

	//TODO minor : if these methods weren't static we could use an interface to bind this to RemoteLibrary

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

	/**
	 * Return the results of this request
	 * @param request
	 * @return
	 * @throws plugins.Library.search.InvalidSearchException
	 */
	static public Set getResults(Integer request) throws InvalidSearchException {
		return (Set)requests.get(request).getResult();
	}
}
