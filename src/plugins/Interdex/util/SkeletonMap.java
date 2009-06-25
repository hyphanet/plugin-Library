/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.Archiver.*;

/**
** Defines an interface for a map or map-like data structure (eg. multimap)
** which represents the skeleton of another map or map-like data structure,
** with some of the data missing. Operations on the missing data throw
** {@link DataNotLoadedException}.
**
** @author infinity0
*/
public interface SkeletonMap<K, V> {

	/**
	** Whether the skeleton is fully loaded and has no data missing. In other
	** words, for all keys k: {@link get(k)} must return the correct value.
	*/
	public boolean isLive();

	/**
	** Whether the skeleton is bare and has no data loaded at all. In other
	** words, for all keys k: {@link get(k)} must throw {@link
	** DataNotLoadedException.}
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
	** If isLive is true, return a map of the same type as the data structure
	** being emulated. Otherwise, throw {@link DataNotLoadedException.}
	*/
	public Object complete() throws DataNotLoadedException;

	/**
	** Inflate the entire skeleton so that after the method call, {@link
	** isLive()} returns true.
	*/
	public void inflate();

	/**
	** Deflate the entire skeleton so that after the method call, {@link
	** isBare()} returns true.
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
	** Inflate the value for a key so that after the method call, get(key) will
	** not throw a {@link DataNotLoadedException}. This method may or may not
	** also inflate other parts of the skeleton.
	**
	** @param key The key for whose value to inflate.
	*/
	public void inflate(K key);

	/**
	** Deflate the value for a key, so that after the method call, get(key)
	** will throw a {@link DataNotLoadedException}. This method may or may not
	** also deflate other parts of the skeleton.
	**
	** @param key The key for whose value to deflate.
	*/
	public void deflate(K key);


	/**
	** An effectively empty marker interface which contains some additional
	** specifications for {@link Serialiser} of {@link SkeletonMap}.
	*/
	public interface SkeletonSerialiser<T extends SkeletonMap> extends Serialiser<T> {

		/**
		** {@inheritDoc}
		**
		** Note that only a skeleton is pulled, so {@link isBare()} should return
		** true for the object returned by {@link Serialiser.PullTask#get()} after
		** this task completes.
		*/
		public void pull(PullTask<T> task);

		/**
		** {@inheritDoc}
		**
		** Note that only a skeleton is pushed, so {@link isBare()} should return
		** true for the object passed into this method.
		**
		** If it is not true, it is recommended that implementations throw {@link
		** IllegalArgumentException} rather than automatically calling {@link
		** SkeletonMap#deflate()} on the object, to maintain symmetry with the
		** {@link SkeletonMap.SkeletonSerialiser#doPull(Serialiser.PullTask)}
		** method (which does not automatically call {@link SkeletonMap#inflate()}
		** on the resulting object), and to provide finer-grained control over the
		** pushing process.
		*/
		public void push(PushTask<T> task);

	}

}
