/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

/**
** Thrown when data hasn't been loaded yet, eg. when a data structure hasn't
** been fully populated from the network.
**
** DOCUMENT
**
** @author infinity0
** @see SkeletonMap
*/
public class DataNotLoadedException extends RuntimeException {

	final Object parent;
	final Object key;
	final Object meta;

	public DataNotLoadedException(String s, Throwable t, Object p, Object k, Object v) {
		super(s, t);
		parent = p;
		key = k;
		meta = v;
	}

	public DataNotLoadedException(Throwable t, Object p, Object k, Object v) {
		this(null, t, p, k, v);
	}

	public DataNotLoadedException(String s, Object p, Object k, Object v) {
		this(s, null, p, k, v);
	}

	public DataNotLoadedException(String s, Object p, Object k) {
		this(s, null, p, k, null);
	}

	public DataNotLoadedException(String s, Object p) {
		this(s, null, p, null, null);
	}

	public Object getParent() { return parent; }
	public Object getKey() { return key; }
	public Object getValue() { return meta; }

}
