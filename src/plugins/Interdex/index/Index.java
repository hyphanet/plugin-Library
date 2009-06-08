/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import java.util.HashSet;

import freenet.keys.FreenetURI;

import plugins.Interdex.util.PrefixTreeMap;

/**
** @author infinity0
*/
public class Index {

	PrefixTreeMap<Token, TokenFilter> filtab;
	PrefixTreeMap<Token, HashSet<TokenEntry>> tktab;
	PrefixTreeMap<URIKey, URIEntry> utab;

	String format;
	String filterType;
	// TODO: etc

	public HashSet<TokenEntry> getEntry(Token t) {
		// TODO: override me
		return (HashSet<TokenEntry>)tktab.get(t);
	}

	public void addEntry(TokenEntry n) {
		// TODO
	}

	public void remEntry(TokenEntry n) {
		// TODO
	}

}
