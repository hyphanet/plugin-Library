/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import plugins.Library.serial.Serialiser.*;
import plugins.Library.serial.Serialiser;
import plugins.Library.serial.TaskAbortException;

import java.util.Map;

/**
** A {@link Skeleton} of a {@link Map}.
**
** @author infinity0
*/
public interface SkeletonMap<K, V> extends Map<K, V>, Skeleton<K> {

	/**
	** {@inheritDoc}
	**
	** In other words, for all keys k: {@link Map#get(Object) get(k)} must
	** return the appropriate value.
	*/
	public boolean isLive();

	/**
	** {@inheritDoc}
	**
	** In other words, for all keys k: {@link Map#get(Object) get(k)} must
	** throw {@link DataNotLoadedException}.
	*/
	public boolean isBare();

	/**
	** {@inheritDoc}
	**
	** For a {@code SkeletonMap}, this inflates the value for a key so that
	** after the method call, {@link Map#get(Object) get(key)} will not throw
	** {@link DataNotLoadedException}.
	**
	** @param key The key for whose value to inflate.
	*/
	public void inflate(K key) throws TaskAbortException;

	/**
	** {@inheritDoc}
	**
	** For a {@code SkeletonMap}, this deflates the value for a key so that
	** after the method call, {@link Map#get(Object) get(k)} will throw a
	** {@link DataNotLoadedException}.
	**
	** @param key The key for whose value to deflate.
	*/
	public void deflate(K key) throws TaskAbortException;

}
