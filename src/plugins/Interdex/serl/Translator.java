/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.serl.Serialiser;
import plugins.Interdex.util.SkeletonMap;

/**
** A class that translates an object into one of another type. Used mostly in
** conjunction with an {@link Serialiser} that can take objects of the latter
** type but not the former type.
**
** @author infinity0
*/
public interface Translator<T, I> {

	/**
	** Reverse the translation.
	**
	** Implementations of this method which act on a recursive data structure
	** that is designed to be partially loaded (such as {@link SkeletonMap}),
	** should follow the same guidelines as in {@link Archiver#pull(Serialiser.PullTask)}.
	*/
	T rev(I intermediate);

	/**
	** Apply the translation.
	**
	** Implementations of this method which act on a recursive data structure
	** that is designed to be partially loaded (such as {@link SkeletonMap}),
	** should follow the same guidelines as in {@link Archiver#push(Serialiser.PushTask)},
	** particularly with regards to throwing {@link IllegalArgumentException}.
	*/
	I app(T translatee);

}
