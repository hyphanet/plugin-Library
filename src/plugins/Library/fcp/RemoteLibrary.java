/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.fcp;

import freenet.pluginmanager.FredPluginTalker;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.PluginTalker;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import java.lang.instrument.IllegalClassFormatException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import plugins.Library.Main;

/**
 * This is the remote FCP interface for Library, these methods call synonomous methods in {@link plugins.Library.fcp.FCPExposedMethods}
 *
 * @author MikeB
 */
public class RemoteLibrary implements FredPluginTalker {
	PluginRespirator pr;
	private final HashMap<String, Object> returnValues;

	public RemoteLibrary(PluginRespirator pr){
		this.pr = pr;
		returnValues = new HashMap();
	}

	/**
	 * Call a remote method in the library
	 * @param method name of method
	 * @param returntype type of return value
	 * @param params the parameters of this function
	 * @return return value of remote function
	 * @throws freenet.pluginmanager.PluginNotFoundException if Library plugin isn't loaded
	 * @throws java.util.concurrent.TimeoutException if Library takes too long to respond
	 * @throws java.lang.instrument.IllegalClassFormatException if the Library returns an invalid type
	 */
	private Object invokeRemoteMethod(String method, String returntype, Object... params) throws PluginNotFoundException, TimeoutException, IllegalClassFormatException{
		String identifier = method+":"+Thread.currentThread().getName();
		PluginTalker pt = pr.getPluginTalker(this, Main.class.getName(), identifier);
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
		Object returnval = returnValues.get(identifier);
		if(returnval.equals(identifier))
			throw new TimeoutException("Timeout waiting for response from call "+identifier);
		return ParameterTypes.convert(returntype, returnval);
	}

	/**
	 * Receive a reply from the Library
	 * @param pluginname
	 * @param identifier for the related method call
	 * @param params response
	 * @param data
	 */
	public void onReply(String pluginname, String identifier, SimpleFieldSet params, Bucket data) {
		if(params.get("return") != null){
			Object returnval = null;
			if((returnval = params.subset("return")) == null)
				returnval = params.get("return");
			Object lock1 = returnValues.put(identifier, returnval);
			synchronized(lock1){
				lock1.notifyAll();
			}
		}else if(params.get("exception") != null)
			returnValues.put(identifier, params.get("exception"));	// Something is wrong
		// Something is wrong
	}
	
	
	
	////// The remote functions
	////// To add, make sure the method is specified the same in FCPExposedMethods
	////// invokeRemoteMethod will return either a string or 
	
	/**
	 * @return the version number of Library
	 * @throws freenet.pluginmanager.PluginNotFoundException if Library is not loaded
	 */
	// TODO handle all the exceptions much better
	public Integer getVersion() throws PluginNotFoundException, TimeoutException, IllegalClassFormatException {
		return (Integer)invokeRemoteMethod("getVersion", "Integer");
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
		return (Integer)invokeRemoteMethod("findTerm", "Integer", indexid, term);
	}

	/**
	 * Adds a page and it's meta data to the Library. If page already exists, any duplicate fields will be overwritten
	 * @param uri of this page
	 * @param title of this page
	 * @param meta key-value pairs of meta data about this page
	 * @return id of page for modification
	 */
	public Integer addPage(String uri, String title, Map<String, String> meta) throws PluginNotFoundException, TimeoutException, IllegalClassFormatException{
		return (Integer)invokeRemoteMethod("addPage", "Integer", uri, title, meta);
	}
}
