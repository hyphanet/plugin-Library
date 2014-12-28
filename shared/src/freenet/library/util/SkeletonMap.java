/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.util;


import java.util.Map;

import freenet.library.io.serial.MapSerialiser;
import freenet.library.util.exec.TaskAbortException;

/**
** A {@link Skeleton} of a {@link Map}.
**
** @author infinity0
*/
public interface SkeletonMap<K, V> extends Map<K, V>, Skeleton<K, MapSerialiser<K, V>> {

	/**
	** {@inheritDoc}
	**
	** In other words, for all keys k: {@link Map#get(Object) get(k)} must
	** return the appropriate value.
	*/
	/*@Override**/ public boolean isLive();

	/**
	** {@inheritDoc}
	**
	** In other words, for all keys k: {@link Map#get(Object) get(k)} must
	** throw {@link DataNotLoadedException}.
	*/
	/*@Override**/ public boolean isBare();

	/*@Override**/ public MapSerialiser<K, V> getSerialiser();

	/**
	** {@inheritDoc}
	**
	** For a {@code SkeletonMap}, this inflates the value for a key so that
	** after the method call, {@link Map#get(Object) get(key)} will not throw
	** {@link DataNotLoadedException}.
	**
	** @param key The key for whose value to inflate.
	*/
	/*@Override**/ public void inflate(K key) throws TaskAbortException;

	/**
	** {@inheritDoc}
	**
	** For a {@code SkeletonMap}, this deflates the value for a key so that
	** after the method call, {@link Map#get(Object) get(k)} will throw a
	** {@link DataNotLoadedException}.
	**
	** @param key The key for whose value to deflate.
	*/
	/*@Override**/ public void deflate(K key) throws TaskAbortException;

}
