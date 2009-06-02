package plugins.XMLLibrarian;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.HashSet;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import freenet.client.FetchException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.api.Bucket;

/**
 * Index Class
 * 
 * This does _NOT_ support index file generate by XMLSpider earlier then version 8 (Sep-2007).
 * 
 * @author j16sdiz (1024D/75494252)
 */
public class Index {
	static final String DEFAULT_FILE = "index.xml";

	protected String baseURI;
	protected PluginRespirator pr;

	protected boolean fetched;
	/**
	 * Index format version:
	 * <ul>
	 * <li>1 = XMLSpider 8 to 33</li>
	 * </ul>
	 * Earlier format are no longer supported.
	 */
	protected int version;

	protected String title;
	protected String ownerName;
	protected String ownerEmail;

	protected List<String> subIndiceList;
	protected SortedMap<String, String> subIndice;
	//  Map of all indexes currently open
	private static HashMap<String, Index> allindices = new HashMap<String, Index>();

	/**
	 * @param baseURI
	 *            Base URI of the index (exclude the <tt>index.xml</tt> part)
	 * @param pluginRespirator
	 */
	public Index(String baseURI, PluginRespirator pluginRespirator) {
		if (!baseURI.endsWith("/"))
			baseURI += "/";

		this.baseURI = baseURI;
		this.pr = pluginRespirator;
		allindices.put(baseURI, this);
	}
	
	/**
	 * Returns a set of Index objects one for each of the uri's specified
	 * gets an existing one if its there else makes a new one
	 * 
	 * @param indexuris list of index specifiers separated by spaces
	 * @return Set of Index objects
	 */
	public static HashSet<Index> getIndices(String indexuris, PluginRespirator pr){
		String[] uris = indexuris.split(" ");
		HashSet<Index> indices = new HashSet<Index>(uris.length);

		for ( String uri : uris){
			if (allindices.containsKey(uri))
				indices.add(allindices.get(uri));
			else
				indices.add(new Index(uri, pr));
		}
		return indices;
	}

	/**
	 * Fetch the main index file
	 * 
	 * @throws IOException
	 * @throws FetchException
	 * @throws SAXException
	 */ 
	public synchronized void fetch() throws IOException, FetchException, SAXException {
        fetch(null);
    }
	public synchronized void fetch(Search search) throws IOException, FetchException, SAXException {
		if (fetched)
			return;

        if(search!=null) search.setprogress("Getting base index");
		Bucket bucket = Util.fetchBucket(baseURI + DEFAULT_FILE, search);
        if(search!=null) search.setprogress("Fetched base index");
		try {
			InputStream is = bucket.getInputStream();
			parse(is);
			is.close();
		} finally {
			bucket.free();
		}

		fetched = true;
	}

	private void parse(InputStream is) throws SAXException, IOException {
		SAXParserFactory factory = SAXParserFactory.newInstance();

		try {
			factory.setNamespaceAware(true);
			factory.setFeature("http://xml.org/sax/features/namespaces", true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

			SAXParser saxParser = factory.newSAXParser();
			MainIndexParser parser = new MainIndexParser();
			saxParser.parse(is, parser);

			version = parser.getVersion();
			if (version == 1) {
				title = parser.getHeader("title");
				ownerName = parser.getHeader("owner");
				ownerEmail = parser.getHeader("email");

				subIndiceList = new ArrayList<String>();
				subIndice = new TreeMap<String, String>();

				for (String key : parser.getSubIndice()) {
					subIndiceList.add(key);
					subIndice.put(key, "index_" + key + ".xml");
				}
				Collections.sort(subIndiceList);
			}
		} catch (ParserConfigurationException e) {
			Logger.error(this, "SAX ParserConfigurationException", e);
			throw new SAXException(e);
		}
	}
	
	public String getIndexURI(){
		return baseURI;
	}

	protected String getSubIndex(String keyword) {
		String md5 = XMLLibrarian.MD5(keyword);
		int idx = Collections.binarySearch(subIndiceList, md5);
		if (idx < 0)
			idx = -idx - 2;
		return subIndice.get(subIndiceList.get(idx));
	}


	public List<URIWrapper> search(Search search) throws Exception {
		fetch(search);
		List<URIWrapper> result = new LinkedList<URIWrapper>();
		String subIndex = getSubIndex(search.getQuery());
		
		// TODO make sure each subindex only gets fetched & parsed once
		try {
			search.setprogress("Getting subindex "+subIndex+" to search for "+search.getQuery());
			Bucket bucket = Util.fetchBucket(baseURI + subIndex, search);
			search.setprogress("Fetched subindex "+subIndex+" to search for "+search.getQuery());

			SAXParserFactory factory = SAXParserFactory.newInstance();
			try {
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				SAXParser saxParser = factory.newSAXParser();
				InputStream is = bucket.getInputStream();
				search.setprogress("Fetched subindex "+subIndex+" to search for "+search.getQuery());
				saxParser.parse(is, new LibrarianHandler(search.getQuery(), result));
				is.close();
			} catch (Throwable err) {
				err.printStackTrace();
				throw new Exception("Could not parse XML: " + err.toString());
			} finally {
				bucket.free();
			}
		} catch (Exception e) {
			Logger.error(this, baseURI + subIndex + " could not be opened: " + e.toString(), e);
			throw e;
		}
		return result;
	}

	/*public List<URIWrapper> search(String[] keywords) throws Exception {
        return search(keywords, null);
    }

	public List<URIWrapper> search(String[] keywords, Search search) throws Exception {
		List<URIWrapper> result = null;

		for (String keyword : keywords) {
			if (keyword.length() < 3)
				continue;
			List<URIWrapper> s = search(keyword, search);

			if (result == null)
				result = s;
			else
				result.retainAll(s);
		}
		if (result == null)
			result = new LinkedList<URIWrapper>();
		return result;
	}*/
}
