/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search.inter;

import plugins.Library.library.Index;
import plugins.Library.index.TermEntry;
import plugins.Library.index.Request;
import plugins.Library.serial.ChainedProgress;

import java.util.Collection;

import java.util.concurrent.BlockingQueue;

/**
** Class representing a query for a single term on a single index.
**
** TODO
**
** @author infinity0
*/
public class IndexQuery implements /*Request,*/ Runnable {

	final BlockingQueue<IndexQuery> out;
	// PRIORITY error too...

	final public Index index;
	final public String term;

	// TODO implement Request by delegating all methods to this
	final public Request<Collection<TermEntry>> request;

	public IndexQuery(Index i, String t, BlockingQueue<IndexQuery> o) {
		index = i;
		term = t;
		out = o;
		request = index.getTermEntries(term); // , false); TODO implement
	}

	public void run() {
		// this uses different thread... untidy...
		// if (!request.started()) { request.run(); }
		// else { request.join(); }
		// TODO make Request implement Runnable
		try {
			out.put(this);
		} catch (InterruptedException e) {
			// TODO.. setError(new TaskAbortException("Query was interrupted", e);
		}
	}

}
