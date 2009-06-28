/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import java.util.Map;

/**
** A simple implementation of {@link CompositeSerialiser} on a single task.
**
** @author infinity0
** @see Translator
*/
abstract public class CompositeArchiver<T, I> extends CompositeSerialiser<T, I, Archiver<I>> implements Archiver<T> {

	public CompositeArchiver(Archiver<I> s, Translator<T, I> t) {
		super(s, t);
	}

	public CompositeArchiver(Archiver<I> s) {
		super(s, null);
	}

	/*========================================================================
	  public interface Archiver
	 ========================================================================*/

	/**
	** {@inheritDoc}
	**
	** This implementation uses {@link CompositeSerialiser#subsrl} to pull the
	** data, and then uses {@link CompositeSerialiser#trans} to translate it to
	** the desired type.
	*/
	@Override public void pull(PullTask<T> task) {
		PullTask<I> serialisable = new PullTask<I>(task.meta);
		subsrl.pull(serialisable);
		T pulldata = trans.rev(serialisable.data);

		task.meta = serialisable.meta; task.data = pulldata;
	}

	/**
	** {@inheritDoc}
	**
	** This implementation uses {@link CompositeSerialiser#trans} to translate
	** the data to the desired type, and then uses {@link
	** CompositeSerialiser#subsrl} to push it.
	*/
	@Override public void push(PushTask<T> task) {
		I intermediate = trans.app(task.data);
		PushTask<I> serialisable = new PushTask<I>(intermediate, task.meta);
		subsrl.push(serialisable);

		task.meta = serialisable.meta;
	}

}
