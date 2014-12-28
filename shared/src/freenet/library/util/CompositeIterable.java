/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.util;

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
	** Whether the iterator supports {@link Iterator#remove()}.
	*/
	final public boolean immutable;

	/**
	** Create a new mutable iterable backed by the given iterable. Note that
	** mutability will only have an effect if the backing iterator is also
	** mutable.
	*/
	public CompositeIterable(Iterable<S> i) {
		ib = i;
		immutable = false;
	}

	/**
	** Create a new iterable backed by the given iterable, with the given
	** mutability setting. Note that mutability will only have an effect if the
	** backing iterator is also mutable.
	*/
	public CompositeIterable(Iterable<S> i, boolean immute) {
		ib = i;
		immutable = immute;
	}

	/*@Override**/ public Iterator<T> iterator() {
		return immutable?
		new Iterator<T>() {
			final Iterator<S> it = ib.iterator();
			/*@Override**/ public boolean hasNext() { return it.hasNext(); }
			/*@Override**/ public T next() { return CompositeIterable.this.nextFor(it.next()); }
			/*@Override**/ public void remove() { throw new UnsupportedOperationException("Immutable iterator"); }
		}:
		new Iterator<T>() {
			final Iterator<S> it = ib.iterator();
			/*@Override**/ public boolean hasNext() { return it.hasNext(); }
			/*@Override**/ public T next() { return CompositeIterable.this.nextFor(it.next()); }
			/*@Override**/ public void remove() { it.remove(); }
		};
	}

	/**
	** Returns an object of the target type given an object of the source type.
	*/
	abstract protected T nextFor(S elem);

}
