package plugins.Library.interfaces;

import freenet.l10n.L10n;
import freenet.l10n.L10n.LANGUAGE;


public class L10nString{


	static LANGUAGE lang;
	
	public static String getString(String key){
		lang = L10n.getSelectedLanguage();
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
		else
			return key;
	}
	
	public static void setLanguage(LANGUAGE newLanguage){
		lang = newLanguage;
	}
}
