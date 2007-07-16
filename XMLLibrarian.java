package plugins.XMLLibrarian;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
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
	
	private  String DEFAULT_INDEX_SITE="SSK@0yc3irwbhLYU1j3MdzGuwC6y1KboBHJ~1zIi8AN2XC0,5j9hrd2LLcew6ieoX1yC-hXRueSKziKYnRaD~aLnAYE,AQACAAE/testsite/";

	private  final String DEFAULT_INDEX_URI = DEFAULT_INDEX_SITE+"index.xml";
	private  String DEFAULT_FILE = "index.xml";
	boolean goon = true;
	Random rnd = new Random();
	PluginRespirator pr;
	private static final String plugName = "Librarian";
	private String word ;
	private boolean processingWord ;
	private boolean found_match ;
	private URIWrapper uriw;
	private HashMap uris;
	private Vector keyuris;
	private Vector fileuris;
	private HashMap keywords;
	private FileWriter output;
	private String prefix_match;
	private int prefix;
	/**
	 * indexList contains the index folders 
	 * each folder has a name and a list of indices added to that folder
	 */
	private HashMap indexList = new HashMap();
	
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
		out.append("Search in the index or folder:<br/>");
        out.append("<form method=\"GET\"><input type=\"text\" value=\"").append(search).append("\" name=\"search\" size=80/><br/><br/>");
        out.append("<input type=\"radio\" name=\"choice\" value=\"index\">Index<br/>");
        out.append("Using the index site(remember to give the site without index.xml):<br/>");
        out.append("<input type=\"text\" name=\"index\" value=\"").append(index).append("\" size=80/><br/>");
     
        out.append("<select name=\"folderList\">");
   
    // indexList.put("word", "dkfhdj");
    // indexList.put("try2", "value2");
  
     String[] words = (String[]) indexList.keySet().toArray(new String[indexList.size()]);
     
      for(int i =0;i<words.length;i++)
      {
    	  out.append("<option value=\"").append(words[i]).append("\">").append(words[i]).append("</option");
      }
        out.append("<br/><input type=\"radio\" name=\"choice\" value=\"folder\"> Folder<br/>");
        out.append("<input type=submit name=\"addToFolder\" value=\"Add to folder\" />");
        out.append("<input type=submit name=\"newFolder\" value=\"New Folder\" />");
//        out.append("<select name=\"folderList\">");
//   
//   //  indexList.put(word, "dkfhdj");
//  
//      words = (String[]) indexList.keySet().toArray(new String[indexList.size()]);
//     
//      for(int i =0;i<words.length;i++)
//      {
//    	  out.append("<option value=\"").append(words[i]).append("\">").append(words[i]).append("</option");
//      }
      	out.append("<input type=submit name=\"List\" value=\"List\" />");
      	out.append("<input type=submit name=\"help\" value=\"Help!\" />");
      	out.append("<input type=submit name = \"find\" value=\"Find!\"/>\n");
      	 out.append("<form><input type=\"file\" name=\"datafile\" /> ");
		out.append("<input type=submit name=\"Import\" value=\"Import From File\"/> ");
		out.append("<form><input type=\"text\" name=\"datafile2\" /> ");
		out.append("<input type=submit name=\"Export\" value=\"Export To File\"/> </form>");
		// index - key to index
		// search - text to search for
	}
	
	
	
	private void fetch(String str) throws Exception{
		FileWriter outp = new FileWriter("loG_fetch",true);
		String uri = DEFAULT_INDEX_SITE + str;
		outp.write(uri);
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
		outp.write("created "+str);
		outp.close();
	}
	
	
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		StringBuffer out = new StringBuffer();
		
		String search = request.getParam("search");
		String stylesheet = request.getParam("stylesheet", null);
		//int page = request.getIntParam("page", 1);
		
		String choice = request.getParam("choice");
		if(stylesheet != null) {
			FilterCallback cb = pr.makeFilterCallback(request.getPath());
			try {
				stylesheet = cb.processURI(stylesheet, "text/css");
			} catch (CommentException e) {
				return "Invalid stylesheet: "+e.getMessage();
			}
		}
		String indexuri = request.getParam("index", DEFAULT_INDEX_SITE);
		 appendDefaultPageStart(out, stylesheet);
			//appendDefaultPostFields(out);
		appendDefaultPostFields(out, search, indexuri);
		appendDefaultPageEnd(out);
		if(choice.equals("folder")){
				 if((request.getParam("find")).equals("Find!"))
				 {
					 String folder = request.getParam("folderList");
					 String[] indices = (String [])indexList.get(folder);
					 for(int i =0;i<indices.length;i++)
						 searchStr(out,search,indices[i],stylesheet);
				 }
			// return out.toString();
		}
		 else if((request.getParam("newFolder")).equals("New Folder")){
				
				// String[] indices = new String[];
				
				 out.append("<p>Name of the new Folder<br/>");
				 out.append("<form><input type=\"text\" name=\"newfolder\" size=80/> ");
				 out.append("<input type=submit value=\"Add\" name=\"addNew\" />");
			 }	 
		else if((request.getParam("addNew")).equals("Add"))
		 {
		 String newFolder = request.getParam("newfolder");
		// out.append("<p>new foler to be added "+newFolder+"</p>");
		 indexList.put(newFolder, new String[]{new String()});
		 out.append("New folder "+newFolder+" added. Kindly refresh the page<br/> ");
//			appendDefaultPageStart(out, stylesheet);
//			//appendDefaultPostFields(out);
//			appendDefaultPostFields(out, search, indexuri);
//			appendDefaultPageEnd(out);
		 return out.toString();
		 }
		else if((request.getParam("help")).equals("Help!"))
		 {
			out.append("<h3>Find</h3>");
			out.append("<p>Search for the queried word in either an index site or a selected folder of indices <br/>");
			out.append("If searching in a folder of indices, select the appropriate folder and check the button for folder<br/>");
			out.append("<h3>Add to folder</h3>");
			out.append("<p>Add the current index site to selected folder<br/>");
			out.append("<h3>New folder</h3>");
			out.append("<p>Create a new folder. To see the added folder refresh the page<br/>");
			out.append("<h3>List</h3>");
			out.append("<p>List the indices in the current folder<br/>");
			
		 }
		 else if((request.getParam("addToFolder")).equals("Add to folder"))
		 {
			 String folder = request.getParam("folderList");
			 indexuri = request.getParam("index",DEFAULT_INDEX_SITE);
			 String[] old = (String []) indexList.get(folder);
			 
			 String firstIndex = old[0]; 
			 String[] indices;
			 if(firstIndex == null || firstIndex.length() == 0)
			 {
				 indices = new String[]{indexuri};
			 }
			 else{
				 indices = new String[old.length+1];
			 System.arraycopy(old, 0, indices, 0, old.length);
			 //out.append("length of new "+indices.length);
			 indices[old.length] = indexuri;}
			// out.append("length of new "+indices.length);
			 out.append("index site "+indexuri+" added to "+folder);
			 indexList.put(folder, indices);
			 
		 }
		 else if((request.getParam("List")).equals("List"))
		 {
			 String folder = request.getParam("folderList");
			 String[] indices = (String[]) indexList.get(folder);
			 for(int i = 0;i<indices.length;i++)
			 {
			 out.append("<p>\n<table class=\"librarian-result\" width=\"100%\" border=1><tr><td align=center bgcolor=\"#D0D0D0\" class=\"librarian-result-url\">\n");
			 out.append("  <A HREF=\"").append(indices[i]).append("\">").append(indices[i]).append("</A>");
			 out.append("</td></tr><tr><td align=left class=\"librarian-result-summary\">\n");
			//out.append("<tt>").append(description).append("</tt>\n");
			out.append("</td></tr></table>\n");
			 }
			 
		 }
//		 else if((request.getParam("import")).equals("Import from File")){
//		//	 String folder = request.getParam("folderList");
//			 out.append("<form><input type=\"file\" name=\"datafile\" /> ");
//			 out.append("<input type=submit name=\"Import\" value=\"Import\" /></form>");
//		 }
//		 else if((request.getParam("export")).equals("Export to File")){
////			// String folder = request.getParam("folderList");
////			 out.append("Export Folder :<select name=\"exportFolder\">");
////			   
////			    // indexList.put("word", "dkfhdj");
////			    // indexList.put("try2", "value2");
////			  
////			     String[] words = (String[]) indexList.keySet().toArray(new String[indexList.size()]);
////			     
////			      for(int i =0;i<words.length;i++)
////			      {
////			    	  out.append("<option value=\"").append(words[i]).append("\">").append(words[i]).append("</option");
////			      }
////			 out.append("<form><input type=\"text\" name=\"datafile2\" /> ");
////			 out.append("<input type=submit name=\"Export\" value=\"Export\" /></form>");
//		 }
		else if(choice.equals("index"))
		{
			searchStr(out,search,indexuri,stylesheet);
		//	return out.toString();
		}
		else if((request.getParam("Import")).equals("Import From File")){
			String folder = request.getParam("folderList");
			String file = request.getParam("datafile");
			Vector indices=new Vector();
			try{
			BufferedReader inp = new BufferedReader(new FileReader(file));
			String index = inp.readLine();
			if(index != null) indices.add(index);
			
			while(index != null){
				indices.add(index);
				out.append("index :"+index);
				index = inp.readLine();
				
			}
			String[] old = (String []) indexList.get(folder);
			String[] finalIndex = new String[old.length + indices.size()];
			System.arraycopy(old, 0, finalIndex, 0, old.length);
			for(int i = 0;i<indices.size();i++){
				finalIndex[old.length + i] = (String) indices.elementAt(i);
			}
			//System.arraycopy(indices, 0, finalIndex, old.length, indices.length);
//			indices = new String[old.length+1];
//			 System.arraycopy(old, 0, indices, 0, old.length);
//			 //out.append("length of new "+indices.length);
//			 indices[old.length] = indexuri;}
//			// out.append("length of new "+indices.length);
//			 out.append("index site "+indexuri+" added to "+folder);
			 indexList.put(folder, finalIndex);
			inp.close();
			}
			catch(Exception e){
				
			}
		}
		else if((request.getParam("Export")).equals("Export To File")){
			  
			String folder = request.getParam("folderList");
			String file = request.getParam("datafile2");
			try{
			FileWriter outp = new FileWriter(file,true);
			
			String[] indices = ((String [])indexList.get(folder));
			for(int i = 0;i<indices.length;i++){
				outp.write(indices[i]+"\n");
			}
			outp.close();
			}
			catch(Exception e){
				
			}
		return out.toString();	
		}
		return out.toString();
	}
	private void searchStr(StringBuffer out,String search,String indexuri,String stylesheet){
		
		if (search.equals("")) {
//			appendDefaultPageStart(out, stylesheet);
//			//appendDefaultPostFields(out);
//			appendDefaultPostFields(out, search, indexuri);
//			appendDefaultPageEnd(out);
			return;
		}
		try {
		
//		appendDefaultPageStart(out, stylesheet);
//		appendDefaultPostFields(out, search, indexuri);

		out.append("<p><span class=\"librarian-searching-for-header\">Searching: </span><span class=\"librarian-searching-for-target\">").append(HTMLEncoder.encode(search)).append("</span></p>\n");
					// Get search result
		out.append("<p>Index Site: "+indexuri+"</p>");
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
				FileWriter output3 = new FileWriter("logfile_geturi",true);
				output3.write("inside keyuris elimination "+keyuris.size());
				synchronized(hs){
				if(keyuris.size() > 0){
				
				Iterator it = hs.iterator();
				while(it.hasNext())
				{
					URIWrapper uri = (URIWrapper) it.next();
					//output.write("\nhs values "+uri.URI);
					if(!Contains(uri.URI,keyuris)) it.remove();
//					if(!((uri.URI).equals(((URIWrapper) (keyuris.elementAt(0))).URI))) {
//						//output.write("\ndoesn't match \n "+keyuris.elementAt())
//						it.remove();
//					}
				}
				
				}
				if(keyuris.size() == 0) hs.clear();}
				
				output3.close();
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
		

	//	appendDefaultPageEnd(out);
		
		//return out.toString();
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	//	return e.toString();
	}
	}
	private boolean Contains(String str, Vector keyuris){
		if(keyuris.size() > 0){
			//to search if the string is present in the vector
			for(int i = 0; i<keyuris.size();i++){
				if(str.equals((((URIWrapper) (keyuris.elementAt(i))).URI))) return true;
				
			}
			return false;
		}
		else return false;
	}
	private Vector getIndex(String word) throws Exception{
	
	    
		String subIndex = searchStr(word);
	
		
		//fetch("index_"+subIndex+".xml");
		Vector index = new Vector();
		index = getEntry(word,subIndex);
		return index;
	}
	private String searchStr(String str) throws Exception{
		// in this we will search for the md5 in index.xml 
		//this should be same as that in XML Spider
		//we need to parse the input stream and then use that to find the matching subindex
		HighLevelSimpleClient hlsc = pr.getHLSimpleClient();
		FreenetURI u = new FreenetURI(DEFAULT_INDEX_SITE + DEFAULT_FILE);
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
		word = str;
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {

		      //  OutputStreamWriter output = new OutputStreamWriter (System.out, "UTF8");
		        SAXParser saxParser = factory.newSAXParser();
		        saxParser.parse(res.asBucket().getInputStream(), new LibrarianHandler() );

		  } catch (Throwable err) {
		        err.printStackTrace ();}
		//by this parsing we should have the correct match in prefix_match

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
	private Vector getEntry(String str,String subIndex)throws Exception{
		//search for the word in the given subIndex
		fileuris = new Vector();
		HighLevelSimpleClient hlsc = pr.getHLSimpleClient();
		FreenetURI u = new FreenetURI(DEFAULT_INDEX_SITE + "index_"+subIndex+".xml");
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
		word = str; //word to be searched
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {

		      //  OutputStreamWriter output = new OutputStreamWriter (System.out, "UTF8");
		        SAXParser saxParser = factory.newSAXParser();
		        saxParser.parse(res.asBucket().getInputStream(), new LibrarianHandler() );

		  } catch (Throwable err) {
		        err.printStackTrace ();}
		 FileWriter outp = new FileWriter("log_get",true);
		 outp.write("fileuris " + fileuris.size());
		 outp.close();
		//now the xml file is created and we need to look for the word
//		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
//		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
//		Document doc = docBuilder.parse("index_"+subIndex+".xml");
//		Element root = doc.getDocumentElement();
//		Element filesElement = (Element) root.getElementsByTagName("files").item(0);
//		NodeList wordList = root.getElementsByTagName("word");
//		for(int i = 0;i<wordList.getLength();i++)
//		{
//			Element wordElt = (Element)wordList.item(i);
//			String key = wordElt.getAttribute("v");
//			
//			if(key.equals(word)) 
//				{
//			
//				NodeList fileList = wordElt.getElementsByTagName("file");
//				
//				
//				
//				for(int j =0;j<fileList.getLength();j++){
//					Element file = (Element) fileList.item(j);
//					
//					//Attr id = (Attr) file.getAttributes().getNamedItem("id");
//					URIWrapper uri = new URIWrapper();
//					String id = file.getAttribute("id");
//					//reference this id from index.xml and get the file
//		//			uri.URI = getURI(id);
//					uri.URI = "not available";
//					NodeList files = filesElement.getElementsByTagName("file");
//					for(int k =0;k<files.getLength();k++){
//						Node fileElt =  files.item(k);
//						String fileid = ((Attr) fileElt.getAttributes().getNamedItem("id")).getValue();
//			
//						if(fileid.equals(id))
//							{
//							uri.URI = ((Attr) fileElt.getAttributes().getNamedItem("key")).getValue();
//							break;
//							}
//					}
//					FileWriter output3 = new FileWriter("logfile_geturi",true);
//					output3.write(uri.URI+"\n");
//					uri.descr = "not available";
//					fileuris.add(uri);
//					output3.close();
//				}
//				break;
//				}
//		}
		
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
			
			//found_match = false;
//			uriw = new URIWrapper();
//			uris = new Vector();
			
		}
		public void setDocumentLocator(Locator value) {
			locator =  value;
		}
		public void endDocument()  throws SAXException
		{
//		if(!word.equals(""))
//			keywords.put(word, keyuris);
//		
		}
		public void startDocument () throws SAXException
	    {
			found_match = false;
		uris = new HashMap();
	    }
	    public void startElement(String nameSpaceURI, String localName,
				 String rawName, Attributes attrs) throws SAXException {
	     
	  
	     
	    	if (rawName == null) {
				rawName = localName;
			}
	    	
	    	String elt_name = rawName;
	    	if(elt_name.equals("prefix")){
	    		prefix = Integer.parseInt(attrs.getValue("value"));
	    	}
	    	if(elt_name.equals("subIndex")){
	    		try{
	    		String md5 = MD5(word);
	    		//here we need to match and see if any of the subindices match the required substring of the word.
	    		for(int i=0;i<prefix;i++){
	    			if((md5.substring(0,prefix-i)).equals(attrs.getValue("key"))) 
	    				{
	    				prefix_match=md5.substring(0, prefix-i);
	    				break;
	    				}
	    		}
	    		}
	    		catch(Exception e){
	    		
	    		}
	    	}
	    	
	    	if(elt_name.equals("files")) processingWord = false;
	    	if(elt_name.equals("keywords")) processingWord = true;
	    	if(elt_name.equals("word")){
	    		//processingWord = true;
	    		try{
	    			FileWriter outp = new FileWriter("log_check",true);
	    			outp.write(" " + word);
	    			
	    			outp.write(" "+attrs.getValue("v")+"\n");
	    			outp.close();
	    			if((attrs.getValue("v")).equals(word)) found_match = true;
	    		}catch(Exception e){}
	    	}
	    	
	    	if(elt_name.equals("file")){
//	    		try{
//	    			FileWriter outp = new FileWriter("log_check",true);
//	    			outp.write(" " + processingWord);
//	    			outp.close();
//	    		}catch(Exception e){}
	    		if(processingWord == true && found_match == true){
//		    		//this file id has to be appended to the fileuris list
		    			
		    			try{
		    				//uris.put(attrs.getValue("id"), attrs.getValue("key"));
		    				FileWriter outp = new FileWriter("add",true);
		    				URIWrapper uri = new URIWrapper();
			    			uri.URI =  (uris.get(attrs.getValue("id"))).toString();
////			    			
			    			uri.descr = "not available";
			    			outp.write("uri.URI "+uri.URI);
			    			fileuris.add(uri);
//		    				outp.write(" uri.URI "+attrs.getValue("id"));
//		    				outp.write("\n uri .URI"+((String) (uris.get(attrs.getValue("id").toString()))));
//		    				outp.write(" "+found_match);
		    				outp.close();
		    			}
		    			catch(Exception e){}
		    			
		    			}
	    		else{
	    			try{
	    				String id = attrs.getValue("id");
	    				String key = attrs.getValue("key");
	    				uris.put(id,key);
	    				FileWriter outp = new FileWriter("add",true);
	    			
	    				String[] words = (String[]) uris.values().toArray(new String[uris.size()]);
	    				//outp.write(attrs.getValue("key")+"\n");
	    				outp.write("id "+id+" key "+key+"\n");
	    				outp.write(uris.size()+"\n");
	    				
	    				outp.close();
	    			}
	    			catch(Exception e){}
	    			
	    		}
	    		
	    		
	    			
	  	    	
	    	}
//	    	if(elt_name.equals("word"))
//	    	{
//	    	try
//	    	{
//	    		if(!word.equals(""))
//	    		{
//	    			keywords.put(word, keyuris);
//	    			word = new String();
//	    			
//	    		}
//	    	 word = attrs.getValue("v");
//	    	     
//	    			 
//	    	 }
//	    	 catch (Exception e)
//	    	 {
//	    		 
//	    	 }
//	    	 
//	    	 processingWord = true;
//	    	 keyuris = new Vector();	
//	    	}
//	    	
//	    	if(elt_name.equals("file"))
//	    	{
//	    		if(!processingWord)
//	    		{
//					uriw = new URIWrapper();
//					uriw.URI = attrs.getValue("key");
//					uriw.descr = "not available";
//					
//					uris.add(uriw);
//	     		}
//	    		else
//	    		{	    				    		
//				    int uriNumber = Integer.parseInt(attrs.getValue("id").toString());
//					URIWrapper uw = (URIWrapper) uris.get(uriNumber);
//					
//					if(!keyuris.contains(uw))
//						keyuris.add(uw);
//	    			}
//	     	}
	    		    	
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
