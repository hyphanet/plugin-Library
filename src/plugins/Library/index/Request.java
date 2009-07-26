/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

/**
** Dummy interface so my code compiles; pending merge to plugin-Library
**
** @author infinity0
*/
public interface Request<E> {

	public void start();

	public E getResult();

	public String getCurrentStatus();

	public String getCurrentStage();

}
