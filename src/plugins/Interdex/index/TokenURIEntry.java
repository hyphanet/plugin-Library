/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import freenet.keys.FreenetURI;

import java.util.Map;

/**
** A {@link TokenEntry} that associates a subject term with a final target
** {@link FreenetURI} that satisfies the term.
**
** @author infinity0
*/
public class TokenURIEntry extends TokenEntry {

	/**
	** URI of the target
	*/
	protected FreenetURI uri;

	/**
	** Type of the target. TODO make subclasses with set types & metadata, eg
	** doc -> occurences, audio, video, etc etc
	*/
	protected String type;

	/**
	** Type-dependent metadata.
	*/
	protected Map<String, Object> meta;

	/**
	** Pointer to the {@link URIEntry} relating to the target uri.
	**
	** TODO serialiser should fill this in.
	*/
	protected transient URIEntry uridata_;

	/**
	** Empty constructor for the JavaBean convention.
	*/
	public TokenURIEntry() { }

	public TokenURIEntry(String s, FreenetURI u) {
		super(s);
		setURI(u);
	}

	public FreenetURI getURI() {
		return uri;
	}

	public void setURI(FreenetURI u) {
		// OPTIMISE make the translator use the same URI object as from the URI table
		uri = u;
	}

	public String getType() {
		return type;
	}

	public void setType(String t) {
		type = t.intern();
	}

	public Map<String, Object> getMeta() {
		return meta;
	}

	public void setMeta(Map<String, Object> m) {
		meta = m;
	}

}
