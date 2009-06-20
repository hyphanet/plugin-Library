/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

/**
** A class that handles serialisation tasks.
**
** TODO think about having Dummy<T> in places instead of object. Then we can
** have class DummyX<? extends X> implements Dummy<T> etc
**
** @author infinity0
*/
public interface Serialiser<T> {

	/**
	** Creates a new inflate task for an object.
	*/
	public InflateTask<T> newInflateTask(Object o);

	/**
	** Creates a new deflate task for an object.
	*/
	public DeflateTask<T> newDeflateTask(T o);

	/**
	** Inflate a skeleton data structure from a dummy data structure. Note that
	** only a skeleton is inflated, so isBare() should return true for the
	** object returned by this method.
	**
	** Implementations of this should provide equivalent behaviour as creating
	** a new InflateTask, calling put(dummy), start(), join(), then returning
	** an object with all data retrieved from the task.
	**
	** @param dummy The dummy data structure that represents the map.
	** @return The skeleton data structure that represents the map.
	*/
	public T inflate(Object dummy);

	/**
	** Deflate a skeleton data structure into a dummy data structure. Note that
	** only a skeleton is deflated, so isBare() should return true for the
	** object passed into this method.
	**
	** Implementations of this should provide equivalent behaviour as creating
	** a new DeflateTask, adding all data to it, calling start(), join(), then
	** returning get().
	**
	** @param skel The skeleton data structure that represents the map.
	** @return The dummy data structure that represents the map.
	*/
	public Object deflate(T skel);


	/**
	** Defines a serialisation task. The methods are named such that
	** implementations of this interface may choose to extend Thread, if they
	** wish.
	*/
	public interface SerialiseTask {

		/**
		** Start the task.
		*/
		public void start();

		/**
		** Wait for the task to finish.
		*/
		public void join();

		/**
		** Set a task option. This method should only be called before start().
		**
		** TODO make this less vague..
		*/
		public void setOption(Object o);

	}

	/**
	** Defines a inflate task, which allows data to be retrieved from the task
	** after it has completed.
	*/
	public interface InflateTask<T> extends SerialiseTask {

		/**
		** Give a dummy data structure to the task for inflation. This method
		** should only be called before start().
		*/
		public void put(Object dummy);

		/**
		** Take the components of the skeleton data structure from the task after
		** it finishes. This method should only be called after join().
		*/
		public Object get(String key);
	}

	/**
	** Defines a deflate task, which allows data to be added to the task before
	** it starts.
	*/
	public interface DeflateTask<T> extends SerialiseTask {

		/**
		** Give the components of the skeleton data structure to the task for
		** deflation. This method should only be called before start().
		*/
		public void put(String key, Object o);

		/**
		** Take the dummy data structure from the task after it finishes. This
		** method should only be called after join().
		*/
		public Object get();

	}

}
