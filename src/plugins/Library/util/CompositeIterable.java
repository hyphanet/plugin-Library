/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import java.util.Iterator;

/**
** An {@link Iterable} backed by another {@link Iterable}.
**
** @param <S> Type of source
** @param <T> Type of target
** @author infinity0
*/
abstract public class CompositeIterable<S, T> implements Iterable<T> {

	/**
	** The backing {@link Iterable}.
	*/
	final protected Iterable<S> ib;

	/**
	** Create a new iterable backed by the given iterable.
	*/
	public CompositeIterable(Iterable<S> i) {
		ib = i;
	}

	@Override public Iterator<T> iterator() {
		return new Iterator<T>() {
			final Iterator<S> it = ib.iterator();
			@Override public boolean hasNext() { return it.hasNext(); }
			@Override public T next() { return CompositeIterable.this.nextFor(it.next()); }
			@Override public void remove() { it.remove(); }
		};
	}

	/**
	** Returns an object of the target type given an object of the source type.
	*/
	abstract protected T nextFor(S elem);

}
