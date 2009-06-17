/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import freenet.keys.FreenetURI;

import plugins.Interdex.util.PrefixTreeMap;

/**
** This class handles serialisation of an IndexTree. inflate(), deflate() act
** on the IndexTree and all its parts; inflate(Token), deflate(Token) act only
** on the parts which match the given Token.
**
** Data considered as "part" of the IndexTree include:
**   * metadata
**   * all {PrefixTreeMap,TokenEntry}s reachable from it
**
** Data considered as not part of the IndexTree include:
**   * other {IndexTree}s pointed to by an {IndexRedirectionEntry}
**
** @author infinity0
*/
public abstract class IndexSerialiser {

	Index index;
	FreenetURI uri;
	// TODO: make thread-safe?

	public IndexSerialiser(FreenetURI u) {
		// TODO: research
		// probably use a SSK-ID instead of FreenetURI, if there is a class for that
		// or the WoT object for an ID
		uri = u;
		//index = new Index();
	}

	public IndexSerialiser(Index t) {
		// TODO: research
		// probably use a SSK-ID instead of FreenetURI, if there is a class for that
		// or the WoT object for an ID
		uri = new FreenetURI("", "");
		index = t;
	}

	/************************************************************************
	 * DEFLATE
	 ************************************************************************/

	public void deflate() {
		deflatePMTRecursive(index.utab);
		deflatePMTRecursive(index.tktab);
		deflatePMTRecursive(index.filtab);
		deflateMeta();
		deflateIndex();
	}

	void deflatePMTRecursive(PrefixTreeMap node) {
		throw new UnsupportedOperationException("Not implemented.");
		/*for (PrefixTreeMap ch: node.child) {
			deflatePMTRecursive(ch);
		}
		deflatePMN(node);*/
	}

	abstract void deflateIndex();
	abstract void deflateMeta();
	abstract void deflatePMN(PrefixTreeMap node);

	/************************************************************************
	 * INFLATE
	 ************************************************************************/

	public void inflate() {
		inflateIndex();
		inflateMeta();
		inflatePMTRecursive(index.filtab);
		inflatePMTRecursive(index.tktab);
		inflatePMTRecursive(index.utab);
	}

	void inflatePMTRecursive(PrefixTreeMap node) {
		// TODO
	}

	abstract void inflateIndex();
	abstract void inflateMeta();
	abstract void inflatePMN(PrefixTreeMap node);

	/************************************************************************
	 * TOKEN-DEFLATE
	 ************************************************************************/

	/**
	** Deflate an IndexTree, but only at the parts which match Token t.
	**
	** @param	tk	The token to look for
	*/
	public void deflate(Token tk) {
		deflatePMTRecursive(index.utab, tk);
		deflatePMTRecursive(index.tktab, tk);
		deflatePMTRecursive(index.filtab, tk);
		deflateMeta();
		deflateIndex();
	}

	void deflatePMTRecursive(PrefixTreeMap node, Token tk) {
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
		inflateMeta();
		inflatePMTRecursive(index.filtab, tk);
		inflatePMTRecursive(index.tktab, tk);
		inflatePMTRecursive(index.utab, tk);
	}

	void inflatePMTRecursive(PrefixTreeMap node, Token tk) {
		throw new UnsupportedOperationException("Not implemented.");
		//inflatePMN(node);
		/*
		IndexEntry en = node.hmap.get(tk);
		if (en == null) { return; }

		if (en instanceof IndexTokenEntry) {
			inflateTokenIndex(((IndexTokenEntry)en).index);
		} else if (en instanceof IndexRedirectionEntry) {
			// pass, this is someone else's problem
		}

		inflateIndexFilter(node.filter);
		if (node.filter != null && !node.filter.has(tk)) { return; }

		for (PrefixTreeMap ch: node.child) {
			// TODO if ch.prefix_bytes do not match t, continue
			inflatePMTRecursive(ch, tk);
		}*/

	}

}
