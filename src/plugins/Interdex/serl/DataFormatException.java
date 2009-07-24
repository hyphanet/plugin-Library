/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

/**
** Thrown when data is not in a recognised format.
**
** @author infinity0
** @see Serialiser
*/
public class DataFormatException extends RuntimeException {

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
		super(s, t);
		data = v;
		parent = p;
		key = k;
	}

	public DataFormatException(Throwable t, Object v, Object p, Object k) {
		this(t==null? null: t.toString(), t, v, p, k);
	}

	public DataFormatException(String s, Object v, Object p, Object k) {
		this(s, null, v, p, k);
	}

	public DataFormatException(String s, Object v, Object p) {
		this(s, null, v, p, null);
	}

	public DataFormatException(String s, Object v) {
		this(s, null, v, null, null);
	}

	public Object getParent() { return parent; }
	public Object getKey() { return key; }
	public Object getValue() { return data; }

}
