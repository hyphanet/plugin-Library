/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.XMLLibrarian;

/**
 * Necessary to be able to use pluginmanager's versions
 */
public class Version {
	private static final String svnRevision = "@custom@";

	static String getSvnRevision() {
		return svnRevision;
	}
}
