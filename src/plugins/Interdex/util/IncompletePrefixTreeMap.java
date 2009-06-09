/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import java.util.TreeMap;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

/**
** Emulates a PrefixTreeMap where some of the data has not been fully loaded.
** Operations on this data structure will throw a DataNotLoadedException when
** it encounters dummy objects such as DummyPrefixTreeMap. This data structure
** can be populated without the node population restrictions of PrefixTreeMap
** via the constructors and assimilate().
**
** This object can be safely casted to a PrefixTreeMap if isComplete() returns
** true, which occurs if and only if it contains no IncompleteTreeMap or
** DummyPrefixTreeMap objects, and it follows the node population restrictions
** of a PrefixTreeMap.
**
** @author infinity0
*/
public class IncompletePrefixTreeMap<K extends PrefixTreeMap.PrefixKey, V> extends PrefixTreeMap<K, V>
implements IncompleteMap<K, V> {

	/**
	** Represents a PrefixTreeMap which has not been loaded, but which a parent
	** IncompletePrefixTreeMap (that has been loaded) refers to.
	**
	** @author infinity0
	*/
	public static class DummyPrefixTreeMap<K extends PrefixTreeMap.PrefixKey, V> extends PrefixTreeMap<K, V>
	implements IncompleteMap<K, V> {

		public DummyPrefixTreeMap(K p, int len, int maxsz) {
			// TODO
			super(p, len, maxsz);
		}

		/************************************************************************
		 * public interface Map
		 ************************************************************************/

		public void clear() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public boolean containsKey(Object key) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public boolean containsValue(Object value) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public Set<Map.Entry<K,V>> entrySet() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public boolean equals(Object o) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public V get(Object key) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public int hashCode() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public boolean isEmpty() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public Set<K> keySet() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public V put(K key, V value) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public void putAll(Map<? extends K,? extends V> t) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public V remove(Object key) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public int size() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}
		public Collection<V> values() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}

		/************************************************************************
		 * public interface IncompleteMap
		 ************************************************************************/

		public boolean isComplete() {
			return false;
		}

		public Map<K, V> complete() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this, prefix);
		}

	}

	public IncompletePrefixTreeMap(K p, int len, int maxsz) {
		super(p, len, maxsz);
	}
	// TODO better constructors
	/*public IncompletePrefixTreeMap(K p, int maxsz) {
		this(p, 0, maxsz);
	}
	public IncompletePrefixTreeMap(K p) {
		this(p, 0, p.symbols());
	}*/

	public boolean assimilate(IncompletePrefixTreeMap<K, V> sub) {
		// TODO
		return false;
	}

	/************************************************************************
	 * public interface IncompleteMap
	 ************************************************************************/

	public boolean isComplete() {
		// TODO
		return false;
	}

	public Map<K, V> complete() {
		if (!isComplete()) {
			throw new DataNotLoadedException("PrefixTreeMap not fully loaded for " + prefix.toString(), this, prefix);
		} else {
			//TODO
			return null;
		}
	}

}
