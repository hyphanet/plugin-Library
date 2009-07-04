/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import java.util.Date;
import java.util.Set;

import freenet.keys.FreenetURI;

/**
** DOCUMENT
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

	Date date_checked;
	String title;
	int size;
	String type;

	/**
	** Terms that this URI is associated with.
	*/
	Set<String> terms;

	public URIEntry(FreenetURI u) {
		subject = u;
		date_checked = new Date();
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

}
