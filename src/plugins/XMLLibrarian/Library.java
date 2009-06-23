package plugins.XMLLibrarian;

import freenet.support.Logger;



public class Library{
	
	public static Request findTerm(String indexid, String term) throws Exception{
		Index index = getIndex(indexid);
		Logger.minor(Library.class, "Finding term: "+term);
		Request request = index.find(term);
		return request;
	}
	
	
	
	public static Index getIndex(String indexid) throws InvalidSearchException{
		Logger.minor(Library.class, "Getting Index: "+indexid);
		return Index.getIndex(indexid);
	}
}
