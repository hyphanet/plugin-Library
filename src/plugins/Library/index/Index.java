package plugins.Library.index;


import plugins.Library.util.InvalidSearchException;
import plugins.Library.util.Request;
import plugins.Library.*;
import java.util.ArrayList;
import java.util.HashMap;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Executor;
import plugins.Library.index.xml.XMLIndex;

/**
 * Index Class	<br />
 * Index's are identified {Index type}:{Index identifier} <br />
 * eg. index type 'xml' creates an XMLIndex
 */
public abstract class Index {
	public enum FetchStatus{UNFETCHED, FETCHING, FETCHED, FAILED}
	static protected PluginRespirator pr;
	static protected Executor executor;
	/**
	 * Map of all indexes currently open Key is indexid
	 */
	static protected HashMap<String, Index> allindices = new HashMap<String, Index>();
	static private HashMap<String, Index> bookmarks = new HashMap<String, Index>();


	protected String indexuri;
	protected FetchStatus fetchStatus = FetchStatus.UNFETCHED;

	protected HashMap<String, String> indexMeta;
	

	/**
	 * Initialise indexMeta
	 */
	protected Index(){
		indexMeta = new HashMap<String, String>();
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
		return allindices.values();
	}
	
	/**
	 * Returns an Index object for the uri specified,
	 * gets an existing one if its there else makes a new one
	 * 
	 * @param indexuri index specifier
	 * @return Index object
	 */
	public static final Index getIndex(String indexuri) throws InvalidSearchException{
		if (indexuri.startsWith("bookmark:")){
			if (bookmarks.containsKey(indexuri.substring(9)))
				return bookmarks.get(indexuri.substring(9));
			else
				throw new InvalidSearchException("Index bookmark '"+indexuri.substring(9)+" does not exist");
		}

		if (!indexuri.endsWith("/"))
			indexuri += "/";
		if (allindices.containsKey(indexuri))
			return allindices.get(indexuri);
		
		//if(indexuri.startsWith("xml:")){
			Index index = new XMLIndex(indexuri);
			allindices.put(indexuri, index);
			return index;
		//}
		//throw new UnsupportedOperationException("Unrecognised index type, id format is <type>:<key> {"+indexuri+"}");
	}

	/**
	 * @return the baseURI of this INdex
	 */
	public String getIndexURI(){
		return indexuri;
	}

	/**
	 * Should be overriden with a function which searches for a term in this Index
	 * @param term a single term to search for
	 * @return Request tracking this operation
	 */
	public abstract Request find(String term);
	
	/**
	 * Static method to setup Index class so it has access to PluginRespirator, and load bookmarks
	 * TODO pull bookmarks from disk
	 */
	public static final void setup(PluginRespirator pr){
		Index.pr = pr;
		executor = pr.getNode().executor;
		try{
			bookmarks.put("wanna", Index.getIndex("USK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search/19/"));
			bookmarks.put("wanna19", Index.getIndex("SSK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search-19/"));
			bookmarks.put("freenetindex", Index.getIndex("USK@US6gHsNApDvyShI~sBHGEOplJ3pwZUDhLqTAas6rO4c,3jeU5OwV0-K4B6HRBznDYGvpu2PRUuwL0V110rn-~8g,AQACAAE/freenet-index/2/"));
		}catch(InvalidSearchException e){
			// Couldnt add wanna for some reason
		}
	}
	
	@Override
	public String toString(){
		return "Index : "+indexuri+" "+fetchStatus;
	}
}




