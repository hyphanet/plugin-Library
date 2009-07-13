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
** By default, nested data structures such as {@link PrefixTreeMap} will use
** the metadata of their children directly (as in, without using a {@link
** Translator} during a serialisation operation. Therefore, it is recommended
** that all metadata passing through this class (and related classes) be
** directly serialisable by an {@link Archiver}. This will make the task of
** writing {@link Translator}s much easier, possibly even unnecessary.
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
	**
	** For any single {@code PullTask}, the metadata should uniquely determine
	** what data will be generated during a pull operation, independent of any
	** information from other {@code PullTasks}, even if they are in the same
	** group of tasks given to a compound {@link Serialiser} such as {@link
	** MapSerialiser}. (The converse is not required for {@link PushTask}s.)
	**
	** Consequently, {@code Serialiser}s should be implemented so that its
	** push operations generate metadata for which this property holds.
	*/
	final public static class PullTask<T> extends Task<T> {

		public PullTask(Object m) {
			meta = m;
		}

	}

	/************************************************************************
	** Defines a push task: given some data and optional metadata, the task is
	** to archive this data and generate the metadata for it in the process.
	**
	** For any single {@code PushTask}, the data may or may not uniquely
	** determine the metadata that will be generated for it during the push
	** operation. For example, compound {@link Serialiser}s that work on groups
	** of tasks may use information from the whole group in order to optimise
	** the operation. (The converse is required for {@link PullTask}s.)
	**
	** Consequently, {@code Serialiser}s may require that extra data is passed
	** to its push operations such that it can...
	*/
	final public static class PushTask<T> extends Task<T> {

		public PushTask(T d) {
			data = d;
		}

		public PushTask(T d, Object m) {
			data = d;
			meta = m;
		}

	}

	/**
	** TODO find a better place to put this...
	**
	** DOCUMENT
	*/
	public interface Trackable<T> extends Serialiser<T> {

		public ProgressTracker<T, ?> getTracker();

	}

	/**
	** TODO rename this to something better...
	*/
	public interface Translate<T, I> extends Serialiser<T> {

		public Translator<T, I> getTranslator();

	}

	/**
	** TODO find a tidy way to make this extend Serialiser<T>...
	**
	** DOCUMENT, copy from CompositeSerialiser
	*/
	public interface Composite<S extends Serialiser> /*extends Serialiser*/ {

		public S getChildSerialiser();

	}

}
