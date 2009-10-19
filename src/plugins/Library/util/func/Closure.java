/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.func;

/**
** An object representing a partially-evaluated subroutine. This is useful in
** many situations, for example when we have a general subroutine that takes
** several parameters, and we want to create a derivative subroutine for which
** a subset of these parameters is fixed (this is sometimes referred to as the
** "environment"). The derivate is sometimes called a "curried function".
**
** This idea can be applied to implement continuations in an algorithm that
** blocks waiting for some of its parameters to arrive.
**
** This interface defines a checked exception to be thrown from the {@link
** #invoke()} method; if you call it on a general {@code Closure} then you will
** also need to catch {@link Exception}. If you don't wish to do this, use and
** implement {@link SafeClosure} instead.
**
** (Unfortunately, using a type paramater to indicate the type of exception
** thrown is unacceptable; the signature {@code throws T} disallows throwing
** multiple types of exception, whereas {@code throws Exception} allows it.)
**
** @param <P> Type of the input parameter. If you need more than one input
**        parameter, consider using the classes in {@link Tuples}.
** @param <E> Type of the thrown exception(s). If your implementation throws
**        more than one type of exception, it's recommended to give the closest
**        superclass of all the exceptions, though {@link Exception} is always
**        safe. If your implementation throws no checked exceptions, consider
**        using {@link SafeClosure} instead of this class.
** @author infinity0
** @see <a href="http://en.wikipedia.org/wiki/Closure_%28computer_science%29">Closure
**      (computer science)</a>
*/
public interface Closure<P, E extends Exception> {

	public void invoke(P param) throws E;

}
