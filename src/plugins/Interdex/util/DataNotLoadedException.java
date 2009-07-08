/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

/**
** Thrown when data hasn't been loaded yet, eg. when a {@link Skeleton} hasn't
** been fully loaded into memory. Exceptions should be constructed such that
** a method call to {@link Skeleton#deflate()} on {@link #getParent()} with
** {@link #getKey()} as its argument will load the data and prevent an
** exception from being thrown the next time round the data is accessed.
**
** Ideally, this would be {@code DataNotLoadedException<K>}, where {@code K} is
** the type parameter for {@link Skeleton}, but java does not currently allow
** parametrised classes to extend {@link Throwable}.
**
** @author infinity0
** @see Skeleton
*/
public class DataNotLoadedException extends RuntimeException {

	/**
	** The parent container of the not-yet-loaded data.
	*/
	final Skeleton parent;

	/**
	** The key, if any, that the not-yet-loaded data was associated with.
	*/
	final Object key;

	/**
	** The metadata for the not-yet-loaded data.
	*/
	final Object meta;

	public DataNotLoadedException(String s, Throwable t, Skeleton p, Object k, Object v) {
		super(s, t);
		parent = p;
		key = k;
		meta = v;
	}

	public DataNotLoadedException(Throwable t, Skeleton p, Object k, Object v) {
		this(null, t, p, k, v);
	}

	public DataNotLoadedException(String s, Skeleton p, Object k, Object v) {
		this(s, null, p, k, v);
	}

	public DataNotLoadedException(String s, Skeleton p, Object k) {
		this(s, null, p, k, null);
	}

	public DataNotLoadedException(String s, Skeleton p) {
		this(s, null, p, null, null);
	}

	public Skeleton getParent() { return parent; }
	public Object getKey() { return key; }
	public Object getValue() { return meta; }

}
