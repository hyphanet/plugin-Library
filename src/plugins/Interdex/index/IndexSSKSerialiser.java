/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import freenet.keys.FreenetURI;

/**
** This class handles serialisation of an IndexTree into an SSK. This includes
** metadata, filters, and {IndexNode}s. {TokenIndex}s are stored in CHKs.
**
** @author infinity0
*/
public class IndexSSKSerialiser extends IndexSerialiser {

	public IndexSSKSerialiser(FreenetURI u) {
		super(u);
	}

	public IndexSSKSerialiser(IndexTree t) {
		super(t);
	}

	// TODO
	void inflateIndex() {}
	void inflateIndexMeta() {}
	void inflateIndexNode(IndexNode node) {}
	void inflateIndexFilter(IndexFilter filter) {}
	void inflateTokenIndex(TokenIndex index) {}

	// TODO
	void deflateIndex() {}
	void deflateIndexMeta() {}
	void deflateIndexNode(IndexNode node) {}
	void deflateIndexFilter(IndexFilter filter) {}
	void deflateTokenIndex(TokenIndex index) {}

	public void deflate(Token tk) {
		throw new UnsupportedOperationException("SSKSerialiser does not support token-deflate.");
	}

}
