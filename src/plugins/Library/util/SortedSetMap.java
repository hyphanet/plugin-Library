/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version) {. See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.AbstractSet;
import java.util.AbstractMap;

/**
** A {@link SortedMap} view of a {@link SortedSet}, with each key mapped to
** itself. The {@link #put(Object, Object)} method only accepts both arguments
** if they are the same object.
**
** Note: this class assumes the set's comparator is consistent with {@link
** Object#equals(Object)}. FIXME LOW
**
** @author infinity0
*/
public class SortedSetMap<E, S extends SortedSet<E>> extends AbstractMap<E, E>
implements Map<E, E>, SortedMap<E, E>/*, NavigableMap<E, E>, Cloneable, Serializable*/ {

	/**
	** {@link SortedSet} backing this {@link SortedMap}.
	*/
	final protected S bkset;

	/**
	** Construct a map-view of the given set.
	*/
	public SortedSetMap(S s) {
		bkset = s;
	}

	/*========================================================================
	  public interface Map
	 ========================================================================*/

	@Override public int size() {
		return bkset.size();
	}

	@Override public boolean isEmpty() {
		return bkset.isEmpty();
	}

	@Override public boolean containsKey(Object o) {
		return bkset.contains(o);
	}

	@Override public boolean containsValue(Object o) {
		return bkset.contains(o);
	}

	@Override public void clear() {
		bkset.clear();
	}

	@Override public E put(E k, E v) {
		if (k != v) {
			throw new IllegalArgumentException("SortedMapSet: cannot accept a non-self mapping");
		}
		return bkset.add(k)? null: v;
	}

	@Override public E get(Object o) {
		return bkset.contains(o)? (E)o: null;
	}

	@Override public E remove(Object o) {
		return bkset.remove(o)? (E)o: null;
	}

	private transient Set<Map.Entry<E, E>> entries;
	@Override public Set<Map.Entry<E, E>> entrySet() {
		if (entries == null) {
			entries = new AbstractSet<Map.Entry<E, E>>() {

				@Override public int size() { return bkset.size(); }

				@Override public Iterator<Map.Entry<E, E>> iterator() {
					return new Iterator<Map.Entry<E, E>>() {
						final Iterator<E> it = bkset.iterator();
						/*@Override**/ public boolean hasNext() { return it.hasNext(); }
						/*@Override**/ public Map.Entry<E, E> next() {
							E e = it.next();
							return Maps.$$(e, e);
						}
						/*@Override**/ public void remove() { it.remove(); }
					};
				}

				@Override public void clear() {
					bkset.clear();
				}

				@Override public boolean contains(Object o) {
					if (!(o instanceof Map.Entry)) { return false; }
					Map.Entry en = (Map.Entry)o;
					if (en.getKey() != en.getValue()) { return false; }
					return bkset.contains(en.getKey());
				}

				@Override public boolean remove(Object o) {
					boolean c = contains(o);
					if (c) { bkset.remove(((Map.Entry)o).getKey()); }
					return c;
				}

			};
		}
		return entries;
	}

	@Override public SortedSet<E> keySet() {
		// FIXME LOW Map.keySet() should be remove-only
		return bkset;
	}

	@Override public Collection<E> values() {
		// FIXME LOW Map.values() should be remove-only
		return bkset;
	}

	/*========================================================================
	  public interface SortedMap
	 ========================================================================*/

	/*@Override**/ public Comparator<? super E> comparator() {
		return bkset.comparator();
	}

	/*@Override**/ public E firstKey() {
		return bkset.first();
	}

	/*@Override**/ public E lastKey() {
		return bkset.last();
	}

	/*@Override**/ public SortedMap<E, E> headMap(E to) {
		return new SortedSetMap<E, SortedSet<E>>(bkset.headSet(to));
	}

	/*@Override**/ public SortedMap<E, E> tailMap(E fr) {
		return new SortedSetMap<E, SortedSet<E>>(bkset.tailSet(fr));
	}

	/*@Override**/ public SortedMap<E, E> subMap(E fr, E to) {
		return new SortedSetMap<E, SortedSet<E>>(bkset.subSet(fr, to));
	}


}
