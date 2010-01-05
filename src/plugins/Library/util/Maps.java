/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import static plugins.Library.util.Maps.$;

import java.util.Map;

/**
** Methods for maps.
**
** @author infinity0
*/
final public class Maps {

	/**
	** A simple {@link Map.Entry} with no special properties.
	*/
	public static class BaseEntry<K, V> implements Map.Entry<K, V> {

		final public K key;
		protected V val;

		public BaseEntry(K k, V v) { key = k; val = v; }

		public K getKey() { return key; }
		public V getValue() { return val; }
		public V setValue(V n) { V o = val; val = n; return o; }

	}

	/**
	** A {@link Map.Entry} whose value cannot be modified.
	*/
	public static class ImmutableEntry<K, V> extends BaseEntry<K, V> {

		public ImmutableEntry(K k, V v) { super(k, v); }

		/**
		** @throws UnsupportedOperationException always
		*/
		@Override public V setValue(V n) {
			throw new UnsupportedOperationException("ImmutableEntry: cannot modify value after creation");
		}

	}

	/**
	** A {@link Map.Entry} whose {@link Object#equals(Object)} and {@link
	** Object#hashCode()} are defined purely in terms of the key, which is
	** immutable in the entry.
	*/
	public static class KeyEntry<K, V> extends BaseEntry<K, V> {

		public KeyEntry(K k, V v) { super(k, v); }

		/**
		** Whether the object is also a {@link KeyEntry} and has the same
		** {@code #key} as this entry.
		*/
		@Override public boolean equals(Object o) {
			if (!(o instanceof KeyEntry)) { return false; }
			return key.equals(((KeyEntry)o).key);
		}

		@Override public int hashCode() {
			return 31 * key.hashCode();
		}

	}

	private Maps() { }

	/**
	** Returns a new {@link BaseEntry} with the given key and value.
	*/
	public static <K, V> Map.Entry<K, V> $(final K k, final V v) {
		return new BaseEntry<K, V>(k, v);
	}

	/**
	** Returns a new {@link ImmutableEntry} with the given key and value.
	*/
	public static <K, V> Map.Entry<K, V> $$(final K k, final V v) {
		return new ImmutableEntry<K, V>(k, v);
	}

	/**
	** Returns a new {@link KeyEntry} with the given key and value.
	*/
	public static <K, V> Map.Entry<K, V> $K(K k, V v) {
		return new KeyEntry<K, V>(k, v);
	}

	public static <K, V> Map<K, V> of(Class<? extends Map> mapcl, Map.Entry<K, V>... items) {
		try {
			Map<K, V> map = mapcl.newInstance();
			for (Map.Entry<K, V> en: items) {
				map.put(en.getKey(), en.getValue());
			}
			return map;
		} catch (InstantiationException e) {
			throw new IllegalArgumentException("Could not instantiate map class", e);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Could not access map class", e);
		}
	}

	/*final private static Map<String, String> testmap = of(SkeletonTreeMap.class,
		$("test1", "test1"),
		$("test2", "test2"),
		$("test3", "test3")
	);*/

}
