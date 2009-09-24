/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.func;

/**
** A {@link Closure} that cannot throw a checked exception.
**
** @param <P> Type of the input parameter. If you want a closure with more than
**        one input parameter, consider using the classes in {@link Tuples}.
** @author infinity0
*/
public interface SafeClosure<P> extends Closure<P, RuntimeException> {

	public boolean invoke(P param);

}
