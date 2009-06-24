package plugins.XMLLibrarian.xmlindex;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import freenet.support.Logger;

import plugins.XMLLibrarian.URIWrapper;

/**
 * Required for using SAX parser on XML indices
 * 
 * @author swati
 * 
 */
public class LibrarianHandler extends DefaultHandler {
	private final String word;
	private final String md5;

	private int prefix;
	private boolean processingWord;
	private String prefixMatch;

	// now we need to adapt this to read subindexing 
	private boolean found_match;
	/** file id -> uri */
	private HashMap<String, String> uris;
	/** file id -> title */
	private HashMap<String, String> titles;
	private List<URIWrapper> fileuris;
	///private List<Integer> fileids;
	private List<FindRequest> requests;
	private List<FindRequest> wordMatches;


	/**
	 * Construct a LibrarianHandler to look for many terms
	 * @param requests the requests wanting to be resolved by this LibrarianHandler, results are written back to them
	 * @throws java.lang.Exception
	 */
	public LibrarianHandler(List<FindRequest> requests) throws Exception {
		this.requests = requests;
		for(FindRequest r : requests)
			r.setResult(new ArrayList<URIWrapper>());
		word = "";
		md5="";
	}

	public void setDocumentLocator(Locator value) {

	}

	public void endDocument() throws SAXException {
	}

	public void startDocument() throws SAXException {
		found_match = false;
		if(uris==null || titles ==null){
			uris = new HashMap<String, String>();
			titles = new HashMap<String, String>();
		}
	}

	public void startElement(String nameSpaceURI, String localName, String rawName, Attributes attrs)
	        throws SAXException {
		if (rawName == null) {
			rawName = localName;
		}
		String elt_name = rawName;
		/*
		 * Gives the maximum number of digits of md5 used for creating subindices
		 */
		if (elt_name.equals("prefix")) {
			prefix = Integer.parseInt(attrs.getValue("value"));
		}

		if (elt_name.equals("files"))
			processingWord = false;
		if (elt_name.equals("keywords"))
			processingWord = true;
		/*
		 * looks for the word in the given subindex file if the word is found then the parser
		 * fetches the corresponding fileElements
		 */
		if (elt_name.equals("word")) {
			try {
				found_match = false;
				wordMatches = null;
				String match = attrs.getValue("v");
				if (match.equals(word))
					found_match = true;
				if (requests!=null){
					wordMatches = new ArrayList<FindRequest>();
					for (FindRequest r : requests){
						//System.out.println("comparing "+r.getSubject()+" with "+match);
						if (match.equals(r.getSubject())){
							wordMatches.add(r);
						}
					}
				}
			} catch (Exception e) {
				Logger.error(this, "word key doesn't match" + e.toString(), e);
			}
		}

		if (elt_name.equals("file")) {
			if (processingWord == true && (found_match == true || wordMatches!=null)) {
				try{
					if(fileuris!=null){
						URIWrapper uri = new URIWrapper();
						uri.URI = uris.get(attrs.getValue("id"));
						synchronized(this){
							if(titles.containsKey(attrs.getValue("id")))
							{
								uri.descr = titles.get(attrs.getValue("id"));
								if ((uri.URI).equals(uri.descr))
									uri.descr = "not available";
							}
							else
								uri.descr = "not available";

							if (fileuris != null)
								fileuris.add(uri);
						}
					}else{
						URIWrapper uri = new URIWrapper();
						uri.URI = uris.get(attrs.getValue("id"));
						synchronized(this){
							if(titles.containsKey(attrs.getValue("id")))
							{
								uri.descr = titles.get(attrs.getValue("id"));
								if ((uri.URI).equals(uri.descr))
									uri.descr = "not available";
							}
							else
								uri.descr = "not available";
							for(FindRequest<ArrayList<URIWrapper>> match : wordMatches){
								match.getResult().add(uri);
							}
						}
					}
				}
				catch (Exception e) {
					Logger.error(this, "Index format may be outdated " + e.toString(), e);
				}

			} else if (processingWord == false) {
				try {
					String id = attrs.getValue("id");
					String key = attrs.getValue("key");
					int l = attrs.getLength();
					String title;
					synchronized (this) {
						if (l >= 3) {
							try {
								title = attrs.getValue("title");
								titles.put(id, title);
							} catch (Exception e) {
								Logger.error(this, "Index Format not compatible " + e.toString(), e);
							}
						}

						uris.put(id, key);
					}
				}
				catch (Exception e) {
					Logger.error(this, "File id and key could not be retrieved. May be due to format clash", e);
				}
			}
		}
	}

	public String getPrefixMatch() {
		return prefixMatch;
	}

}
