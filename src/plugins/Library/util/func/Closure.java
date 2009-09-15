/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.func;

/**
** A closure. DOCUMENT.
**
** Useful for implementing continuations in an algorithm that blocks waiting
** for its parameters to arrive.
**
** @param <P> Type of the input parameter. If you want a closure with more than
**        one input parameter, consider using the classes in {@link Tuples}.
** @author infinity0
** @see <a href="http://en.wikipedia.org/wiki/Closure_%28computer_science%29">Closure
**      (computer science)</a>
*/
public interface Closure<P> {

	public void invoke(P param);

}
