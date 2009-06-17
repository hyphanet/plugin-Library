/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import java.util.Map;

/**
** Defines an interface representing an incompleted data structure which
** emulates another data structure, with some of the data missing. Operations
** on the missing data throw DataNotLoadedException.
**
** @author infinity0
*/
public interface IncompleteMap<K, V> extends Map<K, V> {

	/**
	** Whethere the data structure is complete and has no data missing.
	*/
	public boolean isComplete();

	/**
	** If isComplete is true, return a map of the same type as the data
	** structure being emulated. Otherwise, throw DataNotLoadedException.
	*/
	public Map<K, V> complete() throws DataNotLoadedException;

	/**
	** Inflate the entire map. TODO expand docs
	*/
	public Object inflate();

	/**
	** Deflate the entire map.
	*/
	public Object deflate();

	/**
	** Inflate a submap.
	*/
	public Object inflate(IncompleteMap<K, V> m);

	/**
	** Deflate a submap.
	*/
	public Object deflate(IncompleteMap<K, V> m);

	/**
	** Inflate the value for a key.
	*/
	public Object inflate(K key);

	/**
	** Deflate the value for a key.
	*/
	public Object deflate(K key);

	/**
	** Get the serialiser for the map.
	*/
	public Serialiser getSerialiser();

	/**
	** Set the serialiser for the map.
	*/
	public void setSerialiser(Serialiser s);

}
