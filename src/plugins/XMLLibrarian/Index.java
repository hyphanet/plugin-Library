package plugins.XMLLibrarian;


import java.util.ArrayList;
import java.util.HashMap;


import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;

/**
 * Index Class
 * 
 * 
 */
public class Index implements Status{
	static protected PluginRespirator pr;
	
	protected String indexuri;

	public enum FetchStatus{UNFETCHED, FETCHING, FETCHED, FAILED}
	protected FetchStatus fetchStatus = FetchStatus.UNFETCHED;
	protected ArrayList<Request> waitingOnMainIndex = new ArrayList<Request>();
	
	protected String mainIndexDescription;
	protected int version;
	protected ArrayList<Request> requests = new ArrayList<Request>();

	protected HashMap<String, String> indexMeta;
	
	//  Map of all indexes currently open
	protected static HashMap<String, Index> allindices = new HashMap<String, Index>();

	/**
	 * @param baseURI
	 *            Base URI of the index (exclude the <tt>index.xml</tt> part)
	 * @param pluginRespirator
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
	public static ArrayList<Index> getIndices(String indexuris) throws InvalidSearchException{
		String[] uris = indexuris.split(" ");
		ArrayList<Index> indices = new ArrayList<Index>(uris.length);
		
		for ( String uri : uris){
			indices.add(getIndex(uri));
		}
		return indices;
	}
	
	/**
	 * Returns an Index object for the uri specified,
	 * gets an existing one if its there else makes a new one
	 * 
	 * @param indexuri index specifier
	 * @return Index object
	 */
	public static Index getIndex(String indexuri) throws InvalidSearchException{
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

	
	public String getIndexURI(){
		return indexuri;
	}
	
	public Request find(String term) throws Exception{
		throw new UnsupportedOperationException("No find() method implemented in index "+this.toString()+" : "+indexuri);
	}
	
	/// allow many? is there an advantage?
	public Request getPage(String pageid){
		Request request = new Request(Request.RequestType.PAGE, pageid);
		return request;
	}

	/// GONe
	public void getPage(String pageid, Request request) {
		throw new UnsupportedOperationException("No getPage() method implemented in index "+this.getClass().getDeclaringClass().getName()+" : "+indexuri);
	}
	
	public static void setup(XMLLibrarian xl){
		pr = xl.getPluginRespirator();
	}
	
	public long getDownloadedBlocks(){
		return -1;
	}
	
	public String toString(){
		return "Index : "+indexuri+" "+fetchStatus+" "+mainIndexDescription+" "+waitingOnMainIndex;
	}
}




