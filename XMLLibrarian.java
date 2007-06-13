package plugins.Librarian;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.filter.CommentException;
import freenet.clients.http.filter.FilterCallback;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLEncoder;
import freenet.support.api.HTTPRequest;

public class XMLLibrarian implements FredPlugin, FredPluginHTTP, FredPluginThreadless {
	
	
//the default index site has to be set as the SSK key of the index site 
//the Librarian would later be modified to take the site value from the interface
//this Librarian assumes that the index to be used is present at DEFAULT_INDEX_SITE/index.xml

	private  final String DEFAULT_INDEX_SITE="SSK@BdtiukemDVmUu-Ds8va48bnalaPv2Kc-FAXCgW2fULY,YOeF82YDzFhp2A5ChKeyd2AKHbs~mQTXHRdM3ur-Vuo,AQACAAE/testsite/";
	private  final String DEFAULT_INDEX_URI = DEFAULT_INDEX_SITE+"index.xml";
	private static final String DEFAULT_FILE = "index.xml";
	boolean goon = true;
	Random rnd = new Random();
	PluginRespirator pr;
	private static final String plugName = "Librarian";
	private String word ;
	private boolean processingWord ;
	private URIWrapper uriw;
	private Vector uris;
	private Vector keyuris;
	private Vector fileuris;
	private HashMap keywords;
	private FileWriter output;
	public void terminate() {
		goon = false;
	}
	
	private String getArrayElement(String[] array, int element) {
		try {
			return array[element];
		} catch (Exception e) {
			//e.printStackTrace();
			return "";
		}
	}
	public String handleHTTPPut(HTTPRequest request) throws PluginHTTPException {
		return null;
	}
	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		return null;
	}
	
	private HashMap getElements(String path) {
		String[] getelements = getArrayElement(path.split("\\?"),1).split("\\&");
		HashMap ret = new HashMap();
		for (int i = 0; i < getelements.length ; i++) {
			int eqpos = getelements[i].indexOf("="); 
			if (eqpos < 1)
				// Unhandled so far
				continue;
			
			String key = getelements[i].substring(0, eqpos);
			String value = getelements[i].substring(eqpos + 1);

			ret.put(key, value);
			/*if (getelements[i].startsWith("page="))
				page = Integer.parseInt(getelements[i].substring("page=".length()));
				*/
		}
		return ret;
	}
	
	private void appendDefaultPageStart(StringBuffer out, String stylesheet) {
		out.append("<HTML><HEAD><TITLE>" + plugName + "</TITLE>");
		if(stylesheet != null)
			out.append("<link href=\""+stylesheet+"\" type=\"text/css\" rel=\"stylesheet\" />");
		out.append("</HEAD><BODY>\n");
		out.append("<CENTER><H1>" + plugName + "</H1><BR/><BR/><BR/>\n");
	}

	private void appendDefaultPageEnd(StringBuffer out) {
		out.append("</CENTER></BODY></HTML>");
	}
	
	private void appendDefaultPostFields(StringBuffer out) {
		appendDefaultPostFields(out, "", "");
	}
	
	private void appendDefaultPostFields(StringBuffer out, String search, String index) {
		search = HTMLEncoder.encode(search);
		index = HTMLEncoder.encode(index);
		out.append("Search for:<br/>");
        out.append("<form method=\"GET\"><input type=\"text\" value=\"").append(search).append("\" name=\"search\" size=80/><br/><br/>");
		out.append("Using the index:<br/>");
        out.append("<input type=text name=\"index\" value=\"").append(index).append("\" size=80/>");
		out.append("<input type=submit value=\"Find!\"/></form>\n");
		// index - key to index
		// search - text to search for
	}
	
	private HashMap getFullIndex(String uri) throws Exception {
		
		//this will return a hashmap consisting of the file uri that have the keywords 
		// has to be modified to include the file names in the search
		keywords = new HashMap();
		word = new String();
		HighLevelSimpleClient hlsc = pr.getHLSimpleClient();
		FreenetURI u = new FreenetURI(uri);
		FetchResult res;
		while(true) {
			try {
				res = hlsc.fetch(u);
				break;
			} catch (FetchException e) {
				if(e.newURI != null) {
					u = e.newURI;
					continue;
				} else throw e;
			}
		}
		output = new FileWriter("logfile");
		output.write("testing ");
		String index[] = new String(res.asByteArray()).trim().split("\n");
		//this index is still not recognisable as xml file...so it would be better if we read it as xml....for this all the statements are added to an xml file and then 
		// that file is read
		
		FileWriter out = new FileWriter(DEFAULT_FILE);
		for(int j=0;j<index.length;j++)
		out.write(index[j].toString() + "\n");
		out.close();
		// the file should be done by this
		
		//now we need to parse the xml file and see if we can get the uris with the requied ids
		// we need to use the xml parser
		SAXParserFactory factory = SAXParserFactory.newInstance();
		
		try {

	      //  OutputStreamWriter output = new OutputStreamWriter (System.out, "UTF8");
	        SAXParser saxParser = factory.newSAXParser();
	        saxParser.parse( new File(DEFAULT_FILE), new LibrarianHandler() );

	  } catch (Throwable err) {
	        err.printStackTrace ();
	  }
	
	  return keywords;

	}
	
	private void fetch(String str) throws Exception{
		String uri = DEFAULT_INDEX_SITE + str;
		HighLevelSimpleClient hlsc = pr.getHLSimpleClient();
		FreenetURI u = new FreenetURI(uri);
		FetchResult res;
		while(true) {
			try {
				res = hlsc.fetch(u);
				break;
			} catch (FetchException e) {
				if(e.newURI != null) {
					u = e.newURI;
					continue;
				} else throw e;
			}
		}
		
		String index[] = new String(res.asByteArray()).trim().split("\n");
		
		
		FileWriter out = new FileWriter(str);
		for(int j=0;j<index.length;j++)
		out.write(index[j].toString() + "\n");
		out.close();
	}
	
	
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		StringBuffer out = new StringBuffer();

		//int page = request.getIntParam("page", 1);
		String indexuri = request.getParam("index", DEFAULT_INDEX_URI);
		String search = request.getParam("search");
		String stylesheet = request.getParam("stylesheet", null);
		if(stylesheet != null) {
			FilterCallback cb = pr.makeFilterCallback(request.getPath());
			try {
				stylesheet = cb.processURI(stylesheet, "text/css");
			} catch (CommentException e) {
				return "Invalid stylesheet: "+e.getMessage();
			}
		}
		
		if (search.equals("")) {
			appendDefaultPageStart(out, stylesheet);
			//appendDefaultPostFields(out);
			appendDefaultPostFields(out, search, indexuri);
			appendDefaultPageEnd(out);
			return out.toString();
		}
		
		try {
			
			appendDefaultPageStart(out, stylesheet);
			appendDefaultPostFields(out, search, indexuri);

			out.append("<p><span class=\"librarian-searching-for-header\">Searching for yoohoo: </span><span class=\"librarian-searching-for-target\">").append(HTMLEncoder.encode(search)).append("</span></p>\n");
						// Get search result
			String searchWords[] = search.split(" ");
			
			// Return results in order.
			LinkedHashSet hs = new LinkedHashSet();
			//hs will contain the final results
			//first we will check for the first word
			Vector keyuris;
			for(int i = 0;i<searchWords.length;i++){
				keyuris = getIndex(searchWords[i]);
				if(i == 0){
					synchronized(hs){
					hs.clear();
				if (keyuris != null) {
				Iterator it = keyuris.iterator();
				while (it.hasNext())
				{
				hs.add(it.next());	
				}
			}
				}
				}
				else{
					try{
					
					synchronized(hs){
					if(keyuris.size() > 0){
					
					Iterator it = hs.iterator();
					while(it.hasNext())
					{
						URIWrapper uri = (URIWrapper) it.next();
						//output.write("\nhs values "+uri.URI);
						if(!((uri.URI).equals(((URIWrapper) (keyuris.elementAt(0))).URI))) it.remove();
					}
					
					}
					if(keyuris.size() == 0) hs.clear();}
					//output.close();
					}
					catch(Exception e){
						e.getMessage();
						
						
					}
					
				}
				
					
				}
			// Output results
			int results = 0;
			out.append("<table class=\"librarian-results\"><tr>\n");
			Iterator it = hs.iterator();
			while (it.hasNext()) {
				
				URIWrapper o = (URIWrapper)it.next();
				String showurl = o.URI;
				
				String description = HTMLEncoder.encode(o.descr);
				if(!description.equals("not available")){
					description=description.replaceAll("(\n|&lt;(b|B)(r|R)&gt;)", "<br>");
					description=description.replaceAll("  ", "&nbsp; ");
					description=description.replaceAll("&lt;/?[a-zA-Z].*/?&gt;", "");	
				}
				
				showurl = HTMLEncoder.encode(showurl);
				if (showurl.length() > 60)
					showurl = showurl.substring(0,15) + "&hellip;" + 
					    showurl.replaceFirst("[^/]*/", "/");
				String realurl = (o.URI.startsWith("/")?"":"/") + o.URI;
				realurl = HTMLEncoder.encode(realurl);
				out.append("<p>\n<table class=\"librarian-result\" width=\"100%\" border=1><tr><td align=center bgcolor=\"#D0D0D0\" class=\"librarian-result-url\">\n");
				out.append("  <A HREF=\"").append(realurl).append("\" title=\"").append(o.URI).append("\">").append(showurl).append("</A>\n");
				out.append("</td></tr><tr><td align=left class=\"librarian-result-summary\">\n");
				//out.append("<tt>").append(description).append("</tt>\n");
				out.append("</td></tr></table>\n");
				results++;
			}
			out.append("</tr><table>\n");
            out.append("<p><span class=\"librarian-summary-found-text\">Found: </span><span class=\"librarian-summary-found-number\">").append(results).append(" results</span></p>\n");
			

			appendDefaultPageEnd(out);
			
			return out.toString();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return e.toString();
		}
	}
	private Vector getIndex(String word) throws Exception{
		fetch(DEFAULT_FILE);
	
		String subIndex = searchStr(word);
	
		
		fetch("index_"+subIndex+".xml");
		Vector index = new Vector();
		index = getEntry(word,subIndex);
		return index;
	}
	private String searchStr(String word) throws Exception{
		// in this we will search for the md5 in index.xml 
		//this should be same as that in XML Spider
	
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(DEFAULT_FILE);
		Element root = doc.getDocumentElement();
		Attr prefix_value = (Attr) (root.getElementsByTagName("prefix").item(0)).getAttributes().getNamedItem("value");
		int prefix = Integer.parseInt(prefix_value.getValue()); 
		//Element prefixNode = (Element)root.getFirstChild();
		String md5 = MD5(word);
		NodeList subindexList = root.getElementsByTagName("subIndex");
		String str = md5.substring(0,prefix);		
   	    String prefix_match = search(str,subindexList);
		return prefix_match;
		
	}
	public String search(String str,NodeList list) throws Exception
	{
		int prefix = str.length();
		for(int i = 0;i<list.getLength();i++){
			Element subIndex = (Element) list.item(i);
			String key = subIndex.getAttribute("key");
			if(key.equals(str)) return key;
		}
		
		return search(str.substring(0, prefix-1),list);
	}
	private Vector getEntry(String word,String subIndex)throws Exception{
		//search for the word in the given subIndex
		fileuris = new Vector();
		
			
		//now the xml file is created and we need to look for the word
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		Document doc = docBuilder.parse("index_"+subIndex+".xml");
		Element root = doc.getDocumentElement();
		NodeList wordList = root.getElementsByTagName("word");
		for(int i = 0;i<wordList.getLength();i++)
		{
			Element wordElt = (Element)wordList.item(i);
			String key = wordElt.getAttribute("v");
			
			if(key.equals(word)) 
				{
			
				NodeList fileList = wordElt.getElementsByTagName("file");
				
				
				
				for(int j =0;j<fileList.getLength();j++){
					Element file = (Element) fileList.item(j);
					
					//Attr id = (Attr) file.getAttributes().getNamedItem("id");
					URIWrapper uri = new URIWrapper();
					String id = file.getAttribute("id");
					//reference this id from index.xml and get the file
					uri.URI = getURI(id);
				
					uri.descr = "not available";
					fileuris.add(uri);
				}
				break;
				}
		}
		
		return fileuris;
	}
	public String getURI(String id) throws Exception
	{
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(DEFAULT_FILE);
		Element root = doc.getDocumentElement();
		NodeList fileList = root.getElementsByTagName("file");
		for(int i = 0;i<fileList.getLength();i++){
			Element file = (Element) fileList.item(i);
			String fileId = file.getAttribute("id");
			if(fileId.equals(id)) return file.getAttribute("key");
			}
		return "not available";
	}
	public void runPlugin(PluginRespirator pr) {
		this.pr = pr;
		
	}
	
	private class URIWrapper implements Comparable {
		public String URI;
		public String descr;
		
		public int compareTo(Object o) {
			if (o instanceof URIWrapper)
				return URI.compareTo(((URIWrapper)o).URI);
			return -1;
		}
	}


	public class LibrarianHandler extends DefaultHandler {
		// now we need to adapt this to read subindexing 
		private Locator locator = null;
		public LibrarianHandler() throws Exception{
			processingWord = false;
			uriw = new URIWrapper();
			uris = new Vector();
			
		}
		public void setDocumentLocator(Locator value) {
			locator =  value;
		}
		public void endDocument()  throws SAXException
		{
		if(!word.equals(""))
			keywords.put(word, keyuris);
		
		}
	   
	    public void startElement(String nameSpaceURI, String localName,
				 String rawName, Attributes attrs) throws SAXException {
	    
	    	if (rawName == null) {
				rawName = localName;
			}
	    	
	    	String elt_name = rawName;
	    	if(elt_name.equals("word"))
	    	{
	    	try
	    	{
	    		if(!word.equals(""))
	    		{
	    			keywords.put(word, keyuris);
	    			word = new String();
	    			
	    		}
	    	 word = attrs.getValue("v");
	    	     
	    			 
	    	 }
	    	 catch (Exception e)
	    	 {
	    		 
	    	 }
	    	 
	    	 processingWord = true;
	    	 keyuris = new Vector();	
	    	}
	    	
	    	if(elt_name.equals("file"))
	    	{
	    		if(!processingWord)
	    		{
					uriw = new URIWrapper();
					uriw.URI = attrs.getValue("key");
					uriw.descr = "not available";
					
					uris.add(uriw);
	     		}
	    		else
	    		{	    				    		
				    int uriNumber = Integer.parseInt(attrs.getValue("id").toString());
					URIWrapper uw = (URIWrapper) uris.get(uriNumber);
					
					if(!keyuris.contains(uw))
						keyuris.add(uw);
	    			}
	     	}
	    		    	
	    }
	    
	  	
		
	}
	private static String convertToHex(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
        	int halfbyte = (data[i] >>> 4) & 0x0F;
        	int two_halfs = 0;
        	do {
	        	if ((0 <= halfbyte) && (halfbyte <= 9))
	                buf.append((char) ('0' + halfbyte));
	            else
	            	buf.append((char) ('a' + (halfbyte - 10)));
	        	halfbyte = data[i] & 0x0F;
        	} while(two_halfs++ < 1);
        }
        return buf.toString();
    }
	//this function will return the String representation of the MD5 hash for the input string 
	public static String MD5(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException  {
		MessageDigest md;
		md = MessageDigest.getInstance("MD5");
		byte[] md5hash = new byte[32];
		md.update(text.getBytes("iso-8859-1"), 0, text.length());
		md5hash = md.digest();
		return convertToHex(md5hash);
	}
}
