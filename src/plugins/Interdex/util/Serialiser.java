/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.Archiver.*;

import java.util.Collection;
import java.util.Map;

/**
** A class that handles serialisation tasks more complex than {@link Archiver}.
**
** By default, nested data structures such as {@link PrefixTreeMap} use the
** meta objects of their children directly during a serialisation operation.
** Therefore, it is recommended that the meta objects returned by this class
** (and related classes) be directly serialisable by an {@link Archiver}. This
** will make the task of writing a {@link Translator} much easier, possibly
** even unnecessary.
**
** The recommended way to ensure this is for it to be either: a primitive or
** Object form of a primitive; an Array, {@link Collection}, or {@link Map},
** where the elements are also serialisable as defined here; or a Java Bean.
**
** @author infinity0
*/
public interface Serialiser<T> extends Archiver<T> {

	/**
	** Execute everything in a group of {@link PullTask}s, returning only when
	** they are all done.
	*/
	public void pull(Iterable<PullTask<T>> tasks);

	/**
	** Execute everything in a group of {@link PushTask}s, returning only when
	** they are all done.
	*/
	public void push(Iterable<PushTask<T>> tasks);

	/**
	** Execute everything in a map of {@link PullTask}s, returning only when
	** they are all done.
	*/
	public <K> void pull(Map<K, PullTask<T>> tasks);

	/**
	** Execute everything in a map of {@link PushTask}s, returning only when
	** they are all done.
	*/
	public <K> void push(Map<K, PushTask<T>> tasks);

}
