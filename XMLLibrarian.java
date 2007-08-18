package plugins.XMLLibrarian;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.DOMImplementation;
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
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;
/**
 * XMLLibrarian is a modified version of the old librarian.
 * It uses the Xml index files for searching. 
 * In addition to searching in a single index, XMLLibrarian allows searching in multiple 
 * indices at the same time. 
 * Folders containing set of indices can be created and any search on a folder searches the string in 
 * all the indices in the folder.
 * 
 * The current configuration can be stored in an external file and reused later. The default file for the same 
 * is XMLLibrarian.xml.
 * 
 *The index list of a particular folder can be saved in an external file and likewise imported from 
 *an existing file.
 *XMLLibrarian assumes that the index to be used is present at DEFAULT_INDEX_SITE/index.xml .
 * @author swatigoyal
 *
 */
public class XMLLibrarian implements FredPlugin, FredPluginHTTP, FredPluginThreadless {
	/**
	 * Gives the default index site displayed in the browser.
	 * <p>Change this parameter accordingly.
	 * 
	 */
//	public String DEFAULT_INDEX_SITE="SSK@OvRy7HP~dMKxitNNtZDXMFqI2IIWf7RifXrT61Nlk6c,F5f2AS9NFVTsR2okQFkbUh9EM~HNrD-f8LidYThN3MU,AQACAAE/testsite/";
	//public  String DEFAULT_INDEX_SITE="SSK@0yc3irwbhLYU1j3MdzGuwC6y1KboBHJ~1zIi8AN2XC0,5j9hrd2LLcew6ieoX1yC-hXRueSKziKYnRaD~aLnAYE,AQACAAE/testsite/";
	public String DEFAULT_INDEX_SITE="";
	/*
	 * Current configuration gets saved by default in the configfile.
	 * To Save the current configuration use "Save Configuration"
	 */
	private int version = 2;
	private String configfile = "XMLLibrarian.xml";
	private  String DEFAULT_FILE = "index.xml";
	boolean goon = true;
	private PluginRespirator pr;
	private final String plugName = "XMLLibrarian "+version;
	private String word ;
	private boolean processingWord ;
	private boolean found_match ;
	private HashMap uris;
	private HashMap titles;
	private Vector fileuris;
	private String prefix_match;
	private int prefix;
	private boolean test;
	/**
	 * indexList contains the index folders 
	 * each folder has a name and a list of indices added to that folder
	 */
	private HashMap indexList = new HashMap();

	public void terminate() {
		goon = false;
	
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


	/**
	 * appendDefaultPostFields generates the main interface to the XMLLibrarian
	 * @param out
	 * @param search
	 * @param index
	 */
	public void appendDefaultPostFields(StringBuffer out, String search, String index) {
		search = HTMLEncoder.encode(search);
		index = HTMLEncoder.encode(index);
		out.append("<form method=\"GET\"><table><tr><td>");
		out.append("<input type=submit name=\"addToFolder\" value=\"Add to folder\" /></td><td>");
		out.append("<input type=submit name=\"newFolder\" value=\"New Folder\" /></td>");
		out.append("<td><input type=submit name=\"List\" value=\"List\" /></td>");
		out.append("<td><input type=submit name=\"help\" value=\"Help!\" /></td>");
		out.append("<td><input type=submit name=\"delete\" value=\"Delete Folder\" /></td>");
		out.append("</tr></table>");
		out.append("Search for:<br/>");
		out.append("<p><input type=\"text\" value=\"").append(search).append("\" name=\"search\" size=80/>");
		out.append("<input type=submit name = \"find\" value=\"Find!\"/></p>\n");
		out.append("Using the index or folder <br/>");
		out.append("<p><input type=\"radio\" name=\"choice\" value=\"folder\">Folder");
		out.append("<select name=\"folderList\">");

		String[] words = (String[]) indexList.keySet().toArray(new String[indexList.size()]);

		for(int i =0;i<words.length;i++)
		{
			out.append("<option value=\"").append(words[i]).append("\">").append(words[i]).append("</option></p>");
		}
		out.append("</p><p><input type=\"radio\" name=\"choice\" value=\"index\">Index");
		out.append("<input type=\"text\" name=\"index\" value=\"").append(index).append("\" size=50/><br/>");



		out.append("<br/><br/><p><input type=\"file\" name=\"datafile\" /> ");
		out.append("<select name=\"actionList\" >");
		out.append("<option value=\"Import From File\">Import From File</option>");
		out.append("<option value=\"Export To File\">Export To File</option>");
		out.append("<option value=\"Load Configuration\">Load Configuration</option>");
		out.append("<option value=\"Save Configuration\">Save Configuration</option></select>");
		out.append("<input type=submit name=\"go\" value=\"Go!\" />");
		
		// index - key to index
		// search - text to search for
	}


	/**
	 * Generates the interface to the XMLLibrarian and takes apropos action to an event. 
	 * 
	 * @param request
	 */
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {

		if(test) {reloadOld(configfile); test= false;}

		StringBuffer out = new StringBuffer();
		String search = request.getParam("search");
		String stylesheet = request.getParam("stylesheet", null);
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
		DEFAULT_INDEX_SITE = indexuri;
		appendDefaultPageStart(out, stylesheet);
		appendDefaultPostFields(out, search, indexuri);
		appendDefaultPageEnd(out);
		
		if(((request.getParam("find")).equals("Find!")) && !choice.equals("folder") && !choice.equals("index"))
			out.append("Choose an index or a folder for search\n");
		/*
		 * search for the given string in the chosen folder 
		 */
		if(choice.equals("folder")){
			if((request.getParam("find")).equals("Find!"))
			{
				String folder = request.getParam("folderList");
				try{
					String[] indices = (String [])indexList.get(folder);
					for(int i =0;i<indices.length;i++)
					{try{
						searchStr(out,search,indices[i],stylesheet);}
					catch (Exception e){
						Logger.error(this, "Search for "+search+" in folder "+folder+" failed "+e.toString(), e);
					}
					}
				}
				catch(Exception e){
					out.append("No folder chosen\n");
				}
			}
		}
		/*
		 * create a new folder
		 */
		else if((request.getParam("newFolder")).equals("New Folder")){
			out.append("<p>Name of the new Folder<br/>");
			out.append("<form><input type=\"text\" name=\"newfolder\" size=20/> ");
			out.append("<input type=submit value=\"Add\" name=\"addNew\" />");
		}	 
		else if((request.getParam("addNew")).equals("Add")){
			try{
				String newFolder = request.getParam("newfolder");
				if(newFolder.equals("")) out.append("Invalid folder name \n");
				else {indexList.put(newFolder, new String[]{new String("0")});
				out.append("New folder "+newFolder+" added. Kindly refresh the page<br/> ");}
			}
			catch(Exception e){
				Logger.error(this, "Could not add new folder "+e.toString(), e);
			}
			return out.toString();
		}
		/*
		 * list the usage of various buttons
		 */
		else if((request.getParam("help")).equals("Help!")){
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
		/*
		 * delete the chosen folder
		 */
		else if((request.getParam("delete")).equals("Delete Folder")){
			String folder = request.getParam("folderList");
			if(folder.equals("")) out.append("Choose an existing folder for deletion");
			else{
				indexList.remove(folder);
				out.append("\""+folder+"\" deleted successfully. Kindly refresh the page\n");
			}
		}
		/*
		 * add the current index to the current folder
		 */
		else if((request.getParam("addToFolder")).equals("Add to folder")){
			String folder = request.getParam("folderList");
			indexuri = request.getParam("index",DEFAULT_INDEX_SITE);
			if(folder.equals("") || indexuri.equals(""))out.append("Index \""+indexuri+"\" could not be added to folder \""+folder+"\"");
			else{
				DEFAULT_INDEX_SITE = indexuri;
				try{
					String[] old = (String []) indexList.get(folder);
					String firstIndex = old[0]; 
					String[] indices;
					if(firstIndex.equals(new String("0"))){
						indices = new String[]{indexuri};
					}
					else{
						indices = new String[old.length+1];
						System.arraycopy(old, 0, indices, 0, old.length);

						indices[old.length] = indexuri;
					}

					out.append("index site "+indexuri+" added to "+folder);
					indexList.remove(folder);
					indexList.put(folder, indices);
				}
				catch(Exception e){
					Logger.error(this, "Index "+indexuri+" could not be added to folder "+folder+" "+e.toString(), e);
				}
			}
		}
		/*
		 * list the indices added to the current folder
		 */
		else if((request.getParam("List")).equals("List")){

			String folder = request.getParam("folderList");
			try{
				String[] indices = (String[]) indexList.get(folder);
				for(int i = 0;i<indices.length;i++){
					out.append("<p>\n<table class=\"librarian-result\" width=\"100%\" border=1><tr><td align=center bgcolor=\"#D0D0D0\" class=\"librarian-result-url\">\n");
					out.append("  <A HREF=\"").append(indices[i]).append("\">").append(indices[i]).append("</A>");
					out.append("</td></tr><tr><td align=left class=\"librarian-result-summary\">\n");
					out.append("</td></tr></table>\n");
				}}
			catch(Exception e){
				out.append("No folder chosen for listing \n");
			}
		}
		/*
		 * search for the given string in the current index
		 */
		else if(choice.equals("index")){
			try{
				if(indexuri.equals(""))out.append("Specify a valid index \n");
				else	searchStr(out,search,indexuri,stylesheet);}
			catch(Exception e){
				Logger.error(this, "Searching for the word "+search+" in index "+indexuri+" failed "+e.toString(), e);
			}
		}
		
		else if((request.getParam("go")).equals("Go!")){
			/*
			 * import the list of indices from a file on disk to the current folder
			 */
			if((request.getParam("actionList")).equals("Import From File")){
				String folder = request.getParam("folderList");
				String file = request.getParam("datafile");
				Vector indices=new Vector();
				try{
					BufferedReader inp = new BufferedReader(new FileReader(file));
					String index = inp.readLine();

					while(index != null){
						indices.add(index);
						out.append("index :"+index);
						index = inp.readLine();
					}
					String[] old = (String []) indexList.get(folder);
					String[] finalIndex;
					if(old[0].equals("0")) 
					{
						finalIndex = new String[indices.size()];
						for(int i = 0;i<indices.size();i++){
							finalIndex[i] = (String) indices.elementAt(i);
						}
					}
					else{
						finalIndex = new String[old.length + indices.size()];
						System.arraycopy(old, 0, finalIndex, 0, old.length);
						for(int i = 0;i<indices.size();i++){
							finalIndex[old.length + i] = (String) indices.elementAt(i);
						}
					}
					indexList.remove(folder);
					indexList.put(folder, finalIndex);

					inp.close();
				}
				catch(Exception e){
					out.append("Index list from file \" "+file+"\" could not be imported to folder \""+folder+"\"");
					Logger.error(this, "Index list from "+file+" could not be imported to folder "+folder+" "+e.toString(), e);
				}

			}
			/*
			 * export the current list of indices from the current folder to the specified file
			 */
			else if((request.getParam("actionList")).equals("Export To File")){

				String folder = request.getParam("folderList");
				String file = request.getParam("datafile");
				try{
					FileWriter outp = new FileWriter(file,true);

					String[] indices = ((String [])indexList.get(folder));
					for(int i = 0;i<indices.length;i++){
						outp.write(indices[i]+"\n");
					}
					outp.close();
				}
				catch(Exception e){
					out.append("Could not write index list of folder \""+folder+"\" to external file \""+file+"\"");
					Logger.error(this, "Could not write index list to external file "+e.toString(),e );}
				return out.toString();	
			}
			/*
			 * save the current configuration to the specified file
			 * default configuration file is configfile
			 */
			else if((request.getParam("actionList")).equals("Save Configuration")){
				try{
					String file = request.getParam("datafile");
					if(file.equals("")) file = configfile;
					save(out,file);
					out.append("Saved Configuration to file \""+file+"\"");
				}
				catch(Exception e){
					Logger.error(this, "Configuration could not be saved "+e.toString(), e);
				}
			}
			/*
			 * load a previously saved configuration
			 */
			else if((request.getParam("actionList")).equals("Load Configuration")){
				String file = request.getParam("datafile");
				if(file.equals("")) out.append("Choose an existing file \n");
				else{
					reloadOld(file);
					out.append("Loaded Configuration");}
			}
		}

		return out.toString();
	}
	
	/**
	 * reloadOld exports an externally saved configuration
	 * @param configuration filename
	 */
	private void reloadOld(String config){
		try{
			File f = new File(config);
			if(f.exists()){

				DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
				Document doc = docBuilder.parse(config);
				Element root = doc.getDocumentElement();
				NodeList folders = root.getElementsByTagName("folder");

				for(int i =0;i<folders.getLength();i++)
				{
					Attr folder = (Attr) ((folders.item(i)).getAttributes().getNamedItem("name"));
					String folderName = folder.getValue();
					NodeList indices = ((Element) folders.item(i)).getElementsByTagName("index");
					String[] index = new String[indices.getLength()];
					for(int j=0;j<indices.getLength();j++)
					{
						Attr indexj = (Attr) ((indices.item(j)).getAttributes().getNamedItem("key"));
						index[j] = indexj.getValue();
					}
					indexList.put(folderName, index);
				}}

		}
		catch(Exception e){ Logger.error(this, "Could not read configuration "+e.toString(), e);}
	}
	/*
	 * save the current configuration to the specified file, default being configfile
	 */
	private void save(StringBuffer out, String file){
		File outputFile = new File(file);
		StreamResult resultStream;
		resultStream = new StreamResult(outputFile);
		Document xmlDoc = null;
		DocumentBuilderFactory xmlFactory = null;
		DocumentBuilder xmlBuilder = null;
		DOMImplementation impl = null;
		Element rootElement = null;
		xmlFactory = DocumentBuilderFactory.newInstance();
		try {
			xmlBuilder = xmlFactory.newDocumentBuilder();
		} catch(javax.xml.parsers.ParserConfigurationException e) {

			Logger.error(this, "Spider: Error while initializing XML generator: "+e.toString());
			//return out.toString();
		}

		impl = xmlBuilder.getDOMImplementation();
		xmlDoc = impl.createDocument(null, "XMLLibrarian", null);
		rootElement = xmlDoc.getDocumentElement();

		String[] folders = (String[]) indexList.keySet().toArray(new String[indexList.size()]);
		for(int i=0;i<folders.length;i++)
		{
			Element folder = xmlDoc.createElement("folder");
			String folderName = folders[i];
			folder.setAttribute("name", folderName);

			String[] indices = (String[]) indexList.get(folderName);
			for(int j =0;j<indices.length;j++)
			{
				Element index = xmlDoc.createElement("index");
				index.setAttribute("key", indices[j]);
				folder.appendChild(index);
			}
			rootElement.appendChild(folder);
		}
		DOMSource domSource = new DOMSource(xmlDoc);
		TransformerFactory transformFactory = TransformerFactory.newInstance();
		Transformer serializer;

		try {
			serializer = transformFactory.newTransformer();
			serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			serializer.setOutputProperty(OutputKeys.INDENT,"yes");

			try {
				serializer.transform(domSource, resultStream);
			} catch(javax.xml.transform.TransformerException e) {
				Logger.error(this, "Spider: Error while serializing XML (transform()): "+e.toString());
				//return out.toString();
			}
		} catch(javax.xml.transform.TransformerConfigurationException e) {
			Logger.error(this, "Spider: Error while serializing XML (transformFactory.newTransformer()): "+e.toString());
			//	return out.toString();
		}
		if(Logger.shouldLog(Logger.MINOR, this))
			Logger.minor(this, "Spider: indexes regenerated.");

	}
	
	/**
	 * Searches for the string in the specified index. In case of a folder searches in all included indices
	 * @param out
	 * @param search - string to be searched
	 * @param indexuri
	 * @param stylesheet
	 */
	private void searchStr(StringBuffer out,String search,String indexuri,String stylesheet) throws Exception{

		if (search.equals("")) {
			out.append("Give a valid string to search\n");
			return;
		}
		try {
			out.append("<p><span class=\"librarian-searching-for-header\">Searching: </span><span class=\"librarian-searching-for-target\">").append(HTMLEncoder.encode(search)).append("</span></p>\n");
			// Get search result
			out.append("<p>Index Site: "+indexuri+"</p>");
			DEFAULT_INDEX_SITE = indexuri;
			String searchWords[] = search.split(" ");
			// Return results in order.
			LinkedHashSet hs = new LinkedHashSet();
			Vector keyuris;
			/*
			 * search for each string in the search list
			 * only the common results to all words are returned as final result 
			 * 
			*/
			try{
				for(int i = 0;i<searchWords.length;i++){
					keyuris = getIndex(searchWords[i]);
					if(i == 0){
						synchronized(hs){
							hs.clear();
							if (keyuris != null) {
								Iterator it = keyuris.iterator();
								while (it.hasNext()){
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
									while(it.hasNext()){
										URIWrapper uri = (URIWrapper) it.next();
										if(!Contains(uri.URI,keyuris)) it.remove();
									}
								}
								if(keyuris.size() == 0) hs.clear();
							}
						}
						catch(Exception e){
							e.getMessage();
						}
					}
				}}
			catch(Exception e){
				out.append("Could not complete search for "+search +" in "+indexuri+e.toString());
				Logger.error(this, "Could not complete search for "+search +"in "+indexuri+e.toString(), e);
			}
			// Output results
			int results = 0;
			out.append("<table class=\"librarian-results\"><tr>\n");
			Iterator it = hs.iterator();
			try{
				while (it.hasNext()) {
					URIWrapper o = (URIWrapper)it.next();
					String showurl = o.URI;
					String showtitle = o.descr;
					if(showtitle.equals("not available")) showtitle = showurl;
					String description = HTMLEncoder.encode(o.descr);
					if(!description.equals("not available")){
						description=description.replaceAll("(\n|&lt;(b|B)(r|R)&gt;)", "<br>");
						description=description.replaceAll("  ", "&nbsp; ");
						description=description.replaceAll("&lt;/?[a-zA-Z].*/?&gt;", "");	
					}
					showurl = HTMLEncoder.encode(showurl);
					if (showurl.length() > 60)
						showurl = showurl.substring(0,15) + "&hellip;" +  showurl.replaceFirst("[^/]*/", "/");
					String realurl = (o.URI.startsWith("/")?"":"/") + o.URI;
					realurl = HTMLEncoder.encode(realurl);
					out.append("<p>\n<table class=\"librarian-result\" width=\"100%\" border=1><tr><td align=center bgcolor=\"#D0D0D0\" class=\"librarian-result-url\">\n");
					out.append("  <A HREF=\"").append(realurl).append("\" title=\"").append(o.URI).append("\">").append(showtitle).append("</A>\n");
					out.append("</td></tr><tr><td align=left class=\"librarian-result-summary\">\n");
					out.append("</td></tr></table>\n");
					results++;
				}
			}
			catch(Exception e){
				out.append("Could not display results for "+search+e.toString());
				Logger.error(this, "Could not display search results for "+search+e.toString(), e);
			}
			out.append("</tr><table>\n");
			out.append("<p><span class=\"librarian-summary-found-text\">Found: </span><span class=\"librarian-summary-found-number\">").append(results).append(" results</span></p>\n");
		} catch (Exception e) {
			Logger.error(this, "Could not complete search for "+search +"in "+indexuri+e.toString(), e);
			e.printStackTrace();
		}
	}
	private boolean Contains(String str, Vector keyuris){
		if(keyuris.size() > 0){
			for(int i = 0; i<keyuris.size();i++){
				if(str.equals((((URIWrapper) (keyuris.elementAt(i))).URI))) return true;
			}
			return false;
		}
		else return false;
	}

	/*
	 * gets the index for the given word
	 */
	private Vector getIndex(String word) throws Exception{
		String subIndex = searchStr(word);
		Vector index = new Vector();
		index = getEntry(word,subIndex);
		return index;
	}

	/**
	 * Parses through the main index file(index.xml) looking for the subindex containing the entry for the search string.
	 * @param str  word to be searched
	 * @return
	 * @throws Exception
	 */
	public String searchStr(String str) throws Exception{
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
			SAXParser saxParser = factory.newSAXParser();
			saxParser.parse(res.asBucket().getInputStream(), new LibrarianHandler() );
		} catch (Throwable err) {
			err.printStackTrace ();}


		return prefix_match;

	}

	/**
	 * Searches through the chosen subindex for the files containing the searc word
	 * @param str search string
	 * @subIndex subIndex containing the word
	 */
	public Vector getEntry(String str,String subIndex)throws Exception{
		//search for the word in the given subIndex
		fileuris = new Vector();
		HighLevelSimpleClient hlsc = pr.getHLSimpleClient();
		try{
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
				SAXParser saxParser = factory.newSAXParser();
				saxParser.parse(res.asBucket().getInputStream(), new LibrarianHandler() );
			} catch (Throwable err) {
				err.printStackTrace ();}
		}
		catch(Exception e){
			Logger.error(this, DEFAULT_INDEX_SITE+"index_"+subIndex+".xml could not be opened "+e.toString(), e);
		}
		return fileuris;
	}

	/**
	 * Gets the key of the matched uri.
	 * @param id 	id of the uri which contains the searched word
	 * @return		key of the uri
	 * @throws Exception
	 */
	private String getURI(String id) throws Exception
	{
		try{
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

		}
		catch(Exception e){
			Logger.error(this, "uri for id ="+id+" could not be retrieved "+e.toString(), e);

		}
		return "not available";
	}


	public void runPlugin(PluginRespirator pr) {
		this.pr = pr;
		this.test = true;
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

	/**
	 * Required for using SAX parser on XML indices
	 * @author swati
	 *
	 */
	public class LibrarianHandler extends DefaultHandler {
		// now we need to adapt this to read subindexing 
		private Locator locator = null;
		public LibrarianHandler() throws Exception{
		}
		public void setDocumentLocator(Locator value) {
			locator =  value;
		}
		public void endDocument()  throws SAXException{}

		public void startDocument () throws SAXException
		{
			found_match = false;
			uris = new HashMap();
			titles = new HashMap();
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
					String md5 = MD5(word);
					//here we need to match and see if any of the subindices match the required substring of the word.
					for(int i=0;i<prefix;i++){
						if((md5.substring(0,prefix-i)).equals(attrs.getValue("key"))){ 
							prefix_match=md5.substring(0, prefix-i);
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
					if((attrs.getValue("v")).equals(word)) found_match = true;
				}catch(Exception e){Logger.error(this, "word key doesn't match"+e.toString(), e); }
			}

			if(elt_name.equals("file")){
				if(processingWord == true && found_match == true){
					URIWrapper uri = new URIWrapper();
					uri.URI =  (uris.get(attrs.getValue("id"))).toString();
					//uri.descr = "not available";
					uri.descr = (titles.get(attrs.getValue("id"))).toString();
					if ((uri.URI).equals(uri.descr)) uri.descr = "not available";
					fileuris.add(uri);
				}
				else{
					try{
						String id = attrs.getValue("id");
						String key = attrs.getValue("key");
						String title = attrs.getValue("title");
						uris.put(id,key);
						titles.put(id,title);
						String[] words = (String[]) uris.values().toArray(new String[uris.size()]);
					}
					catch(Exception e){Logger.error(this,"File id and key could not be retrieved. May be due to format clash",e);}
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
