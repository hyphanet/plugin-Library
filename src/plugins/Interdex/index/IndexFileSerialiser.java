/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import freenet.keys.FreenetURI;

import plugins.Interdex.util.PrefixTreeMap;

/**
** This class handles serialisation of an IndexTree into a filetree.
**
** @author infinity0
*/
public class IndexFileSerialiser extends IndexSerialiser {

	public IndexFileSerialiser(FreenetURI u) {
		super(u);
	}

	public IndexFileSerialiser(Index t) {
		super(t);
	}

	// TODO
	void inflateIndex() {}
	void inflateMeta() {}
	void inflatePMN(PrefixTreeMap node) {}

	// TODO
	void deflateIndex() {}
	void deflateMeta() {}
	void deflatePMN(PrefixTreeMap node) {}

}
