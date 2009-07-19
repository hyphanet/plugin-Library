/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index.xml;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import freenet.support.Logger;

import java.util.HashSet;
import java.util.Iterator;
import plugins.Library.util.URIWrapper;

/**
 * Required for using SAX parser on XML indices
 * 
 * @author swati
 * 
 */
public class LibrarianHandler extends DefaultHandler {
	private boolean processingWord;

	/** file id -> uri */
	private HashMap<String, String> uris;
	/** file id -> title */
	private HashMap<String, String> titles;
	private List<FindRequest> requests;
	private List<FindRequest> wordMatches;
	private URIWrapper inFile;
	private StringBuilder characters;


	/**
	 * Construct a LibrarianHandler to look for many terms
	 * @param requests the requests wanting to be resolved by this LibrarianHandler, results are written back to them
	 * @throws java.lang.Exception
	 */
	public LibrarianHandler(List<FindRequest> requests) throws Exception {
		this.requests = new ArrayList(requests);
		for(FindRequest r : requests)
			r.setResult(new HashSet<URIWrapper>());
	}

	public void setDocumentLocator(Locator value) {

	}

	public void endDocument() throws SAXException {
	}

	public void startDocument() throws SAXException {
		if(uris==null || titles ==null){
			uris = new HashMap<String, String>();
			titles = new HashMap<String, String>();
		}
	}

	public void startElement(String nameSpaceURI, String localName, String rawName, Attributes attrs)
	        throws SAXException {
		if(requests.size()==0&&wordMatches.size()==0)
			return;
		if (rawName == null) {
			rawName = localName;
		}
		String elt_name = rawName;

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
				wordMatches = null;
				String match = attrs.getValue("v");
				if (requests!=null){
					wordMatches = new ArrayList<FindRequest>();
					for (Iterator<FindRequest> it = requests.iterator(); it.hasNext();) {
						FindRequest r = it.next();
						if (match.equals(r.getSubject())){
							wordMatches.add(r);
							it.remove();
							Logger.minor(this, "found word match "+wordMatches);
						}
					}
				}
			} catch (Exception e) {
				throw new SAXException(e);
			}
		}

		if (elt_name.equals("file")) {
			if (processingWord == true &&  wordMatches!=null) {
				try{
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
						//Logger.minor(this, "adding to all in "+wordMatches);
						for(FindRequest<URIWrapper> match : wordMatches){
							match.addResult(uri);
							// removed these as they use lots of memory and are only used for phrase search which doesnt work
							//inFile = uri;
							//characters = new StringBuilder();
							//Logger.minor(this, "added "+uri+ " to "+ match);
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
	
	public void characters(char[] ch, int start, int length) {
		if(characters!=null){
			characters.append(ch, start, length);
		}
	}

	public void endElement(String namespaceURI, String localName, String qName) {
		if(inFile!=null){
			String[] termposs = characters.toString().split(",");
			ArrayList<Integer> termpositions = new ArrayList();
			for (String pos : termposs) {
				try{
					termpositions.add(Integer.valueOf(pos));
				}catch(NumberFormatException e){
				}
			}
			inFile.termpositions = termpositions;
			inFile = null;
			characters = null;
		}
	}
}
