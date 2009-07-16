
package plugins.Library.fcp;

import freenet.node.FSParseException;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author MikeB
 */
public class FCPRequestHandler {
	static Class exposedMethods = FCPExposedMethods.class;


	/**
	 * The Parameter types alowed
	 * TODO Will need Exceptions, Collections
	 */
	enum ParameterTypes{
		Boolean(Boolean.class){
			@Override
			Boolean stringValue(String string){
				return java.lang.Boolean.valueOf(string);
			}
		},
		Char(Character.class),
		Double(Double.class),
		Integer(Integer.class){
			@Override
			Integer stringValue(String string){
				return java.lang.Integer.valueOf(string);
			}
		},
		String(String.class){
			@Override
			String stringValue(String string){
				return string;
			}
		},
		Set(SimpleFieldSet.class){
			@Override
			Set sfcValue(SimpleFieldSet sfs) throws IllegalClassFormatException{
				Set set = new HashSet();
				for (Iterator<String> it = sfs.keyIterator(); it.hasNext();) {
					String key = it.next();
					set.add(convert(key, sfs));
				}
				return set;
			}
			SimpleFieldSet toSimpleFieldSet(Set set){
				SimpleFieldSet result = new SimpleFieldSet(true);
				for (Iterator it = set.iterator(); it.hasNext();) {
					Object object = it.next();
					convertPut(object.getClass().getSimpleName(), object, result);
				}
				return result;
			}
		},
		Map(SimpleFieldSet.class){
			@Override
			Map sfcValue(SimpleFieldSet sfs) throws IllegalClassFormatException{
				Map map = new HashMap();
				for (Iterator<String> it = sfs.keyIterator(); it.hasNext();) {
					String key = it.next();
					map.put(key.split("_")[1], convert(key, sfs));
				}
				return map;
			}
			SimpleFieldSet toSimpleFieldSet(Map<String, ?> map){
				SimpleFieldSet result = new SimpleFieldSet(true);
				for (Iterator<String> it = map.keySet().iterator(); it.hasNext();) {
					String key = it.next();
					Object object = map.get(key);
					convertPut(object.getClass().getSimpleName()+"_"+key, object, result);
				}
				return result;
			}
		};

		static Object convert(String key, SimpleFieldSet location) throws IllegalClassFormatException{
			ParameterTypes type = ParameterTypes.valueOf(key.split("_")[0]);
			Object value;
			try {
				value = (SimpleFieldSet.class.isAssignableFrom(type.rclass)) ? type.sfcValue(location.getSubset(key)) : type.stringValue(location.get(key));
			} catch (FSParseException ex) {
				throw new IllegalClassFormatException("Could not parse SimpleFieldSet from FCP message to convert into a collection");
			}
			return value;
		}

		private static void convertPut(String key, Object val, SimpleFieldSet returnSet) {
			if(java.util.Set.class.isInstance(val)){
				returnSet.put(key, ParameterTypes.Set.toSimpleFieldSet(val));
			}if(java.util.Map.class.isInstance(val)){
				returnSet.put(key, ParameterTypes.Map.toSimpleFieldSet(val));
			}else
				returnSet.putOverwrite(key, val.toString());
		}

		static SimpleFieldSet toSimpleFieldSet(Object mapOrSet){
			return null;
		}

		ParameterTypes(Class rclass){
			this.rclass = rclass;
		}
		/**
		 * To convert a string into this parameterType
		 * @param string
		 * @return instance of this parametertype
		 */
		Object stringValue(String string){
			return null;
		}
		/**
		 * To convert a SimpleFieldSet into this type
		 * @param sfs
		 * @return instance of this type
		 */
		Object sfcValue(SimpleFieldSet sfs) throws IllegalClassFormatException{
			return null;
		}
		Class rclass;
	};




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
