package plugins.XMLLibrarian;


import java.util.ArrayList;
import java.util.HashMap;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Executor;
import plugins.XMLLibrarian.xmlindex.FindRequest;
import plugins.XMLLibrarian.xmlindex.XMLIndex;

/**
 * Index Class	<br />
 * Index's are identified {Index type}:{Index identifier} <br />
 * eg. index type 'xml' creates an XMLIndex
 */
public abstract class Index {
	public enum FetchStatus{UNFETCHED, FETCHING, FETCHED, FAILED}
	static protected PluginRespirator pr;
	protected static Executor executor;
	/**
	 * Map of all indexes currently open Key is indexid
	 */
	protected static HashMap<String, Index> allindices = new HashMap<String, Index>();

	
	protected String indexuri;
	protected FetchStatus fetchStatus = FetchStatus.UNFETCHED;

	// FIXME these two should probably be using Request not FetchRequest
	protected ArrayList<FindRequest> waitingOnMainIndex = new ArrayList<FindRequest>();
	protected ArrayList<FindRequest> requests = new ArrayList<FindRequest>();
	
	protected String mainIndexDescription;

	protected HashMap<String, String> indexMeta;
	private static HashMap<String, Index> bookmarks = new HashMap<String, Index>();
	

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
		String[] uris = indexuris.split(" ");
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
		if (indexuri.startsWith("bookmark:"))
			return bookmarks.get(indexuri.substring(9));

		if (!indexuri.endsWith("/"))
			indexuri += "/";
		if (allindices.containsKey(indexuri))
			return allindices.get(indexuri);
		
		if(indexuri.startsWith("xml:")){
			Index index = new XMLIndex(indexuri.substring(4));
			allindices.put(indexuri, index);
			return index;
		}
		throw new UnsupportedOperationException("Unrecognised index type, id format is <type>:<key> {"+indexuri+"}");
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
			bookmarks.put("wanna", Index.getIndex("xml:USK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search/19/"));
		}catch(InvalidSearchException e){
			// Couldnt add wanna for some reason
		}
	}
	
	@Override
	public String toString(){
		return "Index : "+indexuri+" "+fetchStatus+" "+mainIndexDescription+" "+waitingOnMainIndex;
	}
}




