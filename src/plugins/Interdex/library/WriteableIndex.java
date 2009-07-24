/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import freenet.keys.FreenetURI;

import java.util.Collection;

/**
** Represents a writable Index.
**
** @author infinity0
*/
public interface WriteableIndex extends Index {

	public Request<Collection<TokenEntry>> clearTermEntries(String term);

	public Request<TokenEntry> putTokenEntry(TokenEntry entry);

	public Request<TokenEntry> remTokenEntry(TokenEntry entry);

	public Request<URIEntry> clearURIEntry(FreenetURI uri);

	public Request<URIEntry> putURIEntry(URIEntry entry);

	public Request<URIEntry> remURIEntry(URIEntry entry);

	public Request<Object> commitAndPush();

}
