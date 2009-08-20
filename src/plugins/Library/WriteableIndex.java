/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library;

import plugins.Library.index.TermEntry;
import plugins.Library.index.URIEntry;
import plugins.Library.util.exec.Execution;

import freenet.keys.FreenetURI;

import java.util.Set;

/**
** Represents a writable Index.
**
** TODO
**
** @author infinity0
*/
public interface WriteableIndex extends Index {

	public Execution<Set<TermEntry>> clearTermEntries(String term);

	public Execution<TermEntry> putTermEntry(TermEntry entry);

	public Execution<TermEntry> remTermEntry(TermEntry entry);

	public Execution<URIEntry> clearURIEntry(FreenetURI uri);

	public Execution<URIEntry> putURIEntry(URIEntry entry);

	public Execution<URIEntry> remURIEntry(URIEntry entry);

	public Execution<Object> commitAndPush();

}
