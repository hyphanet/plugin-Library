/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import java.util.Map;

/**
** A class that represents a serialisation process which is divided up into
** several parts. The basic premise is to convert the target object into a
** different type of object, which can be handled by an already-existing
** {@link Serialiser} (which may be another {@code CompositeSerialiser}). This
** should help to reduce implementation effort.
**
** Implementations may handle the type conversion itself, or it may specify a
** {@link Translator} to do this for it.
**
** @author infinity0
** @see Translator
*/
abstract public class CompositeSerialiser<T, I, S extends Serialiser<I>> implements Serialiser<T> {

	/**
	** The option {@link Translator} to handle type conversion.
	*/
	final protected Translator<T, I> trans;

	/**
	** The {@link Serialiser} for the next stage of the process.
	*/
	final protected S subsrl;

	public CompositeSerialiser(S s, Translator<T, I> t) {
		subsrl = s;
		trans = t;
	}

	public CompositeSerialiser(S s) {
		this(s, null);
	}

}
