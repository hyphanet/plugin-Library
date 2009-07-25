/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import freenet.keys.FreenetURI;

import java.util.Collection;
import plugins.Library.util.Request;

/**
** Represents a writable Index.
**
** @author infinity0
 *
 * TODO sort out types with requests
*/
public interface WriteableIndex extends Index {

	public Request<Collection<?>> clearTermEntries(String term);

	public Request<?> putTokenEntry(Object entry);

	public Request<?> remTokenEntry(Object entry);

	public Request<?> clearURIEntry(FreenetURI uri);

	public Request<?> putURIEntry(Object entry);

	public Request<?> remURIEntry(Object entry);

	public Request<Object> commitAndPush();

}
