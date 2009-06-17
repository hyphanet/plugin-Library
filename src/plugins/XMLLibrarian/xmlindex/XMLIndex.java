package plugins.XMLLibrarian.xmlindex;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import freenet.pluginmanager.PluginRespirator;
import freenet.client.FetchException;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.client.FetchResult;
import freenet.client.events.ClientEventListener;
import freenet.client.HighLevelSimpleClient;
import freenet.node.RequestStarter;
import freenet.client.events.ClientEvent;
import freenet.node.RequestClient;
import com.db4o.ObjectContainer;
import freenet.client.async.ClientContext;
import freenet.support.io.FileBucket;
import freenet.keys.FreenetURI;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.SortedMap;

import plugins.XMLLibrarian.Index;
import plugins.XMLLibrarian.InvalidSearchException;
import plugins.XMLLibrarian.Request;
import plugins.XMLLibrarian.Search;
import plugins.XMLLibrarian.Status;
import plugins.XMLLibrarian.URIWrapper;
import plugins.XMLLibrarian.XMLLibrarian;


public class XMLIndex extends Index{
	static final String DEFAULT_FILE = "index.xml";

	private HighLevelSimpleClient hlsc;

	/**
	 * Index format version:
	 * <ul>
	 * <li>1 = XMLSpider 8 to 33</li>
	 * </ul>
	 * Earlier format are no longer supported.
	 */
	protected int version;

	protected List<String> subIndiceList;
	protected SortedMap<String, SubIndex> subIndice;
	protected int downloadProgress;
	protected int downloadSize;

	/**
	 * @param baseURI
	 *            Base URI of the index (exclude the <tt>index.xml</tt> part)
	 */
	public XMLIndex(String baseURI) throws InvalidSearchException {
		super();
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
	 * Fetch the main index file
	 * 
	 * @throws IOException
	 * @throws FetchException
	 * @throws SAXException
	 */ 
	private synchronized void fetch() throws IOException, FetchException, SAXException {
        fetch(null);
    }
	private synchronized void fetch(Search search) throws IOException, FetchException, SAXException {
		if (fetchStatus != FetchStatus.UNFETCHED)
			return;
		fetchStatus = FetchStatus.FETCHING;
		
        if(search!=null) search.setprogress("Getting base index");
		Bucket bucket = Util.fetchBucket(indexuri + DEFAULT_FILE, search);
        if(search!=null) search.setprogress("Fetched base index");
		try {
			InputStream is = bucket.getInputStream();
			parse(is);
			is.close();
		} finally {
			bucket.free();
		}

		fetchStatus = FetchStatus.FETCHED;
	}
	
	private void processRequests(Bucket bucket){
		try {
			InputStream is = bucket.getInputStream();
			parse(is);
			is.close();
			fetchStatus = FetchStatus.FETCHED;
			for(Request req : waitingOnMainIndex)
				setdependencies(req);
			waitingOnMainIndex.clear();
		}catch(Exception e){
			fetchStatus = FetchStatus.FAILED;
		} finally {
			bucket.free();
		}
	}
	
	
	ClientEventListener mainIndexListener = new ClientEventListener(){
		/**
		 * Hears an event.
		 * @param container The database context the event was generated in.
		 * NOTE THAT IT MAY NOT HAVE BEEN GENERATED IN A DATABASE CONTEXT AT ALL:
		 * In this case, container will be null, and you should use context to schedule a DBJob.
		 **/
		public void receive(ClientEvent ce, ObjectContainer maybeContainer, ClientContext context){
			mainIndexDescription = ce.getDescription();
			downloadProgress = Integer.parseInt(ce.getDescription().split("[/\b]")[3]);
			downloadSize = Integer.parseInt(ce.getDescription().split("[/\b]")[4]);
		}

		/**
		 * Called when the EventProducer gets removeFrom(ObjectContainer).
		 * If the listener is the main listener which probably called removeFrom(), it should do nothing.
		 * If it's a tag-along but request specific listener, it may need to remove itself.
		 */
		public void onRemoveEventProducer(ObjectContainer container){}
	};
	
	ClientGetCallback mainIndexCallback = new ClientGetCallback(){
		/** Called on successful fetch */
		public void onSuccess(FetchResult result, ClientGetter state, ObjectContainer container){
			processRequests(result.asBucket());
		}

		/** Called on failed/canceled fetch */
		public void onFailure(FetchException e, ClientGetter state, ObjectContainer container){
			fetchStatus = FetchStatus.FAILED;
		}
		
		public void onMajorProgress(ObjectContainer container){}
	};
	
	private synchronized void startFetch() throws IOException, FetchException, SAXException {
		if (fetchStatus != FetchStatus.UNFETCHED)
			return;
		fetchStatus = FetchStatus.FETCHING;
		String uri = indexuri + DEFAULT_FILE;

		// try local file first
		File file = new File(uri);
		if (file.exists() && file.canRead()) {
			downloadProgress = -1;
			processRequests(new FileBucket(file, true, false, false, false, false));
			return;
		}

		// FreenetURI, try to fetch from freenet
		FreenetURI u = new FreenetURI(uri);
		while (true) {
			try {
				hlsc.fetch(u, -1, (RequestClient)hlsc, mainIndexCallback, hlsc.getFetchContext());
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
	
	public String toString(){
		String output = "Index : "+indexuri+" "+fetchStatus+" "+mainIndexDescription+" "+waitingOnMainIndex+"\n\t"+subIndice;
		//for (SubIndex s : subIndice)
		//	output = output+"\n -"+s;
		return output;
	}
	
	protected SubIndex getSubIndex(String keyword) {
		String md5 = XMLLibrarian.MD5(keyword);
		int idx = Collections.binarySearch(subIndiceList, md5);
		if (idx < 0)
			idx = -idx - 2;
		return subIndice.get(subIndiceList.get(idx));
	}

	public synchronized Request find(String term) throws Exception {
		Request request = new Request(Request.RequestType.FIND, term);
		requests.add(request);
		setdependencies(request);
		notifyAll();
		return request;
	}
	
	private synchronized void setdependencies(Request request)throws Exception{
		if (fetchStatus!=FetchStatus.FETCHED){
			waitingOnMainIndex.add(request);
			request.setStage(Request.RequestStatus.INPROGRESS,1, this);
			startFetch();
		}else if (!getSubIndex(request.getSubject()).isAlive()){
			SubIndex subindex = getSubIndex(request.getSubject());
			request.setStage(Request.RequestStatus.INPROGRESS,2, subindex);
			subindex.addRequest(request);
			if(subindex.getFetchStatus()==FetchStatus.UNFETCHED)
				subindex.start();
		}
	}
	

			

	public List<URIWrapper> search(Search search) throws Exception {
		fetch(search);
		List<URIWrapper> result = new LinkedList<URIWrapper>();
		String subIndex = getSubIndex(search.getQuery()).getFileName();
		
		// TODO make sure each subindex only gets fetched & parsed once
		try {
			search.setprogress("Getting subindex "+subIndex+" to search for "+search.getQuery());
			Bucket bucket = Util.fetchBucket(indexuri + subIndex, search);
			search.setprogress("Fetched subindex "+subIndex+" to search for "+search.getQuery());

			SAXParserFactory factory = SAXParserFactory.newInstance();
			try {
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				SAXParser saxParser = factory.newSAXParser();
				InputStream is = bucket.getInputStream();
				search.setprogress("Fetched subindex "+subIndex+" to search for "+search.getQuery());
				saxParser.parse(is, new LibrarianHandler(search.getQuery(), result));
				is.close();
			} catch (Throwable err) {
				err.printStackTrace();
				throw new Exception("Could not parse XML: " + err.toString());
			} finally {
				bucket.free();
			}
		} catch (Exception e) {
			Logger.error(this, indexuri + subIndex + " could not be opened: " + e.toString(), e);
			throw e;
		}
		return result;
	}
	
	public long getDownloadedBlocks(){
		return downloadProgress;
	}
	
	private class SubIndex extends Thread implements Status{
		String indexuri, filename;
		ArrayList<Request> waitingOnSubindex=new ArrayList<Request>();
		FetchStatus fetchStatus = FetchStatus.UNFETCHED;
		long downloadProgress, downloadSize;
		HighLevelSimpleClient hlsc;
		Bucket bucket;
		Exception error;
		
		ClientEventListener subIndexListener = new ClientEventListener(){
			/**
			 * Hears an event.
			 **/
			public void receive(ClientEvent ce, ObjectContainer maybeContainer, ClientContext context){
				//mainIndexDescription = ce.getDescription();
				downloadProgress = Integer.parseInt(ce.getDescription().split("[/\b]")[3]);
				downloadSize = Integer.parseInt(ce.getDescription().split("[/\b]")[4]);
			}
			public void onRemoveEventProducer(ObjectContainer container){}
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
		
		void addRequest(Request request){
			synchronized(waitingOnSubindex){
				waitingOnSubindex.add(request);
			}
		}
		
		public long getDownloadedBlocks(){
			return downloadProgress;
		}
		
		public String toString(){
			return filename+" "+fetchStatus+" "+waitingOnSubindex;
		}
		
		public synchronized void run(){ // TODO  conditions and simultanious parsing and file list
			try{
				while(waitingOnSubindex.size()>0){
					if(fetchStatus==FetchStatus.UNFETCHED){
						try {
							fetchStatus = FetchStatus.FETCHING;
							bucket = Util.fetchBucket(indexuri + filename, hlsc);
							fetchStatus = FetchStatus.FETCHED;
							
						} catch (Exception e) {
							Logger.error(this, indexuri + filename + " could not be opened: " + e.toString(), e);
							throw e;
						}
					}else if(fetchStatus==FetchStatus.FETCHED){
						SAXParserFactory factory = SAXParserFactory.newInstance();
						try {
							factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
							SAXParser saxParser = factory.newSAXParser();
							InputStream is = bucket.getInputStream();
							synchronized(waitingOnSubindex){
								saxParser.parse(is, new LibrarianHandler(waitingOnSubindex));
								for(Request r : waitingOnSubindex)
									r.finished();
								waitingOnSubindex.clear();
							}
							is.close();
						} catch (Throwable err) {
							err.printStackTrace();
							throw new Exception("Could not parse XML: " + err.toString());
						}
					}else
						break;
				}
			}catch(Exception e){
				fetchStatus = FetchStatus.FAILED;
				this.error = e;
			}
		}
	}
}
