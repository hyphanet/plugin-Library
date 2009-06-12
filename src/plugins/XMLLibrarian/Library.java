package plugins.XMLLibrarian;

import java.util.HashMap;


class Library{
	HashMap<String, Index> bookmarks;
	
	public Library(){
		bookmarks = new HashMap<String, Index>();
		try{
			bookmarks.put("wanna", Index.getIndex("USK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search/19/"));
		}catch(InvalidSearchException e){
			// Couldnt add wanna for some reason
		}
	}
	
	public Request findTerm(String indexid, String term) throws InvalidSearchException{
		Index index = getIndex(indexid);
		Request request = index.find(term);
		return request;
	}
	
	public Request getPageMeta(String indexid, String pageid) throws InvalidSearchException{
		Index index = getIndex(indexid);
		Request request = index.getPage(pageid);
		return request;
	}
	
	
	
	private Index getIndex(String indexid) throws InvalidSearchException{
		if (indexid.startsWith("bookmark:"))
			return bookmarks.get(indexid.substring(9));
		else
			return Index.getIndex(indexid);
	}
}
