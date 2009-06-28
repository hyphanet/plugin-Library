/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

/**
** @author infinity0
*/
public abstract class TokenEntry implements Comparable<TokenEntry> {

	public int relevance;

	// TODO make reverse order?
	public int compareTo(TokenEntry o) {
		if (this == o) { return 0; }
		int d = relevance - o.relevance;
		// this is a bit of a hack but is needed since Tree* treats two objects
		// as "equal" if their "compare" returns 0
		if (d != 0) { return d; }
		int h = hashCode() - o.hashCode();
		// on the off chance that the hashCodes are equal but the objects are not,
		// test the string representations of them...
		return (h != 0)? h: (equals(o))? 0: toString().compareTo(o.toString());
	}

}
