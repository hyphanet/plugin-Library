/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library;

import plugins.Library.index.TermEntry;
import plugins.Library.index.URIEntry;
import plugins.Library.util.exec.Execution;

import freenet.keys.FreenetURI;

import java.util.Collection;
import java.util.Set;

/**
** Represents a writable Index.
**
** TODO
**
** @author infinity0
*/
public interface WriteableIndex extends Index {

	public Execution<FreenetURI> putTermEntries(Collection<TermEntry> entries);

	public Execution<FreenetURI> remTermEntries(Collection<TermEntry> entries);

	public Execution<FreenetURI> putURIEntries(Collection<URIEntry> entries);

	public Execution<FreenetURI> remURIEntries(Collection<URIEntry> entries);

}
