/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.library;

import plugins.Library.index.TermEntry;
import plugins.Library.index.URIEntry;
import plugins.Library.index.Request;

import freenet.keys.FreenetURI;

import java.util.Collection;

/**
** Represents the data for an index.
**
** @author infinity0
*/
public interface Index {

	/**
	** Non-blocking fetch of the entries associated with a given term.
	**
	** DOCUMENT
	**
	** @param autostart Whether to automatically start the request using the
	**        default executor for the index. Note that supplying {@code false}
	**        does not guarantee that the request will not be started by the
	**        time you get to it; another thread may have called {@link
	**        Request#execute()} on it in the meantime.
	*/
	public Request<Collection<TermEntry>> getTermEntries(String term, boolean autostart);

	public Request<URIEntry> getURIEntry(FreenetURI uri, boolean autostart);

}
