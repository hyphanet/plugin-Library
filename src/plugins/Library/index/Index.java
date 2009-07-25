/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import freenet.keys.FreenetURI;
import plugins.Library.util.Request;
import java.util.Collection;

/**
** Represents the data for an index.
**
** @author infinity0
*/
public interface Index {

	/**
	** Fetch the TokenEntries associated with a given term.
	**
	** TODO decide what to return.
	**
	** @param term The term to fetch the entries for
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The fetched entries
	** @throws DataNotLoadedException
	**         if the TokenEntries have not been loaded
	 * TODO sort out the return types
	*/
	public Request<Collection<?>> getTermEntries(String term);

	public Request<?> getURIEntry(FreenetURI uri);

}
