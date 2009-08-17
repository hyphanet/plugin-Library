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
** Represents a writable Index.
**
** TODO
**
** @author infinity0
*/
public interface WriteableIndex extends Index {

	public Request<Collection<TermEntry>> clearTermEntries(String term, boolean autostart);

	public Request<TermEntry> putTermEntry(TermEntry entry, boolean autostart);

	public Request<TermEntry> remTermEntry(TermEntry entry, boolean autostart);

	public Request<URIEntry> clearURIEntry(FreenetURI uri, boolean autostart);

	public Request<URIEntry> putURIEntry(URIEntry entry, boolean autostart);

	public Request<URIEntry> remURIEntry(URIEntry entry, boolean autostart);

	public Request<Object> commitAndPush(boolean autostart);

}
