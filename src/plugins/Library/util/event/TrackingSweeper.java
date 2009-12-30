/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.event;

import java.util.IdentityHashMap;
import java.util.Iterator;

/**
** A {@link Sweeper} which uses {@link IdentityHashMap} to keep track of the
** objects added. It also allows iteration through these objects, and supports
** calls to {@link Iterator#remove()}.
**
** @author infinity0
*/
public class TrackingSweeper<T> extends AbstractSweeper<T> implements Iterable<T> {

	protected IdentityHashMap<T, Boolean> objmap;

	/**
	** Construct a new sweeper.
	**
	** @param autostart Whether to construct the sweeper already open.
	** @param expectedMaxSize The expected maximum number of objmap held at
	**        any one time.
	*/
	public TrackingSweeper(boolean autostart, int expectedMaxSize) {
		super(autostart);
		objmap = new IdentityHashMap<T, Boolean>(expectedMaxSize);
	}

	/**
	** Construct a new sweeper.
	**
	** @param autostart Whether to construct the sweeper already open.
	*/
	public TrackingSweeper(boolean autostart) {
		super(autostart);
		objmap = new IdentityHashMap<T, Boolean>();
	}

	/**
	** {@inheritDoc}
	*/
	@Override protected boolean add(T object) {
		return objmap.put(object, Boolean.TRUE) == null;
	}

	/**
	** {@inheritDoc}
	*/
	@Override protected boolean remove(T object) {
		return objmap.remove(object) == null;
	}

	/**
	** {@inheritDoc}
	*/
	/*@Override**/ public int size() {
		return objmap.size();
	}

	/*@Override**/ public Iterator<T> iterator() {
		return new Iterator<T>() {
			final Iterator<T> it = objmap.keySet().iterator();
			/*@Override**/ public boolean hasNext() { return it.hasNext(); }
			/*@Override**/ public T next() { return it.next(); }
			/*@Override**/ public void remove() { releaseFrom(it); }
		};
	}

}
