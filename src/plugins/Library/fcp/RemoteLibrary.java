
package plugins.Library.fcp;

import freenet.pluginmanager.FredPluginTalker;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.PluginTalker;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * This is the remote FCP interface for Library, these methods call synonomous methods in {@link plugins.Library.fcp.FCPExposedMethods}
 *
 * @author MikeB
 */
public class RemoteLibrary implements FredPluginTalker {
	PluginRespirator pr;
	private final HashMap<String, String> returnValues;

	public RemoteLibrary(PluginRespirator pr){
		this.pr = pr;
		returnValues = new HashMap();
	}

	private String invokeRemoteMethod(String method, Object... params) throws PluginNotFoundException, TimeoutException{
		String identifier = method+":"+Thread.currentThread().getName();
		PluginTalker pt = pr.getPluginTalker(this, "plugins.Library.Library", identifier);
		SimpleFieldSet plugparams = new SimpleFieldSet(true);
		plugparams.putOverwrite("method", method);
		for (Object object : params) {
			plugparams.putOverwrite(object.getClass().getSimpleName(), object.toString());
		}
		returnValues.put(identifier, identifier);
		pt.send(plugparams, null);
		try {
			// wait some amount of time for a reply, if none throw timeout exception or something, if theres a response return that
			synchronized(identifier){
				identifier.wait(30000);
			}
		} catch (InterruptedException ex) {
			Logger.error(this, "Wait interrupted", ex);
		}
		if(returnValues.get(identifier).equals(identifier))
			throw new TimeoutException("Timeout waiting for response from call "+identifier);
		return returnValues.get(identifier);
	}

	public void onReply(String pluginname, String identifier, SimpleFieldSet params, Bucket data) {
		if(params.get("return") != null){
			String lock1 = returnValues.put(identifier, params.get("return"));
			synchronized(lock1){
				lock1.notifyAll();
			}
		}else if(params.get("exception") != null)
			returnValues.put(identifier, params.get("exception"));	// Something is wrong
		// Something is wrong
	}




	/**
	 * @return the version number of Library
	 * @throws freenet.pluginmanager.PluginNotFoundException if Library is not loaded
	 */
	public Integer getVersion() throws PluginNotFoundException, TimeoutException {
		return Integer.valueOf(invokeRemoteMethod("getVersion"));
	}

	/**
	 * Find a single term in a single index
	 * @param indexid
	 * @param term
	 * @return id of the Request
	 * @throws java.lang.Exception
	 * @throws freenet.pluginmanager.PluginNotFoundException if Library is not loaded
	 */
	public Integer findTerm(String indexid, String term) throws Exception, PluginNotFoundException {
		return Integer.valueOf(invokeRemoteMethod("findTerm", indexid, term));
	}

	/**
	 * Adds a page and it's meta data to the Library. If page already exists, any duplicate fields will be overwritten
	 * @param uri of this page
	 * @param title of this page
	 * @param meta key-value pairs of meta data about this page
	 * @return id of page for modification
	 */
	public Integer addPage(String uri, String title, Map<String, String> meta) throws PluginNotFoundException, TimeoutException{
		return Integer.valueOf(invokeRemoteMethod("addPage", uri, title, meta));
	}
}
