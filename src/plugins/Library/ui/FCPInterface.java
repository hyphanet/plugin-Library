/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package plugins.Library.ui;

import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *
 * @author devl
 */
public class FCPInterface {
	static Class exposedMethods = FCPExposedMethods.class;


	enum ParameterTypes{
		Boolean(Boolean.class),
		Char(Character.class),
		Double(Double.class),
		Integer(Integer.class),
		String(String.class);

		ParameterTypes(Class rclass){
			this.rclass = rclass;
		}
		Class rclass;
	};

	/**
	 * Call any methods from {@link FCPExposedMethods}.
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
			for (Iterator<String> it = params.keyIterator(); it.hasNext();) {
				String key = it.next();
				if (key.equalsIgnoreCase("method")) {
					methodname = params.get(key);
				} else {
					String type = key.split("_")[0];
					String value = params.get(key);
					parameterTypes.add(ParameterTypes.valueOf(type).rclass);
					parameterValues.add(value);
				}
			}
			Method remoteMethod = exposedMethods.getMethod(methodname, (Class[]) parameterTypes.toArray());
			Object returnval = remoteMethod.invoke(null, parameterValues.toArray());
			returnSet.putOverwrite(returnval.getClass().getSimpleName()+"_return", returnval.toString());
		} catch (Exception ex) {
			Logger.error(FCPInterface.class, null, ex);
			returnSet.putOverwrite(ex.getClass().getName()+"_exception", ex.getMessage());
		}
		try {
			replysender.send(returnSet);
		} catch (PluginNotFoundException ex) {
			Logger.error(FCPInterface.class, "Invalid plugin for reply", ex);
		}
	}

}
