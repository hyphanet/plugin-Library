/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search.inter;

import plugins.Library.Index;
import plugins.Library.index.TermEntry;
import plugins.Library.index.Request;

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

	final public IndexIdentity id;
	final public TermDefinition termdef;

	// TODO implement Request by delegating all methods to this
	final public Request<Collection<TermEntry>> request;

	public IndexQuery(IndexIdentity i, TermDefinition td, BlockingQueue<IndexQuery> o) {
		id = i;
		termdef = td;
		out = o;
		request = id.index.getTermEntries(termdef.subject); // , false); TODO implement
	}

	public void run() {
		try {
			// this uses different thread... untidy...
			// if (!request.started()) { request.run(); }
			// else { request.join(); }
			// TODO make Request implement Runnable

			out.put(this);
		} catch (InterruptedException e) {
			// TODO.. setError(new TaskAbortException("Query was interrupted", e);
		}
	}

}
