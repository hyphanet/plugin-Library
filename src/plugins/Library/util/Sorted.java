/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import plugins.Library.util.func.Tuples.*;
import static plugins.Library.util.func.Tuples.*;

import java.util.Collections;
import java.util.Collection;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.ArrayList;

/**
** Methods on sorted collections
**
** @author infinity0
*/
final public class Sorted {

	public enum Inclusivity { NONE, LEFT, RIGHT, BOTH }

	private Sorted() { }

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
	** Splits the given sorted set at the given separators.
	**
	** TODO currently this method assumes that the comparator of the set is
	** consistent with equals(). Use the SortedSet's comparator() instead (or
	** "natural" ordering.
	**
	** JDK6 use a NavigableSet instead of a SortedSet.
	**
	** @param subj The {@link SortedSet} to split
	** @param sep The separators, arranged in a {@link SortedSet}
	** @param inc Which sides of the returned ranges include the separators
	** @param foundsep This will be filled with the separators that were also
	**        contained in the subject set, and should be passed in empty
	** @return An ordered collection of 2-tuples representing the ranges of the
	**         resulting split sets.
	** @throws NullPointerException if any of the inputs are {@code null}
	*/
	public static <E> Collection<$2<E, E>> split(SortedSet<E> subj, SortedSet<E> sep, SortedSet<E> foundsep, Inclusivity inc) {
		if (!foundsep.isEmpty()) {
			throw new IllegalArgumentException("split(): Must provide an empty set to add found separators to");
		}

		if (subj.isEmpty()) {
			return Collections.emptySet();
		} else if (sep.isEmpty()) {
			return Collections.singleton($2(subj.first(), subj.last()));
		}

		Collection<$2<E, E>> res = new ArrayList<$2<E, E>>(sep.size()+2);
		E csub = subj.first(), csep, nsub, nsep;

		do {
			csep = floor(sep, csub); // JDK6 sep.floor(csub);
			assert(csub != null);
			if (csub.equals(csep)) {
				assert(subj.contains(csep));
				foundsep.add(csep);
				csub = second(subj.tailSet(csep)); // JDK6 subj.higher(csep);
			}

			nsep = (csep == null)? sep.first(): second(sep.tailSet(csep)); // JDK6 sep.higher(csep);

			nsub = (nsep == null)? subj.last(): floor(subj, nsep); // JDK6 subj.floor(nsep);
			assert(nsub != null);
			if (nsub.equals(nsep)) {
				foundsep.add(nsep);
				nsub = lower(subj, nsep); // JDK6 subj.lower(nsep);
			}

			if (csub != null && (nsep == null || !nsep.equals(csub))) {
			//if (csub != null && ((Comparable<E>)csub).compareTo(nsub) <= 0) {
				assert(csub != null && ((Comparable<E>)csub).compareTo(nsub) <= 0);
				switch (inc) {
				case NONE:
					res.add($2(csub, nsub)); break;
				case LEFT:
					res.add($2(csep, nsub)); break;
				case RIGHT:
					res.add($2(csub, nsep)); break;
				case BOTH:
					res.add($2(csep, nsep)); break;
				}
			}
			if (nsep == null) { break; }

			csub = second(subj.tailSet(nsub)); // JDK6 subj.higher(nsub);
		} while (csub != null);

		return res;
	}

	/**
	** Splits the given sorted set at the given separators. The inclusivity is
	** given as {@code RIGHT}. This means when {@link SortedSet#subSet(Object,
	** Object)} is called on each range returned, it will give the subset with
	** the separators excluded.
	**
	** @see #split(SortedSet, SortedSet, SortedSet, Inclusivity)
	*/
	public static <E> Collection<$2<E, E>> split(SortedSet<E> subj, SortedSet<E> sep, SortedSet<E> foundsep) {
		return split(subj, sep, foundsep, Inclusivity.RIGHT);
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
	public static <E> Collection<E> select(SortedSet<E> subj, int num, Inclusivity inc) {
		if (num >= 2) {
			// test most common first
		} else if (num == 1) {
			switch (inc) {
			case LEFT: return Collections.singleton(subj.first());
			case RIGHT: return Collections.singleton(subj.last());
			case BOTH: throw new IllegalArgumentException("select(): can't have num=1 and inc=BOTH");
			// case NONE falls through to main section
			}
		} else if (num == 0) {
			if (inc == Inclusivity.NONE) {
				return Collections.emptySet();
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

		Collection<E> sel = new ArrayList<E>(num);
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
		return sel;
	}

	/**
	** Select {@code n} separator elements from the subject sorted set. The
	** inclusivity is given as {@code NONE}.
	**
	** @see #select(SortedSet, int, Inclusivity)
	*/
	public static <E> Collection<E> select(SortedSet<E> subj, int n) {
		return select(subj, n, Inclusivity.NONE);
	}

}
