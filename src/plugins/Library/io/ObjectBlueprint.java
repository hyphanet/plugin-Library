/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.io;

import java.util.Iterator;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
** An (immutable) blueprint for an object. This class provides methods to
** construct new objects from a list or map of property parameters, and to
** present (currently immutable) map-views of existing objects.
**
** @author infinity0
*/
public class ObjectBlueprint<T> {

	// TODO LOW maybe make the map-view mutable
	// TODO LOW move this to another package, maybe util.reflect

	/**
	** The class that this blueprint represents.
	*/
	final protected Class<T> cls;

	/**
	** Map of properties to their method names. The keys are the property
	** names; the values are the names of the nullary methods to invoke to
	** retrieve the property-values. If any map-value is null, then the field
	** with the same name as its map-key will be used instead.
	*/
	final protected Map<String, String> properties;

	/**
	** Map of properties to their corresponding fields. This is automatically
	** generated from the {@link #properties} map.
	*/
	final protected Map<String, Field> prop_fields = new HashMap<String, Field>();

	/**
	** Map of properties to their corresponding getter methods. This is
	** automatically generated from the {@link #properties} map.
	*/
	final protected Map<String, Method> prop_methods = new HashMap<String, Method>();

	/**
	** Map of properties to their corresponding types. This is automatically
	** generated from the {@link #properties} map.
	*/
	final protected Map<String, Class<?>> param_type = new LinkedHashMap<String, Class<?>>();

	/**
	** A constructor for the class, whose parameter types are the values of
	** {@link #param_type} in the same iteration order. This is automatically
	** from the class object, and must exist (otherwise construction of the
	** blueprint will throw {@link NoSuchMethodException}).
	*/
	final protected Constructor<T> constructor;

	/**
	** Constructs a blueprint from the given {@link #properties} map. If you
	** don't have the map ready at hand, and it's a mixture of methods and
	** fields, consider using {@link #init(Class)} instead.
	**
	** The constructor's parameter types are given by {@code ctor_params}. This
	** '''must be a subset''' of {@code blueprint}; otherwise the blueprint
	** constructor will not be able to determine the types of those parameters.
	** For example, if your class has two fields {@code int field_a} and
	** {@code float field_b}, and {@code blueprint} maps <code>{"field_b":null,
	** "field_a":null}</code>, and {@code ctor_params} lists <code>["field_b",
	** "field_a"]</code>, then this method will search for the constructor with
	** parameters {@code (float, int)}. If it doesn't exist, {@link
	** NoSuchMethodException} will be thrown.
	**
	** If {@code ctor_params} is {@code null}, the parameter types will instead
	** be inferred from the keys of the properties map; the end result will be
	** the same as if it was equal to {@code blueprint.keySet()}.
	**
	** @param c The class to represent
	** @param blueprint The map of properties
	** @param ctor_params Properties corresponding to the input parametrs of
	**        the object constructor.
	** @throws NoSuchFieldException if Java reflection can't find an inferred
	**         field
	** @throws NoSuchMethodException if Java reflection can't find a given
	**         method or the inferred constructor
	** @throws NullPointerException if {@code c} or {@code blueprint} is {@code
	**         null}
	*/
	public ObjectBlueprint(Class<T> c, Map<String, String> blueprint, Iterable<String> ctor_params)
	throws NoSuchFieldException, NoSuchMethodException {
		if (c == null || blueprint == null) { throw new NullPointerException(); }
		cls = c;
		properties = blueprint;
		setProperties();
		if (ctor_params == null) {
			constructor = cls.getConstructor(param_type.values().toArray(new Class<?>[param_type.size()]));
		} else {
			List<Class<?>> types = new ArrayList<Class<?>>();
			for (String property: ctor_params) { types.add(param_type.get(property)); }
			constructor = cls.getConstructor(types.toArray(new Class<?>[types.size()]));
		}
	}

	/**
	** Constructs a blueprint from the given {@link #properties} map. If you
	** don't have the map ready at hand, and it's a mixture of methods and
	** fields, consider using {@link #init(Class)} instead.
	**
	** This constructor just delegates to {@link #ObjectBlueprint(Class, Map,
	** Iterable) ObjectBlueprint(c, blueprint, null)}.
	**
	** @see #ObjectBlueprint(Class, Map, Iterable)
	*/
	public ObjectBlueprint(Class<T> c, Map<String, String> blueprint)
	throws NoSuchFieldException, NoSuchMethodException {
		this(c, blueprint, (Iterable<String>)null);
	}

	/**
	** Constructs a blueprint from the given collection of field names. This
	** constructor just creates the appropriate blueprint map to delegate to
	** {@link #ObjectBlueprint(Class, Map)}.
	**
	** @see #ObjectBlueprint(Class, Map)
	*/
	public ObjectBlueprint(Class<T> c, Collection<String> fields)
	throws NoSuchFieldException, NoSuchMethodException {
		this(c, makePropertiesFromFields(fields));
	}

	/**
	** Helper method for {@link #ObjectBlueprint(Class, Collection)}.
	*/
	private static Map<String, String> makePropertiesFromFields(Collection<String> fields) {
		Map<String, String> blueprint = new LinkedHashMap<String, String>();
		for (String s: fields) {
			blueprint.put(s, null);
		}
		return blueprint;
	}

	/**
	** Constructs a blueprint from the given properties map and the given
	** object constructor. If you don't have the map ready at hand, and it's a
	** mixture of methods and fields, consider using {@link #init(Class)}
	** instead.
	**
	** If {@code ctor} is {@code null}, then any calls to the blueprint's
	** {@link #objectFromMap(Map)} and {@link #newInstance(Object[])}
	** will throw {@link NullPointerException}.
	**
	** @param c The class to represent
	** @param blueprint The map of properties
	** @param ctor The object constructor
	** @throws NoSuchFieldException if Java reflection can't find an inferred
	**         field
	** @throws NoSuchMethodException if Java reflection can't find a given
	**         method or the inferred constructor
	** @throws NullPointerException if {@code c} or {@code blueprint} is {@code
	**         null}
	*/
	public ObjectBlueprint(Class<T> c, Map<String, String> blueprint, Constructor<T> ctor)
	throws NoSuchFieldException, NoSuchMethodException {
		if (c == null || blueprint == null) { throw new NullPointerException(); }
		cls = c;
		properties = blueprint;
		setProperties();
		constructor = ctor;
	}

	/**
	** Helper method for the constructors. Sets {@link #prop_fields},
	** {@link #prop_methods} and {@link #param_type} from {@link #properties}.
	*/
	private void setProperties() throws NoSuchFieldException, NoSuchMethodException {
		assert(properties != null);
		prop_fields.clear();
		prop_methods.clear();
		param_type.clear();

		for (Map.Entry<String, String> en: properties.entrySet()) {
			String property = en.getKey();
			String method_name = en.getValue();

			if (method_name == null || method_name.length() == 0) {
				Field f = cls.getField(property);
				prop_fields.put(property, f);
				param_type.put(property, f.getType());

			} else {
				Method m = cls.getMethod(method_name);
				if (m.getParameterTypes().length > 0) {
					throw new IllegalArgumentException("Bad blueprint: getter method " +method_name+ " for property " +property+ " takes more than 0 arguments");
				}
				prop_methods.put(property, m);
				param_type.put(property, m.getReturnType());
			}
		}
	}

	/**
	** Returns a builder for an {@link ObjectBlueprint} for the given class.
	** This should make construction of blueprints neater in source code, eg:
	**
	**   ObjectBlueprint<MyClass> bprint =
	**     ObjectBlueprint.init(MyClass.class)
	**       .addFields("name", "origin", "date")
	**       .addMethod("height", "getHeight")
	**       .addMethod("width", "getWidth")
	**       .build();
	**
	**
	** @throws NullPointerException if {@code cls} is {@code null}
	** @see <a href="http://en.wikipedia.org/wiki/Builder_pattern">Builder
	**      Pattern</a>
	*/
	public static <T> Builder<T> init(Class<T> cls) {
		return new Builder<T>(cls);
	}

	public Class<T> getObjectClass() {
		return cls;
	}

	public Constructor<T> getObjectConstructor() {
		return constructor;
	}

	/**
	** Constructs a new object by invoking the inferred constructor with the
	** given list of arguments.
	**
	** @throws NullPointerException if no constructor was supplied to this
	**         class' own constructor.
	** @see Constructor#newInstance(Object[])
	*/
	public T newInstance(Object... initargs) throws InstantiationException, IllegalAccessException, InvocationTargetException {
		return constructor.newInstance(initargs);
	}

	/**
	** Map of primitive classes to their object classes.
	*/
	final private static Map<Class<?>, Class<?>> boxes = new IdentityHashMap<Class<?>, Class<?>>();
	static {
		boxes.put(boolean.class, Boolean.class);
		boxes.put(byte.class, Byte.class);
		boxes.put(char.class, Character.class);
		boxes.put(short.class, Short.class);
		boxes.put(int.class, Integer.class);
		boxes.put(long.class, Long.class);
		boxes.put(float.class, Float.class);
		boxes.put(double.class, Double.class);
	}

	/**
	** Casts a value to the object type for the given primitive type.
	**
	** @param cls The primitive class
	** @param val The value to cast
	** @throws ClassCastException if the cast cannot be made
	** @throws IllegalArgumentException if the input class is not a primitive
	*/
	protected static <T> Object boxCast(Class<T> cls, Object val) throws ClassCastException {
		Class<?> tcls = boxes.get(cls);
		if (tcls == null) {
			throw new IllegalArgumentException("Input class must be a primitive type.");
		}
		return tcls.cast(val);
	}

	/**
	** Constructs a new object from the given map of properties and their
	** desired values.
	**
	** @param map Map of properties to their desired values.
	** @throws NullPointerException if no constructor has been set
	** @see Constructor#newInstance(Object[])
	*/
	public T objectFromMap(Map<?, ?> map) throws InstantiationException, IllegalAccessException, InvocationTargetException {
		Object[] initargs = new Object[param_type.size()];
		int i=0;
		for (Map.Entry<String, Class<?>> en: param_type.entrySet()) {
			String property = en.getKey();
			Class<?> type = en.getValue();
			Object value = map.get(property);
			if (value != null && value.equals("null")) { // FIXME: case when type Map and value String "null"
				value = null;
			}
			try {
				if (type.isPrimitive()) {
					value = boxCast(type, value);
				} else {
					value = type.cast(value);
				}
			} catch (ClassCastException e) {
				throw new IllegalArgumentException("Parameter for property " +property+ " is not of the correct type should be "+type+" but is "+value.getClass(), e);
			}
			initargs[i++] = value;
		}
		return constructor.newInstance(initargs);
	}

	/**
	** Returns a map-view of an object. The keys are its properties as defined
	** by the blueprint, and the values are the values of those properties.
	** The map is backed by the object, so changes to the object (if any) are
	** reflected in the map. The map itself is immutable, however (for now;
	** this might be changed later).
	**
	** NOTE: the below implementation is technically not optimal since {@link
	** AbstractMap}'s remove methods (and that of its entry/key/value views)
	** actually iterate over the map looking for the key. However, this
	** shouldn't be an issue since the map doesn't support remove (calling it
	** will only throw {@link UnsupportedOperationException} when the key is
	** found), and most objects only have a small (<20) number of properties.
	** If you find that you need such functionality, feel free to implement a
	** more optimal solution.
	**
	** @param object The object to view as a map.
	*/
	public Map<String, Object> objectAsMap(final T object) {

		return new AbstractMap<String, Object>() {

			final private Set<Map.Entry<String, Object>> entrySet = new AbstractSet<Map.Entry<String, Object>>() {

				@Override public int size() {
					return properties.size();
				}

				@Override public Iterator<Map.Entry<String, Object>> iterator() {
					return new Iterator<Map.Entry<String, Object>>() {
						final Iterator<Map.Entry<String, String>> props = properties.entrySet().iterator();

						/*@Override**/ public boolean hasNext() {
							return props.hasNext();
						}

						/*@Override**/ public Map.Entry<String, Object> next() {
							Map.Entry<String, String> en = props.next();
							final String property = en.getKey();
							final String method_name = en.getValue();
							return new Map.Entry<String, Object>() {
								/*@Override**/ public String getKey() { return property; }
								/*@Override**/ public Object getValue() { return get(property, method_name); }
								/*@Override**/ public Object setValue(Object o) { throw new UnsupportedOperationException("Cannot modify an object in this way."); }
							};
						}

						/*@Override**/ public void remove() {
							throw new UnsupportedOperationException("Cannot modify an object in this way.");
						}

					};
				}

			};

			@Override public int size() {
				return properties.size();
			}

			@Override public boolean containsKey(Object property) {
				return properties.containsKey(property);
			}

			@Override public Object get(Object property) {
				return get((String)property, properties.get(property));
			}

			protected Object get(String property, String method_name) {
				try {
					if (method_name == null || method_name.length() == 0) {
						return prop_fields.get(property).get(object);
					} else {
						return prop_methods.get(method_name).invoke(object);
					}
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				} catch (InvocationTargetException e) {
					throw new IllegalStateException(e);
				}
			}

			@Override public Set<Map.Entry<String, Object>> entrySet() {
				return entrySet;
			}

		};
	}

	/**
	** Builder for a {@link ObjectBlueprint}. This class is protected; use
	** {@link ObjectBlueprint#init(Class)} to create one of these.
	*/
	protected static class Builder<T> {

		final public Class<T> cls;

		protected Iterable<String> ctor_params;

		protected Constructor<T> constructor;

		protected boolean use_ctor;

		/**
		** Properties map. This uses {@link LinkedHashMap}, which iterates through
		** the properties in the same order in which they were added to the builder.
		*/
		final protected Map<String, String> props = new LinkedHashMap<String, String>();

		/**
		** Construct a builder for an {@link ObjectBlueprint} for the given class.
		**
		** @throws NullPointerException if {@code c} is {@code null}
		*/
		public Builder(Class<T> c) {
			if (c == null) { throw new NullPointerException(); }
			cls = c;
		}

		/**
		** @return {@code this}
		*/
		public Builder<T> addMethod(String property, String method_name) {
			props.put(property, method_name);
			return this;
		}

		/**
		** @return {@code this}
		*/
		public Builder<T> addField(String field) {
			props.put(field, null);
			return this;
		}

		/**
		** @return {@code this}
		*/
		public Builder<T> addMethods(Map<String, String> properties) {
			props.putAll(properties);
			return this;
		}

		/**
		** @return {@code this}
		*/
		public Builder<T> addFields(Collection<String> fields) {
			for (String field: fields) {
				props.put(field, null);
			}
			return this;
		}

		/**
		** @return {@code this}
		*/
		public Builder<T> addFields(String... fields) {
			for (String field: fields) {
				props.put(field, null);
			}
			return this;
		}

		/**
		** Sets an object constructor, and tell {@link #build()} to use {@link
		** ObjectBlueprint#ObjectBlueprint(Class, Map, Constructor)}.
		**
		** @return {@code this}
		*/
		public Builder<T> setConstructor(Constructor<T> ctor) {
			use_ctor = true;
			constructor = ctor;
			return this;
		}

		/**
		** Sets the constructor parameters, and tell {@link #build()} to use {@link
		** ObjectBlueprint#ObjectBlueprint(Class, Map, Iterable)}. (This is the
		** default).
		**
		** @return {@code this}
		*/
		public Builder<T> setCtorParams(Iterable<String> params) {
			use_ctor = false;
			ctor_params = params;
			return this;
		}

		/**
		** Build a blueprint from the properties given to the builder so far.
		**
		** @return The built blueprint
		** @see ObjectBlueprint#ObjectBlueprint(Class, Map)
		*/
		public ObjectBlueprint<T> build() throws NoSuchFieldException, NoSuchMethodException {
			if (use_ctor) {
				return new ObjectBlueprint<T>(cls, props, constructor);
			} else {
				return new ObjectBlueprint<T>(cls, props, ctor_params);
			}
		}

	}

}
