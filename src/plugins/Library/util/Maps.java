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

	private Maps() { }

	public static <K, V> Map.Entry<K, V> $(final K k, final V v) {
		return new Map.Entry<K, V>() {
			final K key = k; V value = v;
			public K getKey() { return key; }
			public V getValue() { return value; }
			public V setValue(V n) { V o = value; value = n; return o; }
		};
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
