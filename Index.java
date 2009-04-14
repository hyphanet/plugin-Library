package plugins.XMLLibrarian;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

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
	}

	/**
	 * Fetch the main index file
	 * 
	 * @throws IOException
	 * @throws FetchException
	 * @throws SAXException
	 */ 
	public synchronized void fetch() throws IOException, FetchException, SAXException {
        fetch(new Progress(""));
    }
	public synchronized void fetch(Progress progress) throws IOException, FetchException, SAXException {
		if (fetched)
			return;

        progress.set("Getting base index");
		Bucket bucket = Util.fetchBucket(baseURI + DEFAULT_FILE, pr);
        progress.set("Fetched base index");
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

	protected String getSubIndex(String keyword) {
		String md5 = XMLLibrarian.MD5(keyword);
		int idx = Collections.binarySearch(subIndiceList, md5);
		if (idx < 0)
			idx = -idx - 2;
		return subIndice.get(subIndiceList.get(idx));
	}

	protected List<URIWrapper> search(String keyword) throws Exception {
        return search(keyword, new Progress(""));
    }

	protected List<URIWrapper> search(String keyword, Progress progress) throws Exception {
		List<URIWrapper> result = new LinkedList<URIWrapper>();
		String subIndex = getSubIndex(keyword);

		try {
            progress.set("Getting subindex "+subIndex+" to search for "+keyword);
			Bucket bucket = Util.fetchBucket(baseURI + subIndex, pr);
            progress.set("Fetched subindex "+subIndex+" to search for "+keyword);

			SAXParserFactory factory = SAXParserFactory.newInstance();
			try {
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				SAXParser saxParser = factory.newSAXParser();
				InputStream is = bucket.getInputStream();
				saxParser.parse(is, new LibrarianHandler(keyword, result));
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

	public List<URIWrapper> search(String[] keywords) throws Exception {
        return search(keywords, new Progress(""));
    }

	public List<URIWrapper> search(String[] keywords, Progress progress) throws Exception {
		List<URIWrapper> result = null;

		for (String keyword : keywords) {
			if (keyword.length() < 3)
				continue;
			List<URIWrapper> s = search(keyword, progress);

			if (result == null)
				result = s;
			else
				result.retainAll(s);
		}
		if (result == null)
			result = new LinkedList<URIWrapper>();
		return result;
	}
}
