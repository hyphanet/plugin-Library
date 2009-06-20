/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

/**
** Defines an interface for a map or map-like data structure (eg. multimap)
** which represents the skeleton of another map or map-like data structure,
** with some of the data missing. Operations on the missing data throw
** DataNotLoadedException.
**
** @author infinity0
*/
public interface SkeletonMap<K, V> {

	/**
	** Whether the skeleton is fully loaded and has no data missing. In other
	** words, for all keys k: get(k) will return the correct value.
	*/
	public boolean isFull();

	/**
	** Whether the skeleton is bare and has no data loaded at all. In other
	** words, for all keys k: get(k) will throw DataNotLoadedException.
	*/
	public boolean isBare();

	/**
	** If isFull is true, return a map of the same type as the data
	** structure being emulated. Otherwise, throw DataNotLoadedException.
	*/
	public Object complete() throws DataNotLoadedException;

	/**
	** Inflate the entire skeleton so that after the method call, isFull()
	** returns true.
	*/
	public void inflate();

	/**
	** If isFull() returns true before the method call, deflate the entire
	** skeleton.
	*/
	public void deflate();

	/**
	** Inflate the part of the skeleton which corresponds to the given submap.
	**
	** @param map The submap to inflate.
	*/
	public void inflate(SkeletonMap<K, V> map);

	/**
	** Deflate the part of the skeleton which corresponds to the given submap.
	**
	** @param map The submap to deflate.
	*/
	public void deflate(SkeletonMap<K, V> map);

	/**
	** Inflate the value for a key so that after the method call, get(key)
	** won't throw a DataNotLoadedException. This method may or may not also
	** inflate other parts of the skeleton.
	**
	** @param key The key for whose value to inflate.
	*/
	public void inflate(K key);

	/**
	** Deflate the value for a key, so that after the method call, get(key)
	** will throw a DataNotLoadedException. This method may or may not also
	** deflate other parts of the skeleton.
	**
	** @param key The key for whose value to deflate.
	*/
	public void deflate(K key);

}
