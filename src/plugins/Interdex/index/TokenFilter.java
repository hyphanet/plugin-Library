/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

/**
** This interface specifies a filter to pass any queries through before going
** deeper into an IndexNode structure. If the query returns false here, it is
** dropped. Hence, the filter may give false positives, but it MUST NOT give
** false negatives.
**
** A good filter will improve performance by quickly dropping negative queries.
**
** @author infinity0
*/
public interface TokenFilter {

	public boolean has(Token token);
	public void put(Token token);

}
