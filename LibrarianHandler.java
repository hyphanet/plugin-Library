package plugins.XMLLibrarian;

import java.util.HashMap;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import freenet.support.Logger;

/**
	 * Required for using SAX parser on XML indices
	 * @author swati
	 *
	 */
	public class LibrarianHandler extends DefaultHandler {
		private String word;
		private int prefix;
		private boolean processingWord;
		private String prefixMatch;
		
		// now we need to adapt this to read subindexing 
		private boolean found_match ;
		/** file id -> uri */
		private HashMap<String, String> uris;
		/** file id -> title */
		private HashMap<String, String> titles;
		private List<URIWrapper> fileuris;

		public LibrarianHandler(String word, List<URIWrapper> fileuris) throws Exception {
			this.fileuris = fileuris;
		}
		public void setDocumentLocator(Locator value) {

		}
		public void endDocument()  throws SAXException{}

		public void startDocument () throws SAXException
		{
			found_match = false;
			uris = new HashMap<String, String>();
			titles = new HashMap<String, String>();
		}
		public void startElement(String nameSpaceURI, String localName, String rawName, Attributes attrs) throws SAXException {
			if (rawName == null) {
				rawName = localName;
			}
			String elt_name = rawName;
			/*
			 * Gives the maximum number of digits of md5 used for creating subindices
			 */
			if(elt_name.equals("prefix")){
				prefix = Integer.parseInt(attrs.getValue("value"));
			}
			if(elt_name.equals("subIndex")){
				try{
					String md5 = XMLLibrarian.MD5(word);
					//here we need to match and see if any of the subindices match the required substring of the word.
					for(int i=0;i<prefix;i++){
						if((md5.substring(0,prefix-i)).equals(attrs.getValue("key"))){ 
							prefixMatch = md5.substring(0, prefix - i);
						Logger.normal(this, "match found " + prefixMatch);
						Logger.minor(this, "word searched = " + word + " prefix matcheed = " + getPrefixMatch());
							break;
						}
					}
				}
				catch(Exception e){Logger.error(this, "MD5 of the word"+word+"could not be calculated "+e.toString(), e);}
			}
			
			if(elt_name.equals("files")) processingWord = false;
			if(elt_name.equals("keywords")) processingWord = true;
			/*
			 * looks for the word in the given subindex file
			 * if the word is found then the parser fetches the corresponding fileElements 
			 */
			if(elt_name.equals("word")){
				try{
					found_match = false;
					String match = attrs.getValue("v");
					if(match.equals(word)) found_match = true;
					//if((attrs.getValue("v")).equals(word)) found_match = true;
					Logger.minor(this, "word searched = "+word+" matched");
				}catch(Exception e){Logger.error(this, "word key doesn't match"+e.toString(), e); }
			}

			if(elt_name.equals("file")){
//				try{
//					FileWriter outp = new FileWriter("logfile",true);
//				outp.write("word searched = "+word+" found_match = "+found_match+" processingWord "+processingWord+" \n");
//				outp.close();
//				}
//				catch(Exception e){
					
//				}
				if(processingWord == true && found_match == true){
					URIWrapper uri = new URIWrapper();
					try{
						uri.URI = uris.get(attrs.getValue("id"));
						Logger.minor(this, "word searched = "+word+" file id = "+uri.URI);
					//uri.descr = "not available";
					synchronized(this){
						if(titles.containsKey(attrs.getValue("id")))
						{
						uri.descr = titles.get(attrs.getValue("id"));
						if ((uri.URI).equals(uri.descr)) uri.descr = "not available";
						}
					else uri.descr = "not available";
					
						if (fileuris != null)
							fileuris.add(uri);
					}
					}
					catch(Exception e){
						Logger.error(this, "Index format may be outdated "+e.toString(), e);
					}
					
				}
				else if(processingWord == false){
					try{
						String id = attrs.getValue("id");
						String key = attrs.getValue("key");
						int l = attrs.getLength();
						String title;
						synchronized(this){
							if (l>=3 )
							{
								try{
									title = attrs.getValue("title");
//									FileWriter outp = new FileWriter("logfile",true);
//									outp.write("found title "+title+" == \n");
//									outp.close();
									titles.put(id,title);
								}
								catch(Exception e){
									Logger.error(this, "Index Format not compatible "+e.toString(), e);
								}
							}

							uris.put(id,key);
						}
						//String[] words = (String[]) uris.values().toArray(new String[uris.size()]);
					}
					catch(Exception e){Logger.error(this,"File id and key could not be retrieved. May be due to format clash",e);}
				}
			}
		}
		
		public String getPrefixMatch() {
		return prefixMatch;
		}

	}