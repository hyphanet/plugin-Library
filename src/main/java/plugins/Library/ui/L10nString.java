/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.ui;

import freenet.l10n.BaseL10n;
import freenet.l10n.NodeL10n;
import freenet.l10n.BaseL10n.LANGUAGE;

/**
 * Feeble attempt at a translation interface, this is best left until there is a
 * general Plugin method for doing this
 *
 * @author MikeB
 */
public class L10nString{


	static LANGUAGE lang;

	public static String getString(String key){
		lang = NodeL10n.getBase().getSelectedLanguage();
		if("Index".equals(key))
			switch(lang){
				case ENGLISH:
				default:
					return "Index ";
				}
		else if("Searching-for".equals(key))
			switch(lang){
				case ENGLISH:
				default:
					return "Searching for ";
				}
		else if("in-index".equals(key))
			switch(lang){
				case ENGLISH:
				default:
					return " in index ";
				}
		else if("Search-status".equals(key))
			switch(lang){
				case ENGLISH:
				default:
					return "Search status : ";
				}
		else if("failed".equals(key))
			switch(lang){
				case ENGLISH:
				default:
					return "Failed";
				}
		else if("title".equals(key))
			switch(lang){
				case ENGLISH:
				default:
					return "Search Freenet";
				}
		else if("page-warning".equals(key))
			switch(lang){
			case ENGLISH:
				default:
					return "WARNING: This search, like the rest of Freenet, is not filtered, and could find offensive or illegal content. Be careful!";
			}
		else
			return key;
	}

	public static void setLanguage(LANGUAGE newLanguage){
		lang = newLanguage;
	}
}
