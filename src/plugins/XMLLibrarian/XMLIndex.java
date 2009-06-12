package plugins.XMLLibrarian;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import freenet.pluginmanager.PluginRespirator;
import freenet.client.FetchException;
import freenet.support.Logger;
import freenet.support.api.Bucket;

import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.SortedMap;



class XMLIndex extends Index{
	static final String DEFAULT_FILE = "index.xml";

	protected boolean fetched;
	/**
	 * Index format version:
	 * <ul>
	 * <li>1 = XMLSpider 8 to 33</li>
	 * </ul>
	 * Earlier format are no longer supported.
	 */
	protected int version;

	protected List<String> subIndiceList;
	protected SortedMap<String, String> subIndice;

	/**
	 * @param baseURI
	 *            Base URI of the index (exclude the <tt>index.xml</tt> part)
	 */
	protected XMLIndex(String baseURI) throws InvalidSearchException {
		super();
		if (!baseURI.endsWith("/"))
			baseURI += "/";
		indexuri = baseURI;
			
		// check if index is valid file or URI
		if(!Util.isValid(baseURI))
			throw new InvalidSearchException(baseURI + " is neither a valid file nor valid Freenet URI");

		allindices.put(baseURI, this);
	}

	/**
	 * Fetch the main index file
	 * 
	 * @throws IOException
	 * @throws FetchException
	 * @throws SAXException
	 */ 
	private synchronized void fetch() throws IOException, FetchException, SAXException {
        fetch(null);
    }
	private synchronized void fetch(Search search) throws IOException, FetchException, SAXException {
		if (fetched)
			return;

        if(search!=null) search.setprogress("Getting base index");
		Bucket bucket = Util.fetchBucket(indexuri + DEFAULT_FILE, search);
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
				indexMeta.put("title", parser.getHeader("title"));
				indexMeta.put("ownerName", parser.getHeader("owner"));
				indexMeta.put("ownerEmail", parser.getHeader("email"));

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
			Bucket bucket = Util.fetchBucket(indexuri + subIndex, search);
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
			Logger.error(this, indexuri + subIndex + " could not be opened: " + e.toString(), e);
			throw e;
		}
		return result;
	}
}
