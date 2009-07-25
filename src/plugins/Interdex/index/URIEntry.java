/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import java.util.Date;
import java.util.Set;
import java.util.HashSet;

import freenet.keys.FreenetURI;

/**
** Data associated with a {@link FreenetURI}. DOCUMENT expand this...
**
** URGENT code equals()
**
** @author infinity0
*/
public class URIEntry {

	/**
	** Subject URI of this entry.
	*/
	protected FreenetURI subject;

	/**
	** Quality rating. Must be in the closed interval [0,1].
	*/
	protected float qual;

	// TODO make bean getter/setters for these
	Date date_checked;
	String title;
	int size;
	String type;

	/**
	** Terms that this URI is associated with.
	*/
	protected Set<String> terms;

	public URIEntry(FreenetURI u) {
		subject = u;
		date_checked = new Date();
		terms = new HashSet<String>();
	}

	public FreenetURI getSubject() {
		return subject;
	}

	public void setSubject(FreenetURI u) {
		subject = u;
	}

	public float getQuality() {
		return qual;
	}

	public void setQuality(float q) {
		if (q < 0 || q > 1) {
			throw new IllegalArgumentException("Relevance must be in the closed interval [0,1].");
		}
		qual = q;
	}

	public Set<String> getTerms() {
		return terms;
	}

	public void setTerms(Set<String> t) {
		terms = (t == null)? new HashSet<String>(): t;
	}

}
