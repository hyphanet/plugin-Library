/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.fcp;

import freenet.node.FSParseException;
import freenet.support.SimpleFieldSet;
import java.lang.instrument.IllegalClassFormatException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The Parameter types allowed over FCP. Currently covers Boolean, Integer, String, Set & Map <br />
 * TODO Will need Exceptions, I'm sure this is far from a good way of doing this, I'll change it when I have a new idea
 */
public enum ParameterTypes{
	Boolean(Boolean.class){
		@Override
		Boolean stringValue(String string){
			return java.lang.Boolean.valueOf(string);
		}
	},
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
	/**
	 * Gets converted into a SimpleFieldSet, the keys of which are <class>_[<number>]
	 */
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
				convertPut(null, object, result);
			}
			return result;
		}
	},
	/**
	 * Gets converted into a SimpleFieldSet, the keys of which are <class>_[<key>]
	 */
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
		// Convert a Map of String, type pairs into a SimpleFieldSet
		SimpleFieldSet toSimpleFieldSet(Map<String, ?> map){
			SimpleFieldSet result = new SimpleFieldSet(true);
			for (Iterator<String> it = map.keySet().iterator(); it.hasNext();) {
				String key = it.next();
				Object object = map.get(key);
				convertPut(key, object, result);
			}
			return result;
		}
	};
	
	/**
	 * Convert the element in location at key into an object
	 * @param key is used to find the element and also to realise what it is - <class>[_<name>][_<number as keys have to be unique>]
	 * @param location SimpleFieldSet we are decoding
	 * @return object of class specified in key, can then be cast to the class
	 * @throws java.lang.instrument.IllegalClassFormatException if key incorrectly specifies the class
	 */
	public static Object convert(String key, SimpleFieldSet location) throws IllegalClassFormatException{
		ParameterTypes type = ParameterTypes.valueOf(key.split("_")[0]);
		Object value;
		try {
			if (SimpleFieldSet.class.isAssignableFrom(type.rclass))
				value = type.sfcValue(location.getSubset(key));
			else
				value = type.stringValue(location.get(key));
		} catch (FSParseException ex) {
			throw new IllegalClassFormatException("Could not parse SimpleFieldSet from FCP message to convert into a collection");
		}
		return value;
	}

	/**
	 * Convert the element in data which is either SFS or String into its real object
	 * @param typename the class of the data
	 * @param data either a String or a SimpleFieldSet
	 * @return decoded object
	 * @throws java.lang.instrument.IllegalClassFormatException if key incorrectly specifies the class
	 */
	public static Object convert(String typename, Object data) throws IllegalClassFormatException{
		ParameterTypes type = ParameterTypes.valueOf(typename);
		Object value;
		if (data.getClass().isAssignableFrom(type.rclass))
			value = type.sfcValue((SimpleFieldSet)data);
		else if (data.getClass().isAssignableFrom(type.rclass))
			value = type.stringValue((String)data);
		else
			throw new IllegalClassFormatException("Data is neither SFS or String");
		return value;
	}

	/**
	 * Take an object and put it into the SimpleFieldSet
	 * @param name The name of the element if it has one
	 * @param val the object
	 * @param returnSet the SimpleFieldSet to which this object should be added
	 */
	public static void convertPut(String name, Object val, SimpleFieldSet returnSet) {
		if(name==null)
			name = "";
		String key = val.getClass().getSimpleName() + "_" +name;
		if(java.util.Set.class.isInstance(val)){
			returnSet.put(key, ParameterTypes.Set.toSimpleFieldSet(val));
		}if(java.util.Map.class.isInstance(val)){
			returnSet.put(key, ParameterTypes.Map.toSimpleFieldSet(val));
		}else{
			int i = -1;
			while(true)
				try{
					returnSet.putSingle(key, val.toString()+((i<0)?"":"_"+i));
					break;
				}catch(IllegalStateException e){
					i++;
				}
		}
	}

	/**
	 * Convert a map of set to a simplefieldset, overridden
	 * @param mapOrSet
	 * @return
	 */
	static SimpleFieldSet toSimpleFieldSet(Object mapOrSet){
		return null;
	}

	ParameterTypes(Class rclass){
		this.rclass = rclass;
	}
	/**
	 * To convert a string into this parameterType, overriden by individuals
	 * @param string
	 * @return instance of this parametertype
	 */
	Object stringValue(String string){
		return null;
	}
	/**
	 * To convert a SimpleFieldSet into this type for Sets and Maps
	 * @param sfs
	 * @return instance of this type
	 */
	Object sfcValue(SimpleFieldSet sfs) throws IllegalClassFormatException{
		return null;
	}
	Class rclass;
};