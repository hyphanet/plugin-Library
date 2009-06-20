/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

/**
** Thrown when data hasn't been loaded yet, eg. when a data structure hasn't
** been fully populated from the network.
**
** TODO expand docs
**
** @author infinity0
** @see SkeletonMap
*/
public class DataNotLoadedException extends RuntimeException {

	final Object thrower;
	final Object key;
	final Object dummyvalue;

	public DataNotLoadedException(String s, Throwable t, Object c, Object k, Object v) {
		super(s, t);
		thrower = c;
		key = k;
		dummyvalue = v;
	}

	public DataNotLoadedException(Throwable t, Object c, Object k, Object v) {
		this(null, t, c, k, v);
	}

	public DataNotLoadedException(String s, Object c, Object k, Object v) {
		this(s, null, c, k, v);
	}

	public DataNotLoadedException(String s, Object c, Object k) {
		this(s, null, c, k, null);
	}

	public DataNotLoadedException(String s, Object c) {
		this(s, null, c, null, null);
	}

	public Object getThrower() { return thrower; }
	public Object getKey() { return key; }
	public Object getDummyValue() { return dummyvalue; }

}
