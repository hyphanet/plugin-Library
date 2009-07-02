/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.util.PrefixTreeMap;

import java.util.Collection;
import java.util.Map;

/**
** An empty marker interface for serialisation classes. It defines some nested
** subclasses that acts as a unified interface between these classes.
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
public interface Serialiser<T> {

	/************************************************************************
	** Defines a serialisation task for an object. Contains two fields - data
	** and metadata.
	*/
	abstract public static class Task<T> {

		/**
		** Field for the metadata. This should be serialisable as defined in the
		** description for {@link Serialiser}.
		*/
		public Object meta = null;

		/**
		** Field for the data.
		*/
		public T data = null;

	}

	/************************************************************************
	** Defines a pull task: given some metadata, the task is to retrieve the
	** data for this metadata, possibly updating the metadata in the process.
	*/
	public static class PullTask<T> extends Task<T> {

		public PullTask(Object m) {
			meta = m;
		}

	}

	/************************************************************************
	** Defines a push task: given some data and optional metadata, the task is
	** to submit this data and update the metadata for it in the process.
	*/
	public static class PushTask<T> extends Task<T> {

		public PushTask(T d) {
			data = d;
		}

		public PushTask(T d, Object m) {
			data = d;
			meta = m;
		}

	}

}
