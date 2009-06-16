/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import java.util.Date;

import freenet.keys.FreenetURI;

/**
** @author infinity0
*/
public class URIEntry {

	final FreenetURI uri;
	Date date_checked;

	String title;
	int size;
	String mime_type;

	public URIEntry(FreenetURI u) {
		uri = u;
		date_checked = new Date();
	}

}
