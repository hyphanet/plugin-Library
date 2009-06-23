package plugins.XMLLibrarian;

import freenet.support.Logger;


/**
 * Library class is the api for others to use search facilities, it is used by the interfaces
 * @author MikeB
 */
public class Library{

	/**
	 * Find the specified index and start a find request on it for the specified term
	 */
	public static Request findTerm(String indexid, String term) throws Exception{
		Index index = getIndex(indexid);
		Logger.minor(Library.class, "Finding term: "+term);
		Request request = index.find(term);
		return request;
	}
	
	
	/**
	 * Gets an index using its id in the form {type}:{uri} <br />
	 * known types are xml, bookmark
	 * @param indexid
	 * @return Index object
	 * @throws plugins.XMLLibrarian.InvalidSearchException
	 */
	public static Index getIndex(String indexid) throws InvalidSearchException{
		Logger.minor(Library.class, "Getting Index: "+indexid);
		return Index.getIndex(indexid);
	}
}
