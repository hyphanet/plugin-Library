/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.AbstractSet;
import java.util.ArrayList;

/**
** Methods on sorted collections
**
** Most of the stuff here is completely fucking pointless and only because we
** need to support Java 5. Otherwise, we can just use standard Navigable*
** methods. :|
**
** @author infinity0
*/
final public class Sorted {

	public enum Inclusivity { NONE, LEFT, RIGHT, BOTH }

	private Sorted() { }

	private static class SortedKeySet<K> extends AbstractSet<K> implements SortedSet<K> {

		final SortedMap<K, ?> map;

		public SortedKeySet(SortedMap<K, ?> m) {
			map = m;
		}

		@Override public int size() { return map.size(); }

		@Override public Iterator<K> iterator() {
			return map.keySet().iterator();
		}

		@Override public void clear() { map.clear(); }

		@Override public boolean contains(Object o) {
			return map.containsKey(o);
		}

		@Override public boolean remove(Object o) {
			boolean c = contains(o);
			map.remove(o);
			return c;
		}

		/*@Override**/ public Comparator<? super K> comparator() { return map.comparator(); }
		/*@Override**/ public K first() { return map.firstKey(); }
		/*@Override**/ public K last() { return map.lastKey(); }
		/*@Override**/ public SortedSet<K> headSet(K r) { return new SortedKeySet<K>(map.headMap(r)); }
		/*@Override**/ public SortedSet<K> tailSet(K l) { return new SortedKeySet<K>(map.tailMap(l)); }
		/*@Override**/ public SortedSet<K> subSet(K l, K r) { return new SortedKeySet<K>(map.subMap(l, r)); }

	}

	/**
	** Returns the second element in an iterable, or {@code null} if there is
	** no such element. The iterable is assumed to have at least one element.
	*/
	public static <E> E second(Iterable<E> ib) {
		Iterator<E> it = ib.iterator();
		it.next();
		return (it.hasNext())? it.next(): null;
	}

	/**
	** Returns the least element (in the given set) >= the given element, or
	** {@code null} if there is no such element.
	**
	** This is identical to Java 6's {@code NavigableSet.ceiling(E)}, but works
	** for {@link SortedSet}.
	*/
	public static <E> E ceiling(SortedSet<E> set, E el) {
		SortedSet<E> tail = set.tailSet(el);
		return (tail.isEmpty())? null: tail.first();
	}

	/**
	** Returns the greatest element (in the given set) <= the given element, or
	** {@code null} if there is no such element.
	**
	** This is identical to Java 6's {@code NavigableSet.floor(E)}, but works
	** for {@link SortedSet}.
	*/
	public static <E> E floor(SortedSet<E> set, E el) {
		SortedSet<E> head;
		return (set.contains(el))? el: ((head = set.headSet(el)).isEmpty())? null: head.last();
	}

	/**
	** Returns the least element (in the given set) > the given element, or
	** {@code null} if there is no such element.
	**
	** This is identical to Java 6's {@code NavigableSet.higher(E)}, but works
	** for {@link SortedSet}.
	*/
	public static <E> E higher(SortedSet<E> set, E el) {
		SortedSet<E> tail = set.tailSet(el);
		return (set.contains(el))? second(tail): (tail.isEmpty())? null: tail.first();
	}

	/**
	** Returns the greatest element (in the given set) < the given element, or
	** {@code null} if there is no such element.
	**
	** This is identical to Java 6's {@code NavigableSet.lower(E)}, but works
	** for {@link SortedSet}.
	*/
	public static <E> E lower(SortedSet<E> set, E el) {
		SortedSet<E> head = set.headSet(el);
		return (head.isEmpty())? null: head.last();
	}

	/**
	** Returns a {@link SortedSet} view of the given {@link SortedMap}'s keys.
	** You would think that the designers of the latter would have overridden
	** the {@link SortedMap#keySet()} method to do this, but noooo...
	**
	** JDK6 remove this pointless piece of shit.
	*/
	public static <K> SortedSet<K> keySet(SortedMap<K, ?> map) {
		Set<K> ks = map.keySet();
		return (ks instanceof SortedMap)? (SortedSet<K>)ks: new SortedKeySet<K>(map);
	}

	/**
	** Splits the given sorted set at the given separators.
	**
	** FIXME LOW currently this method assumes that the comparator of the set
	** is consistent with equals(). we should use the SortedSet's comparator()
	** instead, or "natural" ordering.
	**
	** JDK6 use a NavigableSet instead of a SortedSet.
	**
	** @param subj Subject of the split
	** @param sep Separators to split at
	** @param foundsep An empty set which will be filled with the separators
	**        that were also contained in the subject set.
	** @return A list of subsets; each subset contains all entries between two
	**         adjacent separators, or an edge separator and the corresponding
	**         edge of the set. The list is in sorted order.
	** @throws NullPointerException if any of the inputs are {@code null}
	*/
	public static <E> List<SortedSet<E>> split(SortedSet<E> subj, SortedSet<E> sep, SortedSet<E> foundsep) {
		if (!foundsep.isEmpty()) {
			throw new IllegalArgumentException("split(): Must provide an empty set to add found separators to");
		}

		if (subj.isEmpty()) {
			return Collections.emptyList();
		} else if (sep.isEmpty()) {
			return Collections.singletonList(subj);
		}

		List<SortedSet<E>> res = new ArrayList<SortedSet<E>>(sep.size()+2);
		E csub = subj.first(), csep, nsub, nsep;

		do {
			csep = floor(sep, csub); // JDK6 sep.floor(csub);
			assert(csub != null);
			if (csub.equals(csep)) {
				assert(subj.contains(csep));
				foundsep.add(csep);
				csub = second(subj.tailSet(csep)); // JDK6 subj.higher(csep);
				continue;
			}

			nsep = (csep == null)? sep.first(): second(sep.tailSet(csep)); // JDK6 sep.higher(csep);
			assert(nsep == null || ((Comparable<E>)csub).compareTo(nsep) < 0);

			nsub = (nsep == null)? subj.last(): floor(subj, nsep); // JDK6 subj.floor(nsep);
			assert(nsub != null);
			if (nsub.equals(nsep)) {
				foundsep.add(nsep);
				nsub = lower(subj, nsep); // JDK6 subj.lower(nsep);
			}

			assert(csub != null && ((Comparable<E>)csub).compareTo(nsub) <= 0);
			assert(csep != null || nsep != null); // we already took care of sep.size() == 0
			res.add(
				(csep == null)? subj.headSet(nsep):
				(nsep == null)? subj.tailSet(csub):
								subj.subSet(csub, nsep)
			);
			if (nsep == null) { break; }

			csub = second(subj.tailSet(nsub)); // JDK6 subj.higher(nsub);
		} while (csub != null);

		return res;
	}

	/**
	** Select {@code n} separator elements from the subject sorted set. The
	** elements are distributed evenly amongst the set.
	**
	** @param subj The set to select elements from
	** @param num Number of elements to select
	** @param inc Which sides of the set to select. For example, if this is
	**        {@code BOTH}, then the first and last elements will be selected.
	** @return The selected elements
	*/
	public static <E> List<E> select(SortedSet<E> subj, int num, Inclusivity inc) {
		if (num >= 2) {
			// test most common first
		} else if (num == 1) {
			switch (inc) {
			case LEFT: return Collections.singletonList(subj.first());
			case RIGHT: return Collections.singletonList(subj.last());
			case BOTH: throw new IllegalArgumentException("select(): can't have num=1 and inc=BOTH");
			// case NONE falls through to main section
			}
		} else if (num == 0) {
			if (inc == Inclusivity.NONE) {
				return Collections.emptyList();
			} else {
				throw new IllegalArgumentException("select(): can't have num=0 and inc!=NONE");
			}
		} else { // num < 0
			throw new IllegalArgumentException("select(): cannot select a negative number of items");
		}

		if (subj.size() < num) {
			throw new IllegalArgumentException("select(): cannot select " + num + " elements from a set of size " + subj.size());
		} else if (subj.size() == num) {
			return new ArrayList<E>(subj);
		}

		List<E> sel = new ArrayList<E>(num);
		int n = num;
		Iterator<E> it = subj.iterator();

		switch (inc) {
		case NONE: ++n; break;
		case BOTH: --n;
		case LEFT: sel.add(it.next()); break;
		}

		for (Integer s: Integers.allocateEvenly(subj.size() - num, n)) {
			for (int i=0; i<s; ++i) { it.next(); }
			if (it.hasNext()) { sel.add(it.next()); }
			else { assert(sel.size() == num && (inc == Inclusivity.NONE || inc == Inclusivity.LEFT)); }
		}
		assert(sel.size() == num);
		return sel;
	}

	/**
	** Select {@code n} separator elements from the subject sorted set. The
	** inclusivity is given as {@code NONE}.
	**
	** @see #select(SortedSet, int, Inclusivity)
	*/
	public static <E> List<E> select(SortedSet<E> subj, int n) {
		return select(subj, n, Inclusivity.NONE);
	}

}
