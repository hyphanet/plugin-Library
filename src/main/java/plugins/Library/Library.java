/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import plugins.Library.client.FreenetArchiver;
import plugins.Library.index.ProtoIndex;
import plugins.Library.index.ProtoIndexSerialiser;
import plugins.Library.index.xml.URLUpdateHook;
import plugins.Library.index.xml.XMLIndex;
import plugins.Library.io.ObjectStreamReader;
import plugins.Library.io.ObjectStreamWriter;
import plugins.Library.io.serial.Serialiser.PullTask;
import plugins.Library.search.InvalidSearchException;
import plugins.Library.util.exec.TaskAbortException;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchException.FetchExceptionMode;
import freenet.client.FetchResult;
import freenet.client.FetchWaiter;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetter;
import freenet.client.async.PersistenceDisabledException;
import freenet.client.async.USKCallback;
import freenet.client.async.USKManager;
import freenet.client.async.USKRetriever;
import freenet.client.async.USKRetrieverCallback;
import freenet.client.events.ClientEvent;
import freenet.client.events.ClientEventListener;
import freenet.client.events.ExpectedMIMEEvent;
import freenet.keys.FreenetURI;
import freenet.keys.USK;
import freenet.node.NodeClientCore;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.PluginStore;
import freenet.support.Executor;
import freenet.support.Logger;
import freenet.support.io.FileUtil;


/**
 * Library class is the api for others to use search facilities, it is used by the interfaces
 * @author MikeB
 */
final public class Library implements URLUpdateHook {

	public static final String BOOKMARK_PREFIX = "bookmark:";
	public static final String DEFAULT_INDEX_SITE = BOOKMARK_PREFIX + "liberty-of-information" + " " + BOOKMARK_PREFIX + "free-market-free-people" + " " +
	    BOOKMARK_PREFIX + "gotcha" + " " + BOOKMARK_PREFIX + "wanna" + " " + BOOKMARK_PREFIX + "wanna.old" + " " + BOOKMARK_PREFIX + "gogo";
	private static int version = 36;
	public static final String plugName = "Library " + getVersion();

	public static String getPlugName() {
		return plugName;
	}

	public static long getVersion() {
		return version;
	}

	/**
	** Library singleton.
	*/
	private static Library lib;

	public synchronized static Library init(PluginRespirator pr) {
		if (lib != null) {
			throw new IllegalStateException("Library already initialised");
		}
		lib = new Library(pr);
		return lib;
	}

	final private PluginRespirator pr;
	final private Executor exec;

	static volatile boolean logMINOR;
	static volatile boolean logDEBUG;
	
	public static final RequestClient REQUEST_CLIENT = new RequestClient() {

        @Override
        public boolean persistent() {
            return false;
        }

        @Override
        public boolean realTimeFlag() {
            return false;
        }
	    
	};

	static {
		Logger.registerClass(Library.class);
	}

	private final PluginStore store;

	static final String STOREKEY = "indexuris";

	/**
	 * Method to setup Library class so it has access to PluginRespirator, and load bookmarks
	 * TODO pull bookmarks from disk
	 */
	private Library(PluginRespirator pr){
		this.pr = pr;
		PluginStore ps;
		if(pr!=null) {
			this.exec = pr.getNode().executor;
			try {
				ps = pr.getStore();
			} catch (PersistenceDisabledException e) {
				ps = null;
			}
		} else {
			this.exec = null;
			ps = null;
		}
		USKManager uskManager = pr.getNode().clientCore.clientContext.uskManager;
		store = ps;
		if(store != null && store.subStores.containsKey(STOREKEY)) {
			for(Map.Entry<String, String> entry : store.subStores.get(STOREKEY).strings.entrySet()) {
				String name = entry.getKey();
				String target = entry.getValue();
				bookmarks.put(name, target);
			}
		}

		File persistentFile = new File("LibraryPersistent");
		boolean migrated = false;
		if(persistentFile.canRead()){
			try {
				ObjectInputStream is = new ObjectInputStream(new FileInputStream(persistentFile));	// These are annoying but it's better than nothing
				bookmarks = (Map<String, String>)is.readObject();
				is.close();
				FileUtil.secureDelete(persistentFile);
				Logger.error(this, "Moved LibraryPersistent contents into database and securely deleted old file.");
				migrated = true;
			} catch (ClassNotFoundException ex) {
				Logger.error(this, "Error trying to read bookmarks Map from file.", ex);
			} catch (IOException ex) {
				Logger.normal(this, "Error trying to read Library persistent data.", ex);
			}
		}
		boolean needNewWanna = false;
		for(Map.Entry<String, String> entry : bookmarks.entrySet()) {
			String name = entry.getKey();
			String target = entry.getValue();
			if(name.equals("wanna") && target.startsWith("USK@5hH~39FtjA7A9")) {
				name = "wanna.old";
				needNewWanna = true;
			}
			FreenetURI uri;
			long edition = -1;
			try {
				uri = new FreenetURI(target);
				if(uri.isUSK())
					edition = uri.getEdition();
			} catch (MalformedURLException e) {
				Logger.error(this, "Invalid bookmark URI: "+target+" for "+name, e);
				continue;
			}
			if(uri.isUSK()) {
				BookmarkCallback callback = new BookmarkCallback(name, uri.getAllMetaStrings(), edition);
				bookmarkCallbacks.put(name, callback);
				USK u;
				try {
					u = USK.create(uri);
				} catch (MalformedURLException e) {
					Logger.error(this, "Invalid bookmark USK: "+target+" for "+name, e);
					continue;
				}
				uskManager.subscribe(u, callback, false, rcBulk);
				callback.ret = uskManager.subscribeContent(u, callback, false, pr.getHLSimpleClient().getFetchContext(), RequestStarter.IMMEDIATE_SPLITFILE_PRIORITY_CLASS, rcBulk);
			}
		}
		if(bookmarks.isEmpty() || needNewWanna || !bookmarks.containsKey("gotcha") || 
		        !bookmarks.containsKey("liberty-of-information") ||
		        !bookmarks.containsKey("free-market-free-people")) {
		    if(!bookmarks.containsKey("liberty-of-information"))
		        addBookmark("liberty-of-information", "USK@5YCPzcs60uab6VSdiKcyvo-G-r-ga2UWCyOzWHaPoYE,a1zEPCf0qkUFQxftFZK5xmxYdxt0JErDb2aJgwG8s~4,AQACAAE/index.yml/25");
		    if(!bookmarks.containsKey("free-market-free-people"))
		        addBookmark("free-market-free-people", "USK@X4lMQ51bXPSicAgbR~XdFzDyizYHYrvzStdeUrIFhes,0ze4TAqd~RdAMZMsshybHZFema3ZP3id4sgN3H8969g,AQACAAE/index.yml/4");
		    if(!bookmarks.containsKey("gotcha"))
		        addBookmark("gotcha", "USK@zcAnAgT-xp5LnnK28-Lc7Qt-GU7pNKnVdkmU4-HCCBc,s2jiTh8~O9MtnGdVqJqgnKGrXrosK8rArcZ8A49hprY,AQACAAE/index.yml/6");
			if(!bookmarks.containsKey("wanna.old"))
				addBookmark("wanna.old", "USK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search/25/index.xml");
			if(!bookmarks.containsKey("freenetindex"))
				addBookmark("freenetindex", "USK@US6gHsNApDvyShI~sBHGEOplJ3pwZUDhLqTAas6rO4c,3jeU5OwV0-K4B6HRBznDYGvpu2PRUuwL0V110rn-~8g,AQACAAE/freenet-index/5/index.xml");
			if(!bookmarks.containsKey("gogo"))
				addBookmark("gogo", "USK@shmVvDhwivG1z1onSA5efRl3492Xyoov52hrC0tF6uI,wpMhseMpFHPLpXYbV8why1nfBXf2XdSQkzVaemFOEsA,AQACAAE/index.yml/51");
			if(!bookmarks.containsKey("wanna"))
				addBookmark("wanna", "USK@gxuHPaicqxlpPPagBKPVPraZ4bwLdMYBc5vipkWGh3E,08ExdmvZzB8Hfi6H6crbiuCd2~ikWDIpJ8dvr~tLp7k,AQACAAE/index.yml/82");
			migrated = true;
			Logger.normal(this, "Added default indexes");
		}
		if(migrated)
			saveState();
	}

	public synchronized void saveState(){
		if(store == null) return;
		PluginStore inner;
		inner = store.subStores.get(STOREKEY);
		if(inner == null)
			store.subStores.put(STOREKEY, inner = new PluginStore());
		inner.strings.clear();
		inner.strings.putAll(bookmarks);
		try {
			pr.putStore(store);
			if(logMINOR) Logger.minor(this, "Stored state to database");
		} catch (PersistenceDisabledException e) {
			// Not much we can do...
		}
	}
	
	/* FIXME
	 * Parallel fetch from the same index is not currently supported. This means that the only reliable way to
	 * do e.g. an intersection search is to run two completely separate fetches. Otherwise we get assertion 
	 * errors in e.g. BTreePacker.preprocessPullBins from TaskInProgressException's we can't handle from 
	 * ProgressTracker.addPullProgress, when the terms are in the same bucket. We should make it possible to
	 * search for multiple terms in the same btree, but for now, turning off caching is the only viable option.
	 */

//	/**
//	** Holds all the read-indexes.
//	*/
//	private Map<String, Index> rtab = new HashMap<String, Index>();
//
//	/**
//	** Holds all the writeable indexes.
//	*/
//	private Map<String, WriteableIndex> wtab = new HashMap<String, WriteableIndex>();
//
	/**
	** Holds all the bookmarks (aliases into the rtab).
	*/
	private Map<String, String> bookmarks = new HashMap<String, String>();

	private Map<String, BookmarkCallback> bookmarkCallbacks = new HashMap<String, BookmarkCallback>();

	/**
	** Get the index type giving a {@code FreenetURI}. This must not contain
	** a metastring (end with "/") or be a USK.
	*/
	public Class<?> getIndexType(FreenetURI indexuri) throws FetchException {
		if(indexuri.lastMetaString()!=null && indexuri.lastMetaString().equals(XMLIndex.DEFAULT_FILE))
			return XMLIndex.class;
		if(indexuri.lastMetaString()!=null && indexuri.lastMetaString().equals(ProtoIndex.DEFAULT_FILE))
			return ProtoIndex.class;
		if(indexuri.isUSK() && indexuri.getDocName().equals(ProtoIndex.DEFAULT_FILE))
			return ProtoIndex.class;

		NodeClientCore core = pr.getNode().clientCore;
		HighLevelSimpleClient hlsc = core.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS, false, false);

		List<FreenetURI> uris = Arrays.asList(indexuri,
		indexuri.pushMetaString(""),
		indexuri.pushMetaString(ProtoIndex.DEFAULT_FILE),
		indexuri.pushMetaString(XMLIndex.DEFAULT_FILE));

		for (FreenetURI uri: uris) {

			for(int i=0;i<5;i++) {
				ClientContext cctx = core.clientContext;
				FetchContext fctx = hlsc.getFetchContext();
				FetchWaiter fw = new FetchWaiter(REQUEST_CLIENT);
				final ClientGetter gu = hlsc.fetch(uri, 0x10000, fw, fctx);
				gu.setPriorityClass(RequestStarter.INTERACTIVE_PRIORITY_CLASS, cctx);
				
				final Class<?>[] c = new Class[1];
				hlsc.addEventHook(new ClientEventListener() {
					/*@Override**/ public void receive(ClientEvent ce, ClientContext context) {
						if (!(ce instanceof ExpectedMIMEEvent)) { return; }
						synchronized(c) {
							String type = ((ExpectedMIMEEvent)ce).expectedMIMEType;
							Logger.normal(this, "Expected type in index: "+type);
							try {
								c[0] = getIndexTypeFromMIME(type);
								gu.cancel(context);
							} catch (UnsupportedOperationException e) {
								// Ignore
							}
						}
					}
				});
				
				try {
					FetchResult res = fw.waitForCompletion();
					return getIndexTypeFromMIME(res.getMimeType());
					
				} catch (FetchException e) {
					if (e.getMode() == FetchExceptionMode.CANCELLED) {
						synchronized(c) {
							if(c[0] != null)
								return c[0];
							else
								throw new UnsupportedOperationException("Unable to get mime type or got an invalid mime type for index");
						}
					} else if(e.newURI != null) {
						uri = e.newURI;
						continue;
					}
				}
			}

		}
		throw new UnsupportedOperationException("Could not find appropriate data for index");
	}

	public Class<?> getIndexTypeFromMIME(String mime) {
		if (mime.equals(ProtoIndex.MIME_TYPE)) {
			//return "YAML index";
			return ProtoIndex.class;
		} else if (mime.equals(XMLIndex.MIME_TYPE)) {
			//return "XML index";
			return XMLIndex.class;
		} else {
			throw new UnsupportedOperationException("Unknown mime-type for index: "+mime);
		}
	}


/*
	KEYEXPLORER slightly more efficient version that depends on KeyExplorer

	/**
	** Get the index type giving a {@code FreenetURI}. This should have been
	** passed through {@link KeyExplorerUtils#sanitizeURI(List, String)} at
	** some point - ie. it must not contain a metastring (end with "/") or be
	** a USK.
	* /
	public Class<?> getIndexType(FreenetURI uri)
	throws FetchException, IOException, MetadataParseException, LowLevelGetException, KeyListenerConstructionException {
		GetResult getresult  = KeyExplorerUtils.simpleGet(pr, uri);
		byte[] data = BucketTools.toByteArray(getresult.getData());

		if (getresult.isMetaData()) {
			try {
				Metadata md = Metadata.construct(data);

				if (md.isArchiveManifest()) {
					if (md.getArchiveType() == ARCHIVE_TYPE.TAR) {
						return getIndexTypeFromManifest(uri, false, true);

					} else if (md.getArchiveType() == ARCHIVE_TYPE.ZIP) {
						return getIndexTypeFromManifest(uri, true, false);

					} else {
						throw new UnsupportedOperationException("not implemented - unknown archive manifest");
					}

				} else if (md.isSimpleManifest()) {
					return getIndexTypeFromManifest(uri, false, false);
				}

				return getIndexTypeFromSimpleMetadata(md);

			} catch (MetadataParseException e) {
				throw new RuntimeException(e);
			}
		} else {
			throw new UnsupportedOperationException("Found data instead of metadata; I do not have enough intelligence to decode this.");
		}
	}

	public Class<?> getIndexTypeFromSimpleMetadata(Metadata md) {
		String mime = md.getMIMEType();
		if (mime.equals(ProtoIndex.MIME_TYPE)) {
			//return "YAML index";
			return ProtoIndex.class;
		} else if (mime.equals(XMLIndex.MIME_TYPE)) {
			//return "XML index";
			return XMLIndex.class;
		} else {
			throw new UnsupportedOperationException("Unknown mime-type for index");
		}
	}

	public Class<?> getIndexTypeFromManifest(FreenetURI furi, boolean zip, boolean tar)
	throws FetchException, IOException, MetadataParseException, LowLevelGetException, KeyListenerConstructionException {

		boolean automf = true, deep = true, ml = true;
		Metadata md = null;

		if (zip)
			md = KeyExplorerUtils.zipManifestGet(pr, furi);
		else if (tar)
			md = KeyExplorerUtils.tarManifestGet(pr, furi, ".metadata");
		else {
			md = KeyExplorerUtils.simpleManifestGet(pr, furi);
			if (ml) {
				md = KeyExplorerUtils.splitManifestGet(pr, md);
			}
		}

		if (md.isSimpleManifest()) {
			// a subdir
			HashMap<String, Metadata> docs = md.getDocuments();
			Metadata defaultDoc = md.getDefaultDocument();

			if (defaultDoc != null) {
				//return "(default doc method) " + getIndexTypeFromSimpleMetadata(defaultDoc);
				return getIndexTypeFromSimpleMetadata(defaultDoc);
			}

			if (docs.containsKey(ProtoIndex.DEFAULT_FILE)) {
				//return "(doclist method) YAML index";
				return ProtoIndex.class;
			} else if (docs.containsKey(XMLIndex.DEFAULT_FILE)) {
				//return "(doclist method) XML index";
				return XMLIndex.class;
			} else {
				throw new UnsupportedOperationException("Could not find a supported index in the document-listings for " + furi.toString());
			}
		}

		throw new UnsupportedOperationException("Parsed metadata but did not reach a simple manifest: " + furi.toString());
	}
*/
	public Class<?> getIndexType(File f) {
		if (f.getName().endsWith(ProtoIndexSerialiser.FILE_EXTENSION))
			return ProtoIndex.class;
		if (f.isDirectory() && new File(f, XMLIndex.DEFAULT_FILE).canRead())
			return XMLIndex.class;

		throw new UnsupportedOperationException("Could not determine default index file under the path given: " + f);
	}

	public Object getAddressTypeFromString(String indexuri) {
		try {
			// return KeyExplorerUtils.sanitizeURI(new ArrayList<String>(), indexuri); KEYEXPLORER
			// OPT HIGH if it already ends with eg. *Index.DEFAULT_FILE, don't strip
			// the MetaString, and have getIndexType behave accordingly
			FreenetURI tempURI = new FreenetURI(indexuri);
//			if (tempURI.hasMetaStrings()) { tempURI = tempURI.setMetaString(null); }
//			if (tempURI.isUSK()) { tempURI = tempURI.sskForUSK(); }
			return tempURI;
		} catch (MalformedURLException e) {
			File file = new File(indexuri);
			if (file.canRead()) {
				return file;
			} else {
				throw new UnsupportedOperationException("Could not recognise index type from string: " + indexuri);
			}
		}
	}

	/**
	 * Add a new bookmark,
	 * TODO there should be a separate version for untrusted adds from freesites which throws some Security Exception
	 * @param name of new bookmark
	 * @param uri of new bookmark
	 * @return reference of new bookmark
	 */
	public String addBookmark(String name, String uri) {
		FreenetURI u;
		USK uskNew = null;
		BookmarkCallback callback = null;
		long edition = -1;
		try {
			u = new FreenetURI(uri);
			if(u.isUSK()) {
				uskNew = USK.create(u);
				edition = uskNew.suggestedEdition;
			}
		} catch (MalformedURLException e) {
			Logger.error(this, "Invalid new uri "+uri);
			return null;
		}
		String old;
		synchronized(this) {
			old = bookmarks.put(name, uri);
			callback = bookmarkCallbacks.get(name);
			if(callback == null) {
				bookmarkCallbacks.put(name, callback = new BookmarkCallback(name, u.getAllMetaStrings(), edition));
				old = null;
			}
			saveState();
		}
		boolean isSame = false;
		USKManager uskManager = pr.getNode().clientCore.clientContext.uskManager;
		if(old != null) {
			try {
				FreenetURI uold = new FreenetURI(old);
				if(uold.isUSK()) {
					USK usk = USK.create(uold);
					if(!(uskNew != null && usk.equals(uskNew, false))) {
						uskManager.unsubscribe(usk, callback);
						uskManager.unsubscribeContent(usk, callback.ret, true);
					} else
						isSame = true;
				}

			} catch (MalformedURLException e) {
				// Ignore
			}
		}
		if(!isSame) {
			uskManager.subscribe(uskNew, callback, false, rcBulk);
			callback.ret = uskManager.subscribeContent(uskNew, callback, false, pr.getHLSimpleClient().getFetchContext(), RequestStarter.IMMEDIATE_SPLITFILE_PRIORITY_CLASS, rcBulk);
		}
		return name;
	}

	final RequestClient rcBulk = new RequestClient() {

		public boolean persistent() {
			return false;
		}

		public boolean realTimeFlag() {
			return false;
		}

	};

	private class BookmarkCallback implements USKRetrieverCallback, USKCallback {

		private final String bookmarkName;
		private String[] metaStrings;
		USKRetriever ret;
		private long origEdition;

		public BookmarkCallback(String name, String[] allMetaStrings, long origEdition) {
			this.bookmarkName = name;
			this.metaStrings = allMetaStrings;
		}

		public short getPollingPriorityNormal() {
			return RequestStarter.UPDATE_PRIORITY_CLASS;
		}

		public short getPollingPriorityProgress() {
			return RequestStarter.IMMEDIATE_SPLITFILE_PRIORITY_CLASS;
		}

		public void onFound(USK origUSK, long edition, FetchResult data) {
			data.asBucket().free();
			if(logMINOR) Logger.minor(this, "Bookmark "+bookmarkName+" : fetching edition "+edition);
		}

		public void onFoundEdition(long l, USK key, ClientContext context, boolean metadata, short codec, byte[] data, boolean newKnownGood, boolean newSlotToo) {
			if(l < origEdition) {
				Logger.error(this, "Wrong edition: "+l+" less than "+origEdition);
				return;
			}
			if(newKnownGood) {
				String uri = key.copy(l).getURI().setMetaString(metaStrings).toString();
				if(logMINOR) Logger.minor(this, "Bookmark "+bookmarkName+" new last known good edition "+l+" uri is now "+uri);
				addBookmark(bookmarkName, uri);
			}
		}

	}

	public synchronized void removeBookmark(String name) {
		bookmarks.remove(name);
		saveState();
	}

	public synchronized String getBookmark(String bm) {
		return bookmarks.get(bm);
	}
	/**
	 * Returns the Set of bookmark names, unmodifiable
	 * @return The set of bookmarks
	 */
	public Set<String> bookmarkKeys() {
		return Collections.unmodifiableSet(bookmarks.keySet());
	}

	/**
	 * Returns a set of Index objects one for each of the uri's specified
	 * gets an existing one if its there else makes a new one
	 *
	 * @param indexuris list of index specifiers separated by spaces
	 * @return Set of Index objects
	 */
	public final ArrayList<Index> getIndices(String indexuris) throws InvalidSearchException, TaskAbortException {
		String[] uris = indexuris.split("[ ;]");
		ArrayList<Index> indices = new ArrayList<Index>(uris.length);

		for ( String uri : uris){
			indices.add(getIndex(uri, null));
		}
		return indices;
	}

	// See comments near rtab. Can't use in parallel so not acceptable.
//	/**
//	 * Method to get all of the instatiated Indexes
//	 */
//	public final Iterable<Index> getAllIndices() {
//		return rtab.values();
//	}
//
	public final Index getIndex(String indexuri) throws InvalidSearchException, TaskAbortException {
		return getIndex(indexuri, null);
	}

	/**
	 * Gets an index using its id in the form {type}:{uri} <br />
	 * known types are xml, bookmark
	 * @param indexid
	 * @return Index object
	 * @throws plugins.Library.util.InvalidSearchException
	 */
	/**
	 * Returns an Index object for the uri specified,
	 * gets an existing one if its there else makes a new one
	 *
	 * TODO : identify all index types so index doesn't need to refer to them directly
	 * @param indexuri index specifier
	 * @return Index object
	 */
	public final Index getIndex(String indexuri, String origIndexName) throws InvalidSearchException, TaskAbortException {
		Logger.normal(this, "Getting index "+indexuri);
		indexuri = indexuri.trim();
		if (indexuri.startsWith(BOOKMARK_PREFIX)){
			indexuri = indexuri.substring(BOOKMARK_PREFIX.length());
			if (bookmarks.containsKey(indexuri))
				return getIndex(bookmarks.get(indexuri), indexuri);
			else
				throw new InvalidSearchException("Index bookmark '"+indexuri+" does not exist");
		}

		// See comments near rtab. Can't use in parallel so caching is dangerous.
//		if (rtab.containsKey(indexuri))
//			return rtab.get(indexuri);
//
		Class<?> indextype;
		Index index;
		Object indexkey;

		try{
			indexkey = getAddressTypeFromString(indexuri);
		}catch(UnsupportedOperationException e){
			throw new TaskAbortException("Did not recognise index type in : \""+indexuri+"\"", e);
		}

		long edition = -1;

		try {
			if (indexkey instanceof File) {
				indextype = getIndexType((File)indexkey);
			} else if (indexkey instanceof FreenetURI) {
				// TODO HIGH make this non-blocking
				FreenetURI uri = (FreenetURI)indexkey;
				if(uri.isUSK())
					edition = uri.getEdition();
				indextype = getIndexType(uri);
			} else {
				throw new AssertionError();
			}

			if (indextype == ProtoIndex.class) {
				// TODO HIGH this *must* be non-blocking as it fetches the whole index root
				PullTask<ProtoIndex> task = new PullTask<ProtoIndex>(indexkey);
				ProtoIndexSerialiser.forIndex(indexkey, RequestStarter.INTERACTIVE_PRIORITY_CLASS).pull(task);
				index = task.data;

			} else if (indextype == XMLIndex.class) {
				index = new XMLIndex(indexuri, edition, pr, this, origIndexName);

			} else {
				throw new AssertionError();
			}

			// See comments near rtab. Can't use in parallel so caching is dangerous.
			//rtab.put(indexuri, index);
			Logger.normal(this, "Loaded index type " + indextype.getName() + " at " + indexuri);

			return index;

		} catch (FetchException e) {
			throw new TaskAbortException("Failed to fetch index " + indexuri+" : "+e, e, true); // can retry
/* KEYEXPLORER
		} catch (IOException e) {
			throw new TaskAbortException("Failed to fetch index " + indexuri, e, true); // can retry

		} catch (LowLevelGetException e) {
			throw new TaskAbortException("Failed to fetch index " + indexuri, e, true); // can retry

		} catch (KeyListenerConstructionException e) {
			throw new TaskAbortException("Failed to fetch index " + indexuri, e, true); // can retry

		} catch (MetadataParseException e) {
			throw new TaskAbortException("Failed to parse index  " + indexuri, e);
*/
		} catch (UnsupportedOperationException e) {
			throw new TaskAbortException("Failed to parse index  " + indexuri+" : "+e, e);

		} catch (RuntimeException e) {
			throw new TaskAbortException("Failed to load index  " + indexuri+" : "+e, e);

		}
	}


	/**
	** Create a {@link FreenetArchiver} connected to the core of the
	** singleton's {@link PluginRespirator}.
	**
	** @throws IllegalStateException if the singleton has not been initialised
	**         or if it does not have a respirator.
	*/
	public static <T> FreenetArchiver<T>
	makeArchiver(ObjectStreamReader r, ObjectStreamWriter w, String mime, int size, short priorityClass) {
		if (lib == null || lib.pr == null) {
			throw new IllegalStateException("Cannot archive to freenet without a fully live Library plugin connected to a freenet node.");
		} else {
			return new FreenetArchiver<T>(lib.pr.getNode().clientCore, r, w, mime, size, priorityClass);
		}
	}

	/**
	** Create a {@link FreenetArchiver} connected to the core of the
	** singleton's {@link PluginRespirator}.
	**
	** @throws IllegalStateException if the singleton has not been initialised
	**         or if it does not have a respirator.
	*/
	public static <T, S extends ObjectStreamWriter & ObjectStreamReader> FreenetArchiver<T>
	makeArchiver(S rw, String mime, int size, short priorityClass) {
		return Library.<T>makeArchiver(rw, rw, mime, size, priorityClass);
	}

	public static String convertToHex(byte[] data) {
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
	public static String MD5(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] b = text.getBytes("UTF-8");
			md.update(b, 0, b.length);
			byte[] md5hash = md.digest();
			return convertToHex(md5hash);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void update(String updateContext, String indexuri) {
		addBookmark(updateContext, indexuri);
	}
}
