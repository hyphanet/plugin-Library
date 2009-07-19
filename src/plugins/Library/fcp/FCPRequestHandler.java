/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.fcp;

import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Handles FCP requests for the Library
 *
 * @author MikeB
 */
public class FCPRequestHandler {
	static Class exposedMethods = FCPExposedMethods.class;

	
	/**
	 * Call any methods from {@link FCPExposedMethods} in response to FCP requests
	 * @param replysender
	 * @param params Specifies the method to call and the parameters, ("method"=>[Method name], [Type]_[Name]=>[Parameter])
	 * @param data
	 * @param accesstype
	 */
	public static void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		SimpleFieldSet returnSet = new SimpleFieldSet(true);
		try {
			String methodname = null;
			ArrayList<Class> parameterTypes = new ArrayList();
			ArrayList<Object> parameterValues = new ArrayList();
			Logger.normal(params, params.toOrderedString());
			// Get method name and parameters from params
			for (Iterator<String> it = params.keyIterator(); it.hasNext();) {
				String key = it.next();
				if (key.equalsIgnoreCase("method")) {
					methodname = params.get(key);
					Logger.normal(FCPRequestHandler.class, "method: "+methodname);
				} else {
					ParameterTypes type = ParameterTypes.valueOf(key.split("_")[0]);
					Object value = ParameterTypes.convert(key, params);
					parameterTypes.add(type.rclass);
					parameterValues.add(value);
					Logger.normal(FCPRequestHandler.class, "param("+type+") : "+value);
				}
			}
			Method remoteMethod = exposedMethods.getMethod(methodname, parameterTypes.toArray(new Class[0]));
			Logger.normal(remoteMethod, "invoking "+remoteMethod+" : "+methodname);
			Object returnval = null;
			try{
				returnval = remoteMethod.invoke(null, parameterValues.toArray());
			}catch(NullPointerException e){
				Logger.error(e, e.getMessage(), e);
			}
			ParameterTypes.convertPut("return", returnval, returnSet);
			returnSet.putOverwrite("return", returnval.toString());
		} catch (Exception ex) {
			// Ideally we could actually send back the exception, that would require changes to PluginReplySender and I guess it would need a seperate FCPTalkerException interface to receive it
			// This doesn't even send causes currently
			// TODO more robust error system, even if it only logs them and refers to them in an Exception generated the other side
			Logger.error(FCPRequestHandler.class, null, ex);
			ParameterTypes.convertPut("exception", ex, returnSet);
		}
		try {
			replysender.send(returnSet);
		} catch (PluginNotFoundException ex) {
			Logger.error(FCPRequestHandler.class, "Invalid plugin for reply", ex);
		}
	}

}
