/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index.xml;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;
import com.db4o.ObjectContainer;

import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.io.FileBucket;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.client.async.ClientContext;
import freenet.client.events.ClientEventListener;
import freenet.client.events.ClientEvent;
import freenet.client.FetchException;
import freenet.client.HighLevelSimpleClient;
import freenet.client.FetchResult;
import freenet.node.RequestStarter;
import freenet.node.RequestClient;
import freenet.keys.FreenetURI;

import freenet.pluginmanager.PluginRespirator;
import freenet.support.Executor;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.net.MalformedURLException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.SortedMap;
import java.util.HashMap;

import plugins.Library.Library;
import plugins.Library.library.Index;
import plugins.Library.search.InvalidSearchException;
import plugins.Library.index.Request;
import plugins.Library.serial.TaskAbortException;


/**
 * The xml index format
 * @author MikeB
 */
public class XMLIndex implements Index, ClientGetCallback, RequestClient{

	final public static String MIME_TYPE = "application/xml";
	final public static String DEFAULT_FILE = "index.xml";

	private PluginRespirator pr;
	private Executor executor;
	private ClientGetter rootGetter;
	public enum FetchStatus{UNFETCHED, FETCHING, FETCHED, FAILED}
	protected FetchStatus fetchStatus = FetchStatus.UNFETCHED;
	protected HashMap<String, String> indexMeta  = new HashMap<String, String>();
	protected String indexuri;

	private HighLevelSimpleClient hlsc;

	/**
	 * Index format version:
	 * <ul>
	 * <li>1 = XMLSpider 8 to 33</li>
	 * </ul>
	 * Earlier format are no longer supported.
	 */
	private int version;
	private List<String> subIndiceList;
	private SortedMap<String, SubIndex> subIndice;
	private ArrayList<FindRequest> waitingOnMainIndex = new ArrayList<FindRequest>();


	/**
	 * Create an XMLIndex from a URI
	 * @param baseURI
	 *            Base URI of the index (exclude the <tt>index.xml</tt> part)
	 */
	public XMLIndex(String baseURI, PluginRespirator pr) throws InvalidSearchException {
		this.pr = pr;
		if (pr!=null)
			executor = pr.getNode().executor;

		if (!baseURI.endsWith("/"))
			baseURI += "/";
		indexuri = baseURI;

		// check if index is valid file or URI
		if(!Util.isValid(baseURI))
			throw new InvalidSearchException(baseURI + " is neither a valid file nor valid Freenet URI");


		if(pr!=null){
			hlsc = pr.getNode().clientCore.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS);
			hlsc.addEventHook(mainIndexListener);
		}
	}

	/**
	 * process the bucket containing the main index file
	 * @param bucket
	 */
	private void processRequests(Bucket bucket){
		try {
			InputStream is = bucket.getInputStream();
			parse(is);
			is.close();
			fetchStatus = FetchStatus.FETCHED;
			for(FindRequest req : waitingOnMainIndex)
				setdependencies(req);
			waitingOnMainIndex.clear();
		}catch(Exception e){
			fetchStatus = FetchStatus.FAILED;
			Logger.error(this, indexuri, e);
		} finally {
			bucket.free();
		}
	}

	/**
	 * Listener to receive events when fetching the main index
	 */
	ClientEventListener mainIndexListener = new ClientEventListener(){
		/**
		 * Hears an event.
		 **/
		public void receive(ClientEvent ce, ObjectContainer maybeContainer, ClientContext context){
			FindRequest.updateWithEvent(waitingOnMainIndex, ce);
//			Logger.normal(this, "Updated with event : "+ce.getDescription());
		}

		public void onRemoveEventProducer(ObjectContainer container){
			throw new UnsupportedOperationException();
		}
	};

	/**
	 * Callback for when index fetching completes
	 */
	/** Called on successful fetch */
	public void onSuccess(FetchResult result, ClientGetter state, ObjectContainer container){
//		Logger.normal(this, "Fetch successful " + toString());
		processRequests(result.asBucket());
	}

	/** Called on failed/canceled fetch */
	public void onFailure(FetchException e, ClientGetter state, ObjectContainer container){
		if(e.newURI!=null){
			try {
				indexuri = e.newURI.setMetaString(null).toString();
				startFetch();
				return;
			} catch (FetchException ex) {
				Logger.error(this, "what?", ex);
			} catch (MalformedURLException ex) {
				Logger.error(this, "what?", ex);
			}
		}
		fetchStatus = FetchStatus.FAILED;
		Logger.error(this, "Fetch failed on "+toString() + " -- state = "+state, e);
	}

	public void onMajorProgress(ObjectContainer container){}

	public boolean persistent() {
		return false;
	}

	public void removeFrom(ObjectContainer container) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Fetch main index & process if local or fetch in background with callback if Freenet URI
	 * @throws freenet.client.FetchException
	 * @throws java.net.MalformedURLException
	 */
	private synchronized void startFetch() throws FetchException, MalformedURLException {
		if (fetchStatus != FetchStatus.UNFETCHED && fetchStatus != FetchStatus.FAILED)
			return;
		fetchStatus = FetchStatus.FETCHING;
		String uri = indexuri + DEFAULT_FILE;


		// try local file first
		File file = new File(uri);
		if (file.exists() && file.canRead()) {
			processRequests(new FileBucket(file, true, false, false, false, false));
			return;
		}

//		Logger.normal(this, "Fetching "+uri);
		// FreenetURI, try to fetch from freenet
		FreenetURI u = new FreenetURI(uri);
		while (true) {
			try {
				rootGetter = hlsc.fetch(u, -1, this, this, hlsc.getFetchContext().clone());
//				Logger.normal(this, "Fetch started : "+toString());
				break;
			} catch (FetchException e) {
				if (e.newURI != null) {
					u = e.newURI;
					continue;
				} else
					throw e;
			}
		}
	}

	/**
	 * Parse the xml in the main index to read fields for this object
	 * @param is InputStream for main index file
	 * @throws org.xml.sax.SAXException
	 * @throws java.io.IOException
	 */
	private void parse(InputStream is) throws SAXException, IOException {
		SAXParserFactory factory = SAXParserFactory.newInstance();
//		Logger.normal(this, "Parsing main index");

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
				subIndice = new TreeMap<String, SubIndex>();

				for (String key : parser.getSubIndice()) {
					subIndiceList.add(key);
					subIndice.put(key, new SubIndex(indexuri, "index_" + key + ".xml"));
				}
				Collections.sort(subIndiceList);
			}
		} catch (ParserConfigurationException e) {
			Logger.error(this, "SAX ParserConfigurationException", e);
			throw new SAXException(e);
		}
	}

	@Override
	public String toString(){
		String output = "Index:[ "+indexuri+" "+fetchStatus+" "+waitingOnMainIndex+"\n\t"+subIndice + (rootGetter==null?"]":(" GETTING("+rootGetter.toString()+")]"));
		//for (SubIndex s : subIndice)
		//	output = output+"\n -"+s;
		return output;
	}

	/**
	 * Gets the SubIndex object which should hold the keyword
	 */
	private SubIndex getSubIndex(String keyword) {
		String md5 = Library.MD5(keyword);
		int idx = Collections.binarySearch(subIndiceList, md5);
		if (idx < 0)
			idx = -idx - 2;
		return subIndice.get(subIndiceList.get(idx));
	}

	/**
	 * Find the term in this Index
	 */
	public synchronized Request getTermEntries(String term){
		try {
			FindRequest request = new FindRequest(term);
			setdependencies(request);
			notifyAll();
			return request;
		} catch (FetchException ex) {
			Logger.error(this, "Trying to find " + term, ex);
			return null;
		} catch (MalformedURLException ex) {
			Logger.error(this, "Trying to find " + term, ex);
			return null;
		}
	}

	public Request getURIEntry(FreenetURI uri){
		throw new UnsupportedOperationException("getURIEntry not Implemented in XMLIndex");
	}

	/**
	 * Puts request into the dependency List of either the main index or the
	 * subindex depending on whether the main index is availiable
	 * @param request
	 * @throws freenet.client.FetchException
	 * @throws java.net.MalformedURLException
	 */
	private synchronized void setdependencies(FindRequest request) throws FetchException, MalformedURLException{
//		Logger.normal(this, "setting dependencies for "+request+" on "+this.toString());
		if (fetchStatus!=FetchStatus.FETCHED){
			waitingOnMainIndex.add(request);
			request.setStage(FindRequest.Stages.FETCHROOT);
			startFetch();
		}else{
			request.setStage(FindRequest.Stages.FETCHSUBINDEX);
			SubIndex subindex = getSubIndex(request.getSubject());
			subindex.addRequest(request);
//			Logger.normal(this, "STarting "+getSubIndex(request.getSubject())+" to look for "+request.getSubject());
			if(executor!=null)
				executor.execute(subindex, "Subindex:"+subindex.getFileName());
			else
				(new Thread(subindex, "Subindex:"+subindex.getFileName())).start();
		}
	}


	/**
	 * @return the uri of this index prefixed with "xml:" to show what type it is
	 */
	public String getIndexURI(){
		return indexuri;
	}

	private class SubIndex implements Runnable {
		String indexuri, filename;
		private final ArrayList<FindRequest> waitingOnSubindex=new ArrayList<FindRequest>();
		FetchStatus fetchStatus = FetchStatus.UNFETCHED;
		HighLevelSimpleClient hlsc;
		Bucket bucket;
		Exception error;

		/**
		 * Listens for progress on a subIndex fetch
		 */
		ClientEventListener subIndexListener = new ClientEventListener(){
			/**
			 * Hears an event and updates those Requests waiting on this subindex fetch
			 **/
			public void receive(ClientEvent ce, ObjectContainer maybeContainer, ClientContext context){
				FindRequest.updateWithEvent(waitingOnSubindex, ce);
//				Logger.normal(this, "Updated with event : "+ce.getDescription());
			}
			public void onRemoveEventProducer(ObjectContainer container){
				throw new UnsupportedOperationException();
			}
		};

		SubIndex(String indexuri, String filename){
			this.indexuri = indexuri;
			this.filename = filename;

			if(pr!=null){
				hlsc = pr.getNode().clientCore.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS);
				hlsc.addEventHook(subIndexListener);
			}
		}

		String getFileName(){
			return filename;
		}

		FetchStatus getFetchStatus(){
			return fetchStatus;
		}

		/**
		 * Add a request to the List of requests looking in this subindex
		 * @param request
		 */
		void addRequest(FindRequest request){
			if(fetchStatus==FetchStatus.FETCHED)
				request.setStage(FindRequest.Stages.PARSE);
			else
				request.setStage(FindRequest.Stages.FETCHSUBINDEX);
			synchronized(waitingOnSubindex){
				waitingOnSubindex.add(request);
			}
		}

		@Override
		public String toString(){
			return filename+" "+fetchStatus+" "+waitingOnSubindex;
		}


		public synchronized void run(){
			try{
				while(waitingOnSubindex.size()>0){
					if(fetchStatus==FetchStatus.UNFETCHED || fetchStatus == FetchStatus.FAILED){
						try {
							fetchStatus = FetchStatus.FETCHING;
							// TODO tidy the fetch stuff
							bucket = Util.fetchBucket(indexuri + filename, hlsc);
							fetchStatus = FetchStatus.FETCHED;

						} catch (Exception e) {		// TODO tidy the exceptions
							//java.net.MalformedURLException
							//freenet.client.FetchException
							String msg = indexuri + filename + " could not be opened: " + e.toString();
							Logger.error(this, msg, e);
							throw new TaskAbortException(msg, e);
						}
					}else if(fetchStatus==FetchStatus.FETCHED){
						for(FindRequest r : waitingOnSubindex)
							r.setStage(FindRequest.Stages.PARSE);
						SAXParserFactory factory = SAXParserFactory.newInstance();
						try {
							factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
							SAXParser saxParser = factory.newSAXParser();
							InputStream is = bucket.getInputStream();
							///synchronized(waitingOnSubindex){
								saxParser.parse(is, new LibrarianHandler(waitingOnSubindex));
								Logger.minor(this, "parsing finished "+ waitingOnSubindex.toString());
								for (FindRequest findRequest : waitingOnSubindex) {
									findRequest.setFinished();
								}
								waitingOnSubindex.clear();
							///}
							is.close();
						} catch (Exception err) {
							//javax.xml.parsers.ParserConfigurationException
							//java.io.IOException
							//org.xml.sax.SAXException
							Logger.error(this, "Error parsing "+filename, err);
							throw new TaskAbortException("Could not parse XML: ", err);
						}
					} else {
						break;
					}
				}
			} catch (TaskAbortException e) {
				fetchStatus = FetchStatus.FAILED;
				this.error = e;
				Logger.error(this, "Dropping from subindex run loop", e);
				for (FindRequest r : waitingOnSubindex)
					r.setError(e);
			}
		}
	}
}
