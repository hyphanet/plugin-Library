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

	/*
	public void inflate(Map<K, V> m);
	public void deflate(Map<K, V> m);

	public void inflate(Map.Entry<K, V> e);
	public void deflate(Map.Entry<K, V> e);

	public IncompleteMap.Serialiser getSerialiser();
	public IncompleteMap.Serialiser setSerialiser();
	*/


}
