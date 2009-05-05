/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import freenet.keys.FreenetURI;

/**
** This class handles serialisation of an IndexTree. inflate(), deflate() act
** on the IndexTree and all its parts; inflate(Token), deflate(Token) act only
** on the parts which match the given Token.
**
** Data considered as "part" of the IndexTree include:
**   * metadata
**   * all {IndexNode,IndexFilter,TokenEntry}s reachable from it
**
** Data considered as not part of the IndexTree include:
**   * other {IndexTree}s pointed to by an {IndexRedirectionEntry}
**
** @author infinity0
*/
public abstract class IndexSerialiser {

	IndexTree tree;
	FreenetURI uri;
	// TODO: make thread-safe?

	public IndexSerialiser(FreenetURI u) {
		// TODO: research
		// probably use a SSK-ID instead of FreenetURI, if there is a class for that
		// or the WoT object for an ID
		uri = u;
		tree = new IndexTree();
	}

	public IndexSerialiser(IndexTree t) {
		// TODO: research
		// probably use a SSK-ID instead of FreenetURI, if there is a class for that
		// or the WoT object for an ID
		uri = new FreenetURI("", "");
		tree = t;
	}

	/************************************************************************
	 * DEFLATE
	 ************************************************************************/

	public void deflate() {
		deflateIndexNodeRecursive(tree.root);
		deflateIndexMeta();
		deflateIndex();
	}

	void deflateIndexNodeRecursive(IndexNode node) {
		deflateIndexFilter(node.filter);
		for (IndexNode ch: node.child) {
			deflateIndexNode(ch);
		}
		for (Token tk: node.tmap.keySet()) {
			IndexEntry en = node.tmap.get(tk);
			if (en instanceof IndexTokenEntry) {
				deflateTokenIndex(((IndexTokenEntry)en).index);
			} else if (en instanceof IndexRedirectionEntry) {
				// pass, this is someone else's problem
			}
		}
		deflateIndexNode(node);
	}

	abstract void deflateIndex();
	abstract void deflateIndexMeta();
	abstract void deflateIndexNode(IndexNode node);
	abstract void deflateIndexFilter(IndexFilter filter);
	abstract void deflateTokenIndex(TokenIndex index);

	/************************************************************************
	 * INFLATE
	 ************************************************************************/

	public void inflate() {
		inflateIndex();
		inflateIndexMeta();
		inflateIndexNodeRecursive(tree.root);
	}

	void inflateIndexNodeRecursive(IndexNode node) {
		// TODO
	}

	abstract void inflateIndex();
	abstract void inflateIndexMeta();
	abstract void inflateIndexNode(IndexNode node);
	abstract void inflateIndexFilter(IndexFilter filter);
	abstract void inflateTokenIndex(TokenIndex index);

	/************************************************************************
	 * TOKEN-DEFLATE
	 ************************************************************************/

	/**
	** Deflate an IndexTree, but only at the parts which match Token t.
	**
	** @param	tk	The token to look for
	*/
	public void deflate(Token tk) {
		deflateIndexNodeRecursive(tree.root, tk);
		deflateIndexMeta();
		deflateIndex();
	}

	void deflateIndexNodeRecursive(IndexNode node, Token tk) {
		// TODO
	}

	/************************************************************************
	 * TOKEN-INFLATE
	 ************************************************************************/

	/**
	** Inflate an IndexTree, but only at the parts which match Token t.
	**
	** @param	tk	The token to look for
	*/
	public void inflate(Token tk) {
		inflateIndex();
		inflateIndexMeta();
		inflateIndexNodeRecursive(tree.root, tk);
	}

	void inflateIndexNodeRecursive(IndexNode node, Token tk) {
		inflateIndexNode(node);

		IndexEntry en = node.tmap.get(tk);
		if (en == null) { return; }

		if (en instanceof IndexTokenEntry) {
			inflateTokenIndex(((IndexTokenEntry)en).index);
		} else if (en instanceof IndexRedirectionEntry) {
			// pass, this is someone else's problem
		}

		inflateIndexFilter(node.filter);
		if (node.filter != null && !node.filter.has(tk)) { return; }

		for (IndexNode ch: node.child) {
			// TODO if ch.prefix_bytes do not match t, continue
			inflateIndexNodeRecursive(ch, tk);
		}

	}

}
