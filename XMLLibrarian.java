package plugins.XMLLibrarian;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Vector;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
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
 * XMLLibrarian is a modified version of the old librarian. It uses the Xml index files for
 * searching. In addition to searching in a single index, XMLLibrarian allows searching in multiple
 * indices at the same time. Folders containing set of indices can be created and any search on a
 * folder searches the string in all the indices in the folder.
 * 
 * The current configuration can be stored in an external file and reused later. The default file
 * for the same is XMLLibrarian.xml.
 * 
 *The index list of a particular folder can be saved in an external file and likewise imported from
 * an existing file. XMLLibrarian assumes that the index to be used is present at
 * DEFAULT_INDEX_SITE/index.xml .
 * 
 * @author swatigoyal
 * 
 */
public class XMLLibrarian implements FredPlugin, FredPluginHTTP, FredPluginVersioned, FredPluginThreadless {
	/**
	 * Default index site
	 */
	public static final String DEFAULT_INDEX_SITE = "USK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search/19/";
	/*
	 * Current configuration gets saved by default in the configfile. To Save the current
	 * configuration use "Save Configuration"
	 */
	private static int version = 20;
	private static final String plugName = "XMLLibrarian " + version;

	public String getVersion() {
		return version + " r" + Version.getSvnRevision();
	}

	private static final String DEFAULT_FILE = "index.xml";
	private PluginRespirator pr;

	public void terminate() {

	}

	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		String search = request.getParam("search");
		String indexuri = request.isParameterSet("index") ? request.getParam("index") : DEFAULT_INDEX_SITE;

		return handleInner(request.getPath(), search, indexuri);
	}

	private void appendDefaultPageStart(StringBuilder out) {

		out.append("<HTML><HEAD><TITLE>" + plugName + "</TITLE>");
		out.append("</HEAD><BODY>\n");
		out.append("<CENTER><H1>" + plugName + "</H1><BR/><BR/><BR/>\n");
	}

	private void appendDefaultPageEnd(StringBuilder out) {
		out.append("</CENTER></BODY></HTML>");
	}

	/**
	 * appendDefaultPostFields generates the main interface to the XMLLibrarian
	 * 
	 * @param out
	 * @param search
	 * @param index
	 */

	private void appendDefaultPostFields(StringBuilder out, String search, String index) {
		search = HTMLEncoder.encode(search);
		index = HTMLEncoder.encode(index);
		String s = "<div style=\"visibility:hidden;\"><input type=submit name = \"find\" value=\"Find!\" TABINDEX=1/></div>";
		out.append("<form method=\"GET\">");
		out.append(s);
		out.append("Search for:<br/>");
		out.append("<p><input type=\"text\" value=\"").append(search).append("\" name=\"search\" size=80/>");
		out.append("<input type=submit name = \"find\" value=\"Find!\" TABINDEX=1/></p>\n");
		out.append("Using the index <br/>");
		out.append("</p><p>Index");
		out.append("<input type=\"text\" name=\"index\" value=\"").append(index).append("\" size=50/><br/>");
		out.append("</p></form>");
	}

	/**
	 * Generates the interface to the XMLLibrarian and takes apropos action to an event.
	 * 
	 * @param request
	 */
	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		String search = request.getPartAsString("search", 80);
		String indexuri = request.isPartSet("index") ? request.getPartAsString("index", 200) : DEFAULT_INDEX_SITE;

		return handleInner(request.getPath(), search, indexuri);
	}

	private String handleInner(String path, String search, String indexuri) {
		StringBuilder out = new StringBuilder();

		if (!indexuri.endsWith("/"))
			indexuri += "/";

		appendDefaultPageStart(out);
		appendDefaultPostFields(out, search, indexuri);
		appendDefaultPageEnd(out);

		try {
			if (indexuri.equals(""))
				out.append("Specify a valid index \n");
			else
				searchStr(out, search, indexuri);
		} catch (Exception e) {
			Logger.error(this,
			        "Searching for the word " + search + " in index " + indexuri + " failed " + e.toString(), e);
		}

		return out.toString();
	}

	/**
	 * Searches for the string in the specified index. In case of a folder searches in all included
	 * indices
	 * 
	 * @param out
	 * @param search
	 *            - string to be searched
	 * @param indexuri
	 * @param stylesheet
	 */
	private void searchStr(StringBuilder out, String search, String indexuri) throws Exception {
		search = search.toLowerCase();
		if (search.equals("")) {
			out.append("Give a valid string to search\n");
			return;
		}
		String searchWord = null;
		try {
			out
			        .append(
			                "<p><span class=\"librarian-searching-for-header\">Searching: </span><span class=\"librarian-searching-for-target\">")
			        .append(HTMLEncoder.encode(search)).append("</span></p>\n");
			// Get search result
			out.append("<p>Index Site: " + HTMLEncoder.encode(indexuri) + "</p>");

			String[] searchWords = search.split("[^\\p{L}\\{N}]+");
			// Return results in order.
			LinkedHashSet<URIWrapper> hs = null;
			/*
			 * search for each string in the search list only the common results to all words are
			 * returned as final result
			 */
			try {
				for (String s : searchWords) {
					searchWord = s;
					if (searchWord.length() < 3)
						continue; // xmlspider don't include words length < 3, have to fix this

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
				if (uri != null) {
					String encoded = HTMLEncoder.encode(uri);
					href = "<a href=\"/" + encoded + "\">";
					endHref = "</a>";
				}
				out.append("<p>Could not fetch " + href + "sub-index" + endHref + " for " + HTMLEncoder.encode(search)
				        + " : " + e.getMessage() + "</p>\n");
				Logger.normal(this, "<p>Could not fetch sub-index for " + HTMLEncoder.encode(search) + " in "
				        + HTMLEncoder.encode(indexuri) + " : " + e.toString() + "</p>\n", e);
			} catch (Exception e) {
				out.append("<p>Could not complete search for " + HTMLEncoder.encode(search) + " : " + e.toString()
				        + "</p>\n");
				out.append(String.valueOf(e.getStackTrace()));
				Logger.error(this, "Could not complete search for " + search + "in " + indexuri + e.toString(), e);
			}
			// Output results
			int results = 0;
			out.append("<table class=\"librarian-results\"><tr>\n");
			Iterator<URIWrapper> it = hs.iterator();
			try {
				while (it.hasNext()) {
					URIWrapper o = it.next();
					String showurl = o.URI;
					String showtitle = o.descr;
					if (showtitle.trim().length() == 0)
						showtitle = "not available";
					if (showtitle.equals("not available"))
						showtitle = showurl;
					String description = HTMLEncoder.encode(o.descr);
					if (!description.equals("not available")) {
						description = description.replaceAll("(\n|&lt;(b|B)(r|R)&gt;)", "<br>");
						description = description.replaceAll("  ", "&nbsp; ");
						description = description.replaceAll("&lt;/?[a-zA-Z].*/?&gt;", "");
					}
					showurl = HTMLEncoder.encode(showurl);
					if (showurl.length() > 60)
						showurl = showurl.substring(0, 15) + "&hellip;" + showurl.replaceFirst("[^/]*/", "/");
					String realurl = (o.URI.startsWith("/") ? "" : "/") + o.URI;
					realurl = HTMLEncoder.encode(realurl);
					out
					        .append("<p>\n<table class=\"librarian-result\" width=\"100%\" border=1><tr><td align=center bgcolor=\"#D0D0D0\" class=\"librarian-result-url\">\n");
					out.append("  <A HREF=\"").append(realurl).append("\" title=\"").append(o.URI).append("\">")
					        .append(showtitle).append("</A>\n");
					out.append("</td></tr><tr><td align=left class=\"librarian-result-summary\">\n");
					out.append("</td></tr></table>\n");
					results++;
				}
			} catch (Exception e) {
				out.append("Could not display results for " + search + e.toString());
				Logger.error(this, "Could not display search results for " + search + e.toString(), e);
			}
			out.append("</tr><table>\n");
			out
			        .append(
			                "<p><span class=\"librarian-summary-found-text\">Found: </span><span class=\"librarian-summary-found-number\">")
			        .append(results).append(" results</span></p>\n");
		} catch (Exception e) {
			Logger.error(this, "Could not complete search for " + search + " in " + indexuri + e.toString(), e);
			e.printStackTrace();
		}
	}

	private String getSubIndex(String indexuri, String word) {
		if (word == null)
			return null;

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
	 * Parses through the main index file(index.xml) looking for the subindex containing the entry
	 * for the search string.
	 * 
	 * @param str
	 *            word to be searched
	 * @return
	 * @throws Exception
	 */
	private String getSubindex(String indexuri, String str) throws Exception {
		Bucket bucket = fetchBucket(indexuri + DEFAULT_FILE);

		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
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
	 * 
	 * @param str
	 *            search string
	 * @subIndex subIndex containing the word
	 */
	private Vector<URIWrapper> getEntry(String str, String indexuri, String subIndex) throws Exception {
		//search for the word in the given subIndex
		Vector<URIWrapper> fileuris = new Vector<URIWrapper>();

		try {
			Bucket bucket = fetchBucket(indexuri + "index_" + subIndex + ".xml");

			SAXParserFactory factory = SAXParserFactory.newInstance();
			try {
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				SAXParser saxParser = factory.newSAXParser();
				InputStream is = bucket.getInputStream();
				saxParser.parse(is, new LibrarianHandler(str, fileuris));
				is.close();
			} catch (Throwable err) {
				err.printStackTrace();
				throw new Exception("Could not parse XML: " + err.toString());
			} finally {
				bucket.free();
			}
		} catch (Exception e) {
			Logger.error(this, indexuri + "index_" + subIndex + ".xml could not be opened " + e.toString(), e);
			throw e;
		}
		return fileuris;
	}

	public void runPlugin(PluginRespirator pr) {
		this.pr = pr;
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
			} while (two_halfs++ < 1);
		}
		return buf.toString();
	}

	//this function will return the String representation of the MD5 hash for the input string 
	public static String MD5(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException {
		MessageDigest md;
		md = MessageDigest.getInstance("MD5");
		byte[] b = text.getBytes("UTF-8");
		md.update(b, 0, b.length);
		byte[] md5hash = md.digest();
		return convertToHex(md5hash);
	}
}
