/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.concurrent;

/**
** An interface for a general class that accepts objects to be acted on.
**
** TODO NORM maybe just get rid of this since we now have ObjectProcessor
**
** @author infinity0
*/
public interface Scheduler extends java.io.Closeable {

	/**
	** Stop accepting objects (but continue any started actions).
	*/
	public void close();

}
