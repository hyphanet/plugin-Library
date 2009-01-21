package plugins.XMLLibrarian;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
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

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.HTTPRequestImpl;
import freenet.clients.http.filter.CommentException;
import freenet.clients.http.filter.FilterCallback;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLEncoder;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.io.FileBucket;
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
public class XMLLibrarian implements FredPlugin, FredPluginHTTP, FredPluginVersioned, FredPluginThreadless {
	/**
	 * Default index site
	 */
	public final String DEFAULT_INDEX_SITE = "SSK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search-17/";
	/*
	 * Current configuration gets saved by default in the configfile.
	 * To Save the current configuration use "Save Configuration"
	 */
	private int version = 19;
	private final String plugName = "XMLLibrarian " + version;

	public String getVersion() {
		return version + " r" + Version.getSvnRevision();
	}
	
	private String configfile = "XMLLibrarian.xml";
	private final String DEFAULT_FILE = "index.xml";
	private PluginRespirator pr;

	private boolean test;
	
	/**
	 * indexList contains the index folders 
	 * each folder has a name and a list of indices added to that folder
	 */
	private HashMap<String, String[]> indexList = new HashMap<String, String[]>();

	public void terminate() {
	
	}
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		if(test) {reloadOld(configfile); test= false;}

		String search = request.getParam("search");
		String stylesheet = request.getParam("stylesheet");
		String choice = request.getParam("choice");
		String indexuri = request.isParameterSet("index") ? request.getParam("index") : DEFAULT_INDEX_SITE;
		String find = request.getParam("find");
		String folder = request.getParam("folderList");
		String newFolder = request.getParam("newFolder");
		String addNew = (request.getParam("addNew"));
		String help = (request.getParam("help"));
		String delete = (request.getParam("delete"));
		String list = (request.getParam("List"));
		String addToFolder = (request.getParam("addToFolder"));
		String go = (request.getParam("go"));
		String actionList = request.getParam("actionList");
		String file = request.getParam("datafile");
		
		return handleInner(request.getPath(), search, stylesheet, choice, indexuri, find, folder, newFolder, addNew, help, delete, list, addToFolder, go, actionList, file);
	}

	private void appendDefaultPageStart(StringBuilder out, String stylesheet) {
		
		out.append("<HTML><HEAD><TITLE>" + plugName + "</TITLE>");
		if(stylesheet != null)
			out.append("<link href=\""+stylesheet+"\" type=\"text/css\" rel=\"stylesheet\" />");
		//String s = "<script type=\"text/javascript\">"+"function reloadPage(){ window.location.reload()}</script>";
		//out.append(s);
		out.append("</HEAD><BODY>\n");
		out.append("<CENTER><H1>" + plugName + "</H1><BR/><BR/><BR/>\n");
	}

	private void appendDefaultPageEnd(StringBuilder out) {
		out.append("</CENTER></BODY></HTML>");
	}


	/**
	 * appendDefaultPostFields generates the main interface to the XMLLibrarian
	 * @param out
	 * @param search
	 * @param index
	 */
	
	public void appendDefaultPostFields(StringBuilder out, String search, String index) {
		search = HTMLEncoder.encode(search);
		index = HTMLEncoder.encode(index);
		String s = "<div style=\"visibility:hidden;\"><input type=submit name = \"find\" value=\"Find!\" TABINDEX=1/></div>";
		out.append("<form method=\"POST\">");
		out.append(s);
		out.append("<table><tr><td><input type=submit name=\"addToFolder\" value=\"Add to folder\" tabindex=9 /></td><td>");
		out.append("<input type=submit name=\"newFolder\" value=\"New Folder\" tabindex=8/></td>");
		out.append("<td><input type=submit name=\"List\" value=\"List\" tabindex=7/></td>");
		out.append("<td><input type=submit name=\"help\" value=\"Help!\" tabindex=6/></td>");
		out.append("<td><input type=submit name=\"delete\" value=\"Delete Folder\" tabindex=5/></td>");
		out.append("<input type=hidden name=formPassword value=\""+pr.getNode().clientCore.formPassword+"\">");
		out.append("</tr></table>");
		out.append("Search for:<br/>");
		out.append("<p><input type=\"text\" value=\"").append(search).append("\" name=\"search\" size=80/>");
		out.append("<input type=submit name = \"find\" value=\"Find!\" TABINDEX=1/></p>\n");
		out.append("Using the index or folder <br/>");
		out.append("<p><input type=\"radio\" name=\"choice\" value=\"folder\">Folder");
		out.append("<select name=\"folderList\">");

		String[] words = indexList.keySet().toArray(new String[indexList.size()]);

		for(int i =0;i<words.length;i++)
		{
			out.append("<option value=\"").append(words[i]).append("\">").append(HTMLEncoder.encode(words[i])).append("</option></p>");
		}
		out.append("</p><p><input type=\"radio\" name=\"choice\" value=\"index\" checked=\"checked\" >Index");
		out.append("<input type=\"text\" name=\"index\" value=\"").append(index).append("\" size=50/><br/>");



		out.append("<br/><br/><p><input type=\"file\" name=\"datafile\" /> ");
		out.append("<select name=\"actionList\" >");
		out.append("<option value=\"Import From File\">Import From File</option>");
		out.append("<option value=\"Export To File\">Export To File</option>");
		out.append("<option value=\"Load Configuration\">Load Configuration</option>");
		out.append("<option value=\"Save Configuration\">Save Configuration</option></select>");
		out.append("<input type=submit name=\"go\" value=\"Go!\" />");
		out.append("</form>");
	//	out.append("SetDefaultButton(this.Page, \"search\",\"find\") ");
		// index - key to index
		// search - text to search for
	}


	/**
	 * Generates the interface to the XMLLibrarian and takes apropos action to an event. 
	 * 
	 * @param request
	 */
	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {

		if(test) {reloadOld(configfile); test= false;}

		String search = request.getPartAsString("search", 80);
		String stylesheet = request.getPartAsString("stylesheet", 200);
		String choice = request.getPartAsString("choice", 80);
		String indexuri = request.isPartSet("index") ? request.getPartAsString("index", 200) : DEFAULT_INDEX_SITE;
		String find = request.getPartAsString("find",80);
		String folder = request.getPartAsString("folderList", 100);
		String newFolder = request.getPartAsString("newFolder",80);
		String addNew = (request.getPartAsString("addNew",80));
		String help = (request.getPartAsString("help",80));
		String delete = (request.getPartAsString("delete",80));
		String list = (request.getPartAsString("List",80));
		String addToFolder = (request.getPartAsString("addToFolder",80));
		String go = (request.getPartAsString("go",80));
		String actionList = request.getPartAsString("actionList",80);
		String file = request.getPartAsString("datafile",80);
		
		return handleInner(request.getPath(), search, stylesheet, choice, indexuri, find, folder, newFolder, addNew, help, delete, list, addToFolder, go, actionList, file);
	}
	
	private String handleInner(String path, String search, String stylesheet, String choice, String indexuri, String find, String folder, String newFolder, String addNew, String help, String delete, String list, String addToFolder, String go, String actionList, String file) {
		StringBuilder out = new StringBuilder();
		if(stylesheet != null && !(stylesheet.length() == 0)) {
			FilterCallback cb = pr.makeFilterCallback(path);
			try {
				stylesheet = cb.processURI(stylesheet, "text/css");
			} catch (CommentException e) {
				return "Invalid stylesheet: "+e.getMessage();
			}
		}

		if (!indexuri.endsWith("/")) indexuri += "/";

		String indexSite = HTMLEncoder.encode(indexuri);
		appendDefaultPageStart(out, stylesheet);
		appendDefaultPostFields(out, search, indexuri);
		appendDefaultPageEnd(out);
		
		if(((find.equals("Find!")) && !choice.equals("folder") && !choice.equals("index")))
			out.append(HTMLEncoder.encode("Choose an index or a folder for search\n"));
		/*
		 * search for the given string in the chosen folder 
		 */
		if(choice.equals("folder")){
			if((find.equals("Find!")))
			{
				try{
					String[] indices = indexList.get(folder);
					
					String firstIndex = indices[0]; 
					if (firstIndex.equals("0")) {
						out.append("No indices found in folder \""+folder+"\"");
					}
					else{
					for(int i =0;i<indices.length;i++) {
						try {
							searchStr(out,search,indices[i],stylesheet);
						} catch (FetchException e) {
							Logger.normal(this, "Search for "+search+" in folder "+folder+" failed: "+e.toString(), e);
							out.append("<p>Unable to fetch index: "+indices[i]);
							out.append(e.getMessage());
							out.append(String.valueOf(e.getStackTrace()));
						} catch (Exception e) {
							Logger.error(this, "Search for "+search+" in folder "+folder+" failed "+e.toString(), e);
							out.append("<p>Unable to search in index: "+e.toString()+"</p>\n");
						}
					}}
				}
				catch(Exception e){
					out.append("No folder chosen\n");
				}
			}
		}
		/*
		 * create a new folder
		 */
		else if((newFolder).equals("New Folder")){
			out.append("<p>Name of the new Folder<br/>");
			out.append("<form><input type=\"text\" name=\"newfolder\" size=20/> ");
			out.append("<input type=hidden name=formPassword value=\""+pr.getNode().clientCore.formPassword+"\">");
			out.append("<input type=submit value=\"Add\" name=\"addNew\" />");
		}	 
		else if(addNew.equals("Add")){
			try{
				synchronized(this){
				if(newFolder.equals("")) out.append("Invalid folder name \n");
				else {
						indexList.put(newFolder, new String[] { "0" });
				out.append("New folder "+HTMLEncoder.encode(newFolder)+" added. Kindly refresh the page<br/> ");
				}}
			}
			catch(Exception e){
				Logger.error(this, "Could not add new folder "+e.toString(), e);
			}
			return out.toString();
		}
		/*
		 * list the usage of various buttons
		 */
		else if(help.equals("Help!")){
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
		else if(delete.equals("Delete Folder")){
			synchronized(this){
			if(folder.equals("")) out.append("Choose an existing folder for deletion");
			else{
				indexList.remove(folder);
				out.append("\""+HTMLEncoder.encode(folder)+"\" deleted successfully. Kindly refresh the page\n");
			}}
		}
		/*
		 * add the current index to the current folder
		 */
		else if(addToFolder.equals("Add to folder")){
			if(folder.equals("") || indexuri.equals(""))out.append("Index \""+HTMLEncoder.encode(indexuri)+"\" could not be added to folder \""+HTMLEncoder.encode(folder)+"\"");
			else{
				indexSite = indexuri;
				try{
					String[] old = indexList.get(folder);
					String firstIndex = old[0]; 
					String[] indices;
					if (firstIndex.equals("0")) {
						indices = new String[]{indexuri};
					}
					else{
						indices = new String[old.length+1];
						System.arraycopy(old, 0, indices, 0, old.length);

						indices[old.length] = indexuri;
					}

					out.append("index site "+HTMLEncoder.encode(indexuri)+" added to "+folder);
					synchronized(this){
					indexList.remove(folder);
					indexList.put(folder, indices);}
				}
				catch(Exception e){
					Logger.error(this, "Index "+indexuri+" could not be added to folder "+folder+" "+e.toString(), e);
				}
			}
		}
		/*
		 * list the indices added to the current folder
		 */
		else if(list.equals("List")){

			try{
				String[] indices = indexList.get(folder);
				for(int i = 0;i<indices.length;i++){
					out.append("<p>\n<table class=\"librarian-result\" width=\"100%\" border=1><tr><td align=center bgcolor=\"#D0D0D0\" class=\"librarian-result-url\">\n");
					out.append("  <A HREF=\"").append(HTMLEncoder.encode(indices[i])).append("\">").append(indices[i]).append("</A>");
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
				if(indexuri.equals("")) out.append("Specify a valid index \n");
				else	searchStr(out,search,indexuri,stylesheet);}
			catch(Exception e){
				Logger.error(this, "Searching for the word "+search+" in index "+indexuri+" failed "+e.toString(), e);
			}
		}
		
		else if(go.equals("Go!")){
			/*
			 * import the list of indices from a file on disk to the current folder
			 */
			if((actionList.equals("Import From File"))){
				Vector<String> indices=new Vector<String>();
				try{
					BufferedReader inp = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF8"));
				
					String index = inp.readLine();

					while(index != null){
						indices.add(index);
						out.append("index :"+HTMLEncoder.encode(index));
						index = inp.readLine();
					}
					String[] old = indexList.get(folder);
					String[] finalIndex;
					if(old[0].equals("0")) 
					{
						finalIndex = new String[indices.size()];
						for(int i = 0;i<indices.size();i++){
							finalIndex[i] = indices.elementAt(i);
						}
					}
					else{
						finalIndex = new String[old.length + indices.size()];
						System.arraycopy(old, 0, finalIndex, 0, old.length);
						for(int i = 0;i<indices.size();i++){
							finalIndex[old.length + i] = indices.elementAt(i);
						}
					}
					synchronized(this){
					indexList.remove(folder);
					indexList.put(folder, finalIndex);
					}
					inp.close();
				}
				catch(Exception e){
					out.append("Index list from file \" "+HTMLEncoder.encode(file)+"\" could not be imported to folder \""+folder+"\"");
					Logger.error(this, "Index list from "+file+" could not be imported to folder "+folder+" "+e.toString(), e);
				}

			}
			/*
			 * export the current list of indices from the current folder to the specified file
			 */
			else if((actionList.equals("Export To File"))){

				try{
					FileWriter outp = new FileWriter(file,true);
					try {
						String[] indices = indexList.get(folder);
						for(int i = 0;i<indices.length;i++){
							outp.write(indices[i]+"\n");
						}
					} finally {
						outp.close();
					}
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
			else if(actionList.equals("Save Configuration")){
				synchronized(this){
				try{
					if(file.equals("")) file = configfile;
					save(out,file);
					out.append("Saved Configuration to file \""+file+"\"");
				}
				catch(Exception e){
					Logger.error(this, "Configuration could not be saved "+e.toString(), e);
				}}
			}
			/*
			 * load a previously saved configuration
			 */
			else if(actionList.equals("Load Configuration")){
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
					synchronized(this){
					indexList.put(folderName, index);}
				}}

		}
		catch(Exception e){ Logger.error(this, "Could not read configuration "+e.toString(), e);}
	}
	/*
	 * save the current configuration to the specified file, default being configfile
	 */
	private void save(StringBuilder out, String file){
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

		String[] folders = indexList.keySet().toArray(new String[indexList.size()]);
		for(int i=0;i<folders.length;i++)
		{
			Element folder = xmlDoc.createElement("folder");
			String folderName = folders[i];
			folder.setAttribute("name", folderName);

			String[] indices = indexList.get(folderName);
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
	private void searchStr(StringBuilder out,String search,String indexuri,String stylesheet) throws Exception{
		search = search.toLowerCase();
		if (search.equals("")) {
			out.append("Give a valid string to search\n");
			return;
		}
		String searchWord = null;
		try {
			out.append("<p><span class=\"librarian-searching-for-header\">Searching: </span><span class=\"librarian-searching-for-target\">").append(HTMLEncoder.encode(search)).append("</span></p>\n");
			// Get search result
			out.append("<p>Index Site: "+HTMLEncoder.encode(indexuri)+"</p>");
			
			String[] searchWords = search.split("[^\\p{L}\\{N}]+");
			// Return results in order.
			LinkedHashSet<URIWrapper> hs = null;
			/*
			 * search for each string in the search list
			 * only the common results to all words are returned as final result 
			 * 
			*/
			try{
				for (String s : searchWords) {
					searchWord = s;
					if (searchWord.length() < 3)
						continue;		// xmlspider don't include words length < 3, have to fix this

					Vector<URIWrapper> keyuris = searchWord(indexuri, searchWord);
					if (hs == null)
						hs = new LinkedHashSet<URIWrapper>(keyuris);
					else
						hs.retainAll(keyuris);
				}

				if (hs == null)
					hs = new LinkedHashSet<URIWrapper>();
			} catch (FetchException e) {
				String uri = getSubIndex(indexuri, searchWord);
				String href = "";
				String endHref = "";
				if(uri != null) {
					String encoded = HTMLEncoder.encode(uri);
					href="<a href=\"/" + encoded+"\">";
					endHref = "</a>";
				}
				out.append("<p>Could not fetch "+href+"sub-index"+endHref+" for "+HTMLEncoder.encode(search)+" : "+e.getMessage()+"</p>\n");
				Logger.normal(this, "<p>Could not fetch sub-index for "+HTMLEncoder.encode(search)+" in "+HTMLEncoder.encode(indexuri)+" : "+e.toString()+"</p>\n", e);
			} catch(Exception e) {
				out.append("<p>Could not complete search for "+HTMLEncoder.encode(search) +" : "+e.toString()+"</p>\n");
				out.append(String.valueOf(e.getStackTrace()));
				Logger.error(this, "Could not complete search for "+search +"in "+indexuri+e.toString(), e);
			}
			// Output results
			int results = 0;
			out.append("<table class=\"librarian-results\"><tr>\n");
			Iterator<URIWrapper> it = hs.iterator();
			try{
				while (it.hasNext()) {
					URIWrapper o = it.next();
					String showurl = o.URI;
					String showtitle = o.descr;
					if(showtitle.trim().length() == 0)
						showtitle = "not available";
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
			Logger.error(this, "Could not complete search for "+search +" in "+indexuri+e.toString(), e);
			e.printStackTrace();
		}
	}
	
	private String getSubIndex(String indexuri, String word) {
		if(word == null) return null;
		
		try {
			String subindex = getSubindex(indexuri, word);
			File file = new File(indexuri + "index_" + subindex + ".xml");

			if (file.exists())
				return file.toURI().toASCIIString();

			return new FreenetURI(indexuri + "index_" + subindex + ".xml").toASCIIString();
		} catch (MalformedURLException e) {
			return null;
		} catch (Exception e) {
			return null;
		}
	}
	
	/**
	 * Search for a word
	 */
	private Vector<URIWrapper> searchWord(String indexuri, String word) throws Exception {
		String subIndex = getSubindex(indexuri, word);
		Vector<URIWrapper> index = getEntry(word, indexuri, subIndex);
		return index;
	}

	private Bucket fetchBucket(String uri) throws FetchException, MalformedURLException {
		// File
		File file = new File(uri);
		if (file.exists() && file.canRead()) {
			return new FileBucket(file, true, false, false, false, false);
		}

		// FreenetURI
		HighLevelSimpleClient hlsc = pr.getHLSimpleClient();
		FreenetURI u = new FreenetURI(uri);
		FetchResult res;
		while (true) {
			try {
				res = hlsc.fetch(u);
				break;
			} catch (FetchException e) {
				if (e.newURI != null) {
					u = e.newURI;
					continue;
				} else
					throw e;
			}
		}
		
		return res.asBucket();
	}
	
	/**
	 * Parses through the main index file(index.xml) looking for the subindex containing the entry for the search string.
	 * @param str  word to be searched
	 * @return
	 * @throws Exception
	 */
	public String getSubindex(String indexuri, String str) throws Exception {
		Bucket bucket = fetchBucket(indexuri + DEFAULT_FILE);
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			SAXParser saxParser = factory.newSAXParser();
			InputStream is = bucket.getInputStream();
			LibrarianHandler lib = new LibrarianHandler(str, new Vector<URIWrapper>());
			saxParser.parse(is, lib);
			is.close();
			
			return lib.getPrefixMatch();
		} catch (Throwable err) {
			err.printStackTrace();
		} finally {
			bucket.free();
		}

		return null;
	}

	/**
	 * Searches through the chosen subindex for the files containing the searc word
	 * @param str search string
	 * @subIndex subIndex containing the word
	 */
	public Vector<URIWrapper> getEntry(String str, String indexuri, String subIndex) throws Exception {
		//search for the word in the given subIndex
		Vector<URIWrapper> fileuris = new Vector<URIWrapper>();
		
				try {
			Bucket bucket = fetchBucket(indexuri + "index_" + subIndex + ".xml");
	
			SAXParserFactory factory = SAXParserFactory.newInstance();
			try {
				SAXParser saxParser = factory.newSAXParser();
				InputStream is = bucket.getInputStream();
				saxParser.parse(is, new LibrarianHandler(str, fileuris));
				is.close();
			} catch (Throwable err) {
				err.printStackTrace ();
				throw new Exception("Could not parse XML: "+err.toString());
			} finally {
				bucket.free();
			}
		}
		catch(Exception e){
			Logger.error(this, indexuri + "index_" + subIndex + ".xml could not be opened " + e.toString(), e);
			throw e;
		}
		return fileuris;
	}

	public void runPlugin(PluginRespirator pr) {
		this.pr = pr;
		this.test = true;
	}

	private static String convertToHex(byte[] data) {
		StringBuilder buf = new StringBuilder();
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
		byte[] b = text.getBytes("UTF-8");
		md.update(b, 0, b.length);
		byte[] md5hash = md.digest();
		return convertToHex(md5hash);
	}
}
