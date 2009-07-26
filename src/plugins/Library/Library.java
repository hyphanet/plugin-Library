/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package plugins.Library;

import plugins.Library.library.*;
import plugins.Library.index.*;
import plugins.Library.index.xml.XMLIndex;
import plugins.Library.search.Request;
import plugins.Library.search.InvalidSearchException;

import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * Library class is the api for others to use search facilities, it is used by the interfaces
 * @author MikeB
 */
public class Library {

	public static final String DEFAULT_INDEX_SITE = "bookmark:freenetindex";
	private static int version = 1;


	private static final Class[] indexTypes = new Class[]{
		plugins.Library.index.xml.XMLIndex.class,
	};


	/**
	** Holds all the read-indexes.
	*/
	private static Map<String, Index> rtab = new HashMap<String, Index>();

	/**
	** Holds all the writeable indexes.
	*/
	private static Map<String, WriteableIndex> wtab = new HashMap<String, WriteableIndex>();

	/**
	** Holds all the bookmarks (aliases into the rtab).
	*/
	private static Map<String, String> bookmarks = new HashMap<String, String>();
	private static PluginRespirator pr;

	public static long getVersion() {
		return version;
	}


	/**
	 * Find the specified index and start a find request on it for the specified term
	 */
	public static Request findTerm(String indexid, String term) throws Exception{
		Index index = getIndex(indexid);
		Logger.minor(Library.class, "Finding term: "+term);
		Request request = index.getTermEntries(term);
		return request;
	}






	/**
	 * Returns a set of Index objects one for each of the uri's specified
	 * gets an existing one if its there else makes a new one
	 *
	 * @param indexuris list of index specifiers separated by spaces
	 * @return Set of Index objects
	 */
	public static final ArrayList<Index> getIndices(String indexuris) throws InvalidSearchException{
		String[] uris = indexuris.split("[ ;]");
		ArrayList<Index> indices = new ArrayList<Index>(uris.length);

		for ( String uri : uris){
			indices.add(getIndex(uri));
		}
		return indices;
	}

	/**
	 * Static method to get all of the instatiated Indexes
	 */
	public static final Iterable<Index> getAllIndices() {
		return rtab.values();
	}

	/**
	 * Gets an index using its id in the form {type}:{uri} <br />
	 * known types are xml, bookmark
	 * @param indexid
	 * @return Index object
	 * @throws plugins.Library.util.InvalidSearchException
	 */
	/**
	 * Returns an Index object for the uri specified,
	 * gets an existing one if its there else makes a new one
	 *
	 * TODO : identify all index types so index doesn't need to refer to them directly
	 * @param indexuri index specifier
	 * @return Index object
	 */
	public static final Index getIndex(String indexuri) throws InvalidSearchException{
		if (indexuri.startsWith("bookmark:")){
			if (bookmarks.containsKey(indexuri.substring(9)))
				return getIndex(bookmarks.get(indexuri.substring(9)));
			else
				throw new InvalidSearchException("Index bookmark '"+indexuri.substring(9)+" does not exist");
		}

		if (!indexuri.endsWith("/"))
			indexuri += "/";
		if (rtab.containsKey(indexuri))
			return rtab.get(indexuri);

		Index index;
		// Look for this index type in the indexTypes array
		if(indexuri.contains(":"))
			for (int i = 0; i < indexTypes.length; i++) {
				Class class1 = indexTypes[i];
				try {
					String id = ((String)class1.getMethod("getID").invoke(null));
					if (indexuri.split(":")[0].equalsIgnoreCase(id))
						index = (Index) class1.getConstructor(indexuri.getClass(), pr.getClass()).newInstance(indexuri, pr);
				} catch (Exception ex) {
					Logger.normal(class1, indexuri, ex);
				}
			}

		//if(indexuri.startsWith("xml:")){
			index = new XMLIndex(indexuri, pr);
			rtab.put(indexuri, index);
			return index;
		//}
		//throw new UnsupportedOperationException("Unrecognised index type, id format is <type>:<key> {"+indexuri+"}");
	}


	/**
	 * Static method to setup Index class so it has access to PluginRespirator, and load bookmarks
	 * TODO pull bookmarks from disk
	 */
	public static final void setup(PluginRespirator pr){
		Library.pr = pr;
		bookmarks.put("wanna", "USK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search/19/");
		bookmarks.put("wanna19", "SSK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search-19/");
		bookmarks.put("freenetindex", "USK@US6gHsNApDvyShI~sBHGEOplJ3pwZUDhLqTAas6rO4c,3jeU5OwV0-K4B6HRBznDYGvpu2PRUuwL0V110rn-~8g,AQACAAE/freenet-index/2/");
	}



	private static String convertToHex(byte[] data) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < data.length; i++) {
			int halfbyte = (data[i] >>> 4) & 0x0F;
			int two_halfs = 0;
			do {
				if ((0 <= halfbyte) && (halfbyte <= 9))
					buf.append((char) ('0' + halfbyte));
				else
					buf.append((char) ('a' + (halfbyte - 10)));
				halfbyte = data[i] & 0x0F;
			} while (two_halfs++ < 1);
		}
		return buf.toString();
	}

	//this function will return the String representation of the MD5 hash for the input string
	public static String MD5(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] b = text.getBytes("UTF-8");
			md.update(b, 0, b.length);
			byte[] md5hash = md.digest();
			return convertToHex(md5hash);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
