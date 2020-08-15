/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.io;

/**
** Thrown when data is not in a recognised format.
**
** @author infinity0
*/
public class DataFormatException extends java.io.IOException {

	/**
	** The parent, if any, of the unrecognised data.
	*/
	final Object parent;

	/**
	** The key, if any, that the data is associated with.
	*/
	final Object key;

	/**
	** The unrecognised data.
	*/
	final Object data;

	public DataFormatException(String s, Throwable t, Object v, Object p, Object k) {
		super(s);
		initCause(t);
		data = v;
		parent = p;
		key = k;
	}

	public DataFormatException(String s, Throwable t, Object v) {
		this(s, t, v, null, null);
	}

	public Object getParent() { return parent; }
	public Object getKey() { return key; }
	public Object getValue() { return data; }

}
