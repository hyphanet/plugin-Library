/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.serl.Serialiser.*;
import plugins.Interdex.serl.Serialiser;

import com.google.common.collect.Multimap;

import java.util.Map;

/**
** Defines an interface for a {@link Map} or map-like data structure (eg.
** {@link Multimap}) which represents the skeleton of another map or map-like
** data structure, with some of the data missing. Operations on the missing
** data throw {@link DataNotLoadedException}.
**
** @author infinity0
** @see Serialiser
*/
public interface SkeletonMap<K, V> {

	/**
	** Whether the skeleton is fully loaded and has no data missing. In other
	** words, for all keys k: {@link Map#get(Object) get(k)} must return the
	** correct value.
	*/
	public boolean isLive();

	/**
	** Whether the skeleton is bare and has no data loaded at all. In other
	** words, for all keys k: {@link Map#get(Object) get(k)} must throw {@link
	** DataNotLoadedException}.
	*/
	public boolean isBare();

	/**
	** Get the meta data associated with this skeleton.
	*/
	public Object getMeta();

	/**
	** Set the meta data associated with this skeleton.
	*/
	public void setMeta(Object m);

	/**
	** If {@link #isLive()} is true, return an instance of the data structure
	** being emulated that is an effective clone of this structue. Otherwise,
	** throw {@link DataNotLoadedException}.
	**
	** TODO maybe this method is totally unnecessary...
	*/
	public Object complete() throws DataNotLoadedException;

	/**
	** Inflate the entire skeleton so that after the method call, {@link
	** #isLive()} returns true.
	*/
	public void inflate();

	/**
	** Deflate the entire skeleton so that after the method call, {@link
	** #isBare()} returns true.
	*/
	public void deflate();

	/*/*
	** Inflate the part of the skeleton which corresponds to the given submap.
	**
	** @param map The submap to inflate.
	*/
	//public void inflate(SkeletonMap<K, V> map);

	/*/*
	** Deflate the part of the skeleton which corresponds to the given submap.
	**
	** @param map The submap to deflate.
	*/
	//public void deflate(SkeletonMap<K, V> map);

	/**
	** Inflate the value for a key so that after the method call, {@link
	** Map#get(Object) get(key)} will not throw {@link DataNotLoadedException}.
	** This method may or may not also inflate other parts of the skeleton.
	**
	** @param key The key for whose value to inflate.
	*/
	public void inflate(K key);

	/**
	** Deflate the value for a key, so that after the method call, {@link
	** Map#get(Object) get(k)} will throw a {@link DataNotLoadedException}.
	** This method may or may not also deflate other parts of the skeleton.
	**
	** @param key The key for whose value to deflate.
	*/
	public void deflate(K key);

}
