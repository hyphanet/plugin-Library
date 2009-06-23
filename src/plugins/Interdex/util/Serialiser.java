/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import java.util.Collection;
import java.util.Map;

/**
** A class that handles serialisation tasks.
**
** @author infinity0
*/
public interface Serialiser<T> {

	/**
	** Creates a {@link PullTask} from a dummy.
	*/
	public PullTask<T> makePullTask(Dummy<T> o);

	/**
	** Creates a {@link PushTask} from some data structure.
	*/
	public PushTask<T> makePushTask(T data);

	/**
	** Execute a {@link PullTask}, returning only when the task is done.
	*/
	public void doPull(PullTask<T> task);

	/**
	** Execute a {@link PushTask}, returning only when the task is done.
	*/
	public void doPush(PushTask<T> task);

	/**
	** Execute everything in a group of {@link PullTask}s, returning only when
	** they are all done.
	*/
	public void doPull(Iterable<PullTask<T>> tasks);

	/**
	** Execute everything in a group of {@link PushTask}s, returning only when
	** they are all done.
	*/
	public void doPush(Iterable<PushTask<T>> tasks);


	/**
	** Represents an "empty" object that corresponds to a "full" object. For
	** example, might be a URI pointer to where the rest of the data is held.
	**
	** For now, this is only an empty marker interface.
	**
	** Dummy values must themselves be directly serialisable by the parent
	** {@link Serialiser}, as defined in {@link Task}.
	*/
	public interface Dummy<T> {

		static Dummy NULL = new Dummy() {};

	}

	/**
	** Defines a serialisation task for an object, mapping its fields (in
	** {@link String} form) to the their values (in a form that can be
	** serialised by the parent {@link Serialiser}).
	**
	** The recommended way to ensure that a value can be serialised by any
	** Serialiser is for it to be either: a primitive or Object form of a
	** primitive; an Array, {@link Collection}, or {@link Map}, whether the
	** elements are also serialisable as defined here; or a Java Bean.
	**
	** Implementations must ensure that data insertion/retrieval methods are
	** never called if {@link begun()}/{@link done()} returns true/false,
	** respectively. A recommended way to do this is to override such methods
	** to throw {@link IllegalStateException} under the appropriate conditions.
	*/
	public interface Task<T> extends Map<String, Object> {

		/**
		** Whether the task has been started or not. If this is false, then
		** {@link done()} must return false.
		*/
		public boolean begun();

		/**
		** Whether the task is finished or not. If this is true, then {@link
		** begun()} must return true.
		*/
		public boolean done();

	}

	/**
	** Defines a pull task, which allows data to be retrieved from the task
	** after it has completed.
	**
	** Implementations' constructors should create a task ready to be executed
	** without any further additions necessary to the task.
	*/
	public interface PullTask<T> extends Task<T> {

		/**
		** Retrieve the data after the task is done.
		**
		** @throws IllegalStateException If the task is not done.
		*/
		public T get() throws IllegalStateException;

		/**
		** Retrieve the dummy after the task is done. This will usually be the
		** same dummy that was passed to the constructor, but may be different,
		** for example if the serialiser followed a URI redirect.
		**
		** @throws IllegalStateException If the task is not done.
		*/
		public void getDummy(Dummy<T> d) throws IllegalStateException;

	}

	/**
	** Defines a push task, which allows data to be added to the task before
	** it starts.
	**
	** Implementations' constructors should create a task ready to be executed
	** without any further additions necessary to the task (except possibly
	** {@link putDummy(Dummy<T>)}).
	*/
	public interface PushTask<T> extends Task<T> {

		/**
		** Retrieve the dummy after the task is done. If the task is not done,
		** throw IllegalStateException.
		**
		** @throws IllegalStateException If the task is not done.
		*/
		public Dummy<T> get() throws IllegalStateException;

		/**
		** Give a dummy to the serialiser as a hint. The serialiser may or may
		** not choose to ignore the dummy passed here; the actual value used
		** is always returned correctly by {@link get()}.
		**
		** @throws IllegalStateException If the task has already begun.
		*/
		public void putDummy(Dummy<T> d) throws IllegalStateException;

	}

}
