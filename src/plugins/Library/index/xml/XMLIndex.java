/*
 * This code is part of Freenet. It is distributed under the GNU General Public License, version 2
 * (or at your option any later version). See http://www.gnu.org/ for further details of the GPL.
 */
package plugins.Library.index.xml;

import plugins.Library.Library;
import plugins.Library.Index;
import plugins.Library.index.TermEntry;
import plugins.Library.index.TermPageEntry;
import plugins.Library.index.URIEntry;
import plugins.Library.search.InvalidSearchException;
import plugins.Library.util.exec.Execution;
import plugins.Library.util.exec.TaskAbortException;
import freenet.support.Fields;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.io.FileBucket;
import freenet.support.io.ResumeFailedException;
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
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.TreeMap;
import java.util.SortedMap;
import java.util.HashMap;


/**
 * The xml index format
 * 
 * @author MikeB
 */
public class XMLIndex implements Index, ClientGetCallback {

  final public static String MIME_TYPE = "application/xml";
  final public static String DEFAULT_FILE = "index.xml";

  private PluginRespirator pr;
  private Executor executor;
  private ClientGetter rootGetter;
  private int fetchFailures = 0;

  public enum FetchStatus {
    UNFETCHED, FETCHING, FETCHED, FAILED
  }

  protected FetchStatus fetchStatus = FetchStatus.UNFETCHED;
  protected HashMap<String, String> indexMeta = new HashMap<String, String>();
  // TODO it could be tidier if this was converted to a FreenetURI
  protected String indexuri;
  protected long origEdition;
  private URLUpdateHook updateHook;
  private String updateContext;

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

  static volatile boolean logMINOR;
  static volatile boolean logDEBUG;

  static {
    Logger.registerClass(XMLIndex.class);
  }

  /**
   * Create an XMLIndex from a URI
   * 
   * @param baseURI Base URI of the index (exclude the <tt>index.xml</tt> part)
   */
  public XMLIndex(String baseURI, long edition, PluginRespirator pr, URLUpdateHook hook,
      String context) throws InvalidSearchException {
    this.pr = pr;
    this.origEdition = edition;
    this.updateHook = hook;
    this.updateContext = context;
    if (pr != null)
      executor = pr.getNode().executor;

    if (baseURI.endsWith(DEFAULT_FILE))
      baseURI = baseURI.replace(DEFAULT_FILE, "");
    if (!baseURI.endsWith("/"))
      baseURI += "/";
    indexuri = baseURI;

    // check if index is valid file or URI
    if (!Util.isValid(baseURI))
      throw new InvalidSearchException(baseURI + " is neither a valid file nor valid Freenet URI");


    if (pr != null) {
      hlsc = pr.getNode().clientCore.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS, false,
          false);
      hlsc.addEventHook(mainIndexListener);
    }
  }

  /**
   * process the bucket containing the main index file
   * 
   * @param bucket
   */
  private void processRequests(Bucket bucket) {
    try {
      InputStream is = bucket.getInputStream();
      parse(is);
      is.close();
      fetchStatus = FetchStatus.FETCHED;
      for (FindRequest req : waitingOnMainIndex)
        setdependencies(req);
      waitingOnMainIndex.clear();
    } catch (Exception e) {
      fetchStatus = FetchStatus.FAILED;
      for (FindRequest findRequest : waitingOnMainIndex) {
        findRequest.setError(new TaskAbortException("Failure parsing " + toString(), e));
      }
      Logger.error(this, indexuri, e);
    } finally {
      bucket.free();
    }
  }

  /**
   * Listener to receive events when fetching the main index
   */
  ClientEventListener mainIndexListener = new ClientEventListener() {
    /**
     * Hears an event.
     **/
    public void receive(ClientEvent ce, ClientContext context) {
      FindRequest.updateWithEvent(waitingOnMainIndex, ce);
      // Logger.normal(this, "Updated with event : "+ce.getDescription());
    }
  };

  /**
   * Callback for when index fetching completes
   */
  /** Called on successful fetch */
  public void onSuccess(FetchResult result, ClientGetter state) {
    // Logger.normal(this, "Fetch successful " + toString());
    processRequests(result.asBucket());
  }

  /** Called on failed/canceled fetch */
  public void onFailure(FetchException e, ClientGetter state) {
    fetchFailures++;
    if (fetchFailures < 20 && e.newURI != null) {
      try {
        if (logMINOR)
          Logger.minor(this, "Trying new URI: " + e.newURI);
        indexuri = e.newURI.setMetaString(new String[] {""}).toString();
        if (origEdition != -1 && e.newURI.getEdition() < origEdition) {
          Logger.error(this, "Redirect to earlier edition?!?!?!?: " + e.newURI.getEdition()
              + " from " + origEdition);
        } else {
          if (logMINOR)
            Logger.minor(this, "Trying new URI: " + e.newURI + " : " + indexuri);
          startFetch(true);
          if (updateHook != null && updateContext != null)
            updateHook.update(updateContext, indexuri);
          return;
        }
      } catch (FetchException ex) {
        e = ex;
      } catch (MalformedURLException ex) {
        Logger.error(this, "what?", ex);
      }
    }
    fetchStatus = FetchStatus.FAILED;
    for (FindRequest findRequest : waitingOnMainIndex) {
      findRequest
          .setError(new TaskAbortException("Failure fetching rootindex of " + toString(), e));
    }
    Logger.error(this, "Fetch failed on " + toString() + " -- state = " + state, e);
  }

  /**
   * Fetch main index & process if local or fetch in background with callback if Freenet URI
   * 
   * @throws freenet.client.FetchException
   * @throws java.net.MalformedURLException
   */
  private synchronized void startFetch(boolean retry) throws FetchException, MalformedURLException {
    if ((!retry) && (fetchStatus != FetchStatus.UNFETCHED && fetchStatus != FetchStatus.FAILED))
      return;
    fetchStatus = FetchStatus.FETCHING;
    String uri = indexuri + DEFAULT_FILE;


    // try local file first
    File file = new File(uri);
    if (file.exists() && file.canRead()) {
      processRequests(new FileBucket(file, true, false, false, false));
      return;
    }

    if (logMINOR)
      Logger.minor(this, "Fetching " + uri);
    // FreenetURI, try to fetch from freenet
    FreenetURI u = new FreenetURI(uri);
    while (true) {
      try {
        rootGetter = hlsc.fetch(u, -1, this, hlsc.getFetchContext().clone());
        Logger.normal(this, "Fetch started : " + toString());
        break;
      } catch (FetchException e) {
        if (e.newURI != null) {
          u = e.newURI;
          if (logMINOR)
            Logger.minor(this, "New URI: " + uri);
          continue;
        } else
          throw e;
      }
    }
  }

  /**
   * Parse the xml in the main index to read fields for this object
   * 
   * @param is InputStream for main index file
   * @throws org.xml.sax.SAXException
   * @throws java.io.IOException
   */
  private void parse(InputStream is) throws SAXException, IOException {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    // Logger.normal(this, "Parsing main index");

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
          String stillbase;
          try {
            FreenetURI furi = new FreenetURI(indexuri);
            stillbase = (furi.isUSK() ? furi.sskForUSK() : furi).toString();
          } catch (MalformedURLException e) {
            stillbase = indexuri;
          }
          subIndice.put(key, new SubIndex(stillbase, "index_" + key + ".xml"));
        }
        Collections.sort(subIndiceList);
      }
    } catch (ParserConfigurationException e) {
      Logger.error(this, "SAX ParserConfigurationException", e);
      throw new SAXException(e);
    }
  }

  @Override
  public String toString() {
    String output = "Index:[ " + indexuri + " " + fetchStatus + " " + waitingOnMainIndex + "\n\t"
        + subIndice + (rootGetter == null ? "]" : (" GETTING(" + rootGetter.toString() + ")]"));
    // for (SubIndex s : subIndice)
    // output = output+"\n -"+s;
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
  public synchronized Execution<Set<TermEntry>> getTermEntries(String term) {
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

  public Execution<URIEntry> getURIEntry(FreenetURI uri) {
    throw new UnsupportedOperationException("getURIEntry not Implemented in XMLIndex");
  }

  /**
   * Puts request into the dependency List of either the main index or the subindex depending on
   * whether the main index is availiable
   * 
   * @param request
   * @throws freenet.client.FetchException
   * @throws java.net.MalformedURLException
   */
  private synchronized void setdependencies(FindRequest request)
      throws FetchException, MalformedURLException {
    // Logger.normal(this, "setting dependencies for "+request+" on "+this.toString());
    if (fetchStatus != FetchStatus.FETCHED) {
      waitingOnMainIndex.add(request);
      request.setStage(FindRequest.Stages.FETCHROOT);
      startFetch(false);
    } else {
      request.setStage(FindRequest.Stages.FETCHSUBINDEX);
      SubIndex subindex = getSubIndex(request.getSubject());
      subindex.addRequest(request);
      // Logger.normal(this, "STarting "+getSubIndex(request.getSubject())+" to look for
      // "+request.getSubject());
      if (executor != null)
        executor.execute(subindex, "Subindex:" + subindex.getFileName());
      else
        (new Thread(subindex, "Subindex:" + subindex.getFileName())).start();
    }
  }


  /**
   * @return the uri of this index prefixed with "xml:" to show what type it is
   */
  public String getIndexURI() {
    return indexuri;
  }

  private class SubIndex implements Runnable {
    String indexuri, filename;
    private final ArrayList<FindRequest> waitingOnSubindex = new ArrayList<FindRequest>();
    private final ArrayList<FindRequest> parsingSubindex = new ArrayList<FindRequest>();
    FetchStatus fetchStatus = FetchStatus.UNFETCHED;
    HighLevelSimpleClient hlsc;
    Bucket bucket;
    Exception error;

    /**
     * Listens for progress on a subIndex fetch
     */
    ClientEventListener subIndexListener = new ClientEventListener() {
      /**
       * Hears an event and updates those Requests waiting on this subindex fetch
       **/
      public void receive(ClientEvent ce, ClientContext context) {
        FindRequest.updateWithEvent(waitingOnSubindex, ce);
        // Logger.normal(this, "Updated with event : "+ce.getDescription());
      }
    };

    SubIndex(String indexuri, String filename) {
      this.indexuri = indexuri;
      this.filename = filename;

      if (pr != null) {
        hlsc = pr.getNode().clientCore.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS, false,
            false);
        hlsc.addEventHook(subIndexListener);
      }
    }

    String getFileName() {
      return filename;
    }

    FetchStatus getFetchStatus() {
      return fetchStatus;
    }

    /**
     * Add a request to the List of requests looking in this subindex
     * 
     * @param request
     */
    void addRequest(FindRequest request) {
      if (fetchStatus == FetchStatus.FETCHED)
        request.setStage(FindRequest.Stages.PARSE);
      else
        request.setStage(FindRequest.Stages.FETCHSUBINDEX);
      synchronized (waitingOnSubindex) {
        waitingOnSubindex.add(request);
      }
    }

    @Override
    public String toString() {
      return filename + " " + fetchStatus + " " + waitingOnSubindex;
    }


    public synchronized void run() {
      try {
        while (waitingOnSubindex.size() > 0) {
          if (fetchStatus == FetchStatus.UNFETCHED || fetchStatus == FetchStatus.FAILED) {
            try {
              fetchStatus = FetchStatus.FETCHING;
              // TODO tidy the fetch stuff
              bucket = Util.fetchBucket(indexuri + filename, hlsc);
              fetchStatus = FetchStatus.FETCHED;

            } catch (Exception e) { // TODO tidy the exceptions
              // java.net.MalformedURLException
              // freenet.client.FetchException
              String msg = indexuri + filename + " could not be opened: " + e.toString();
              Logger.error(this, msg, e);
              throw new TaskAbortException(msg, e);
            }
          } else if (fetchStatus == FetchStatus.FETCHED) {
            parseSubIndex();
          } else {
            break;
          }
        }
      } catch (TaskAbortException e) {
        fetchStatus = FetchStatus.FAILED;
        this.error = e;
        Logger.error(this, "Dropping from subindex run loop", e);
        for (FindRequest r : parsingSubindex)
          r.setError(e);
        for (FindRequest r : waitingOnSubindex)
          r.setError(e);
      }
    }

    public void parseSubIndex() throws TaskAbortException {
      synchronized (parsingSubindex) {
        // Transfer all requests waiting on this subindex to the parsing list
        synchronized (waitingOnSubindex) {
          parsingSubindex.addAll(waitingOnSubindex);
          waitingOnSubindex.removeAll(parsingSubindex);
        }
        // Set status of all those about to be parsed to PARSE
        for (FindRequest r : parsingSubindex)
          r.setStage(FindRequest.Stages.PARSE);

        // Multi-stage parse to minimise memory usage.

        // Stage 1: Extract the declaration (first tag), copy everything before "<files " to one
        // bucket, plus everything after "</files>".
        // Copy the declaration, plus everything between the two (inclusive) to another bucket.

        Bucket mainBucket, filesBucket;

        try {
          InputStream is = bucket.getInputStream();
          mainBucket = pr.getNode().clientCore.tempBucketFactory.makeBucket(-1);
          filesBucket = pr.getNode().clientCore.tempBucketFactory.makeBucket(-1);
          OutputStream mainOS = new BufferedOutputStream(mainBucket.getOutputStream());
          OutputStream filesOS = new BufferedOutputStream(filesBucket.getOutputStream());
          // OutputStream mainOS = new BufferedOutputStream(new FileOutputStream("main.tmp"));
          // OutputStream filesOS = new BufferedOutputStream(new FileOutputStream("files.tmp"));

          BufferedInputStream bis = new BufferedInputStream(is);

          byte greaterThan = ">".getBytes("UTF-8")[0];
          byte[] filesPrefix = "<files ".getBytes("UTF-8");
          byte[] filesPrefixAlt = "<files>".getBytes("UTF-8");
          assert (filesPrefix.length == filesPrefixAlt.length);
          byte[] filesEnd = "</files>".getBytes("UTF-8");

          final int MODE_SEEKING_DECLARATION = 1;
          final int MODE_SEEKING_FILES = 2;
          final int MODE_COPYING_FILES = 3;
          final int MODE_COPYING_REST = 4;
          int mode = MODE_SEEKING_DECLARATION;
          int b;
          byte[] declarationBuf = new byte[100];
          int declarationPtr = 0;
          byte[] prefixBuffer = new byte[filesPrefix.length];
          int prefixPtr = 0;
          byte[] endBuffer = new byte[filesEnd.length];
          int endPtr = 0;
          while ((b = bis.read()) != -1) {
            if (mode == MODE_SEEKING_DECLARATION) {
              if (declarationPtr == declarationBuf.length)
                throw new TaskAbortException("Could not split up XML: declaration too long",
                    new Exception("bad xml"));
              declarationBuf[declarationPtr++] = (byte) b;
              mainOS.write(b);
              filesOS.write(b);
              if (b == greaterThan) {
                mode = MODE_SEEKING_FILES;
              }
            } else if (mode == MODE_SEEKING_FILES) {
              if (prefixPtr != prefixBuffer.length) {
                prefixBuffer[prefixPtr++] = (byte) b;
              } else {
                if (Fields.byteArrayEqual(filesPrefix, prefixBuffer)
                    || Fields.byteArrayEqual(filesPrefixAlt, prefixBuffer)) {
                  mode = MODE_COPYING_FILES;
                  filesOS.write(prefixBuffer);
                  filesOS.write(b);
                } else {
                  mainOS.write(prefixBuffer[0]);
                  System.arraycopy(prefixBuffer, 1, prefixBuffer, 0, prefixBuffer.length - 1);
                  prefixBuffer[prefixBuffer.length - 1] = (byte) b;
                }
              }
            } else if (mode == MODE_COPYING_FILES) {
              if (endPtr != endBuffer.length) {
                endBuffer[endPtr++] = (byte) b;
              } else {
                if (Fields.byteArrayEqual(filesEnd, endBuffer)) {
                  mode = MODE_COPYING_REST;
                  filesOS.write(endBuffer);
                  mainOS.write(b);
                } else {
                  filesOS.write(endBuffer[0]);
                  System.arraycopy(endBuffer, 1, endBuffer, 0, endBuffer.length - 1);
                  endBuffer[endBuffer.length - 1] = (byte) b;
                }
              }
            } else if (mode == MODE_COPYING_REST) {
              mainOS.write(b);
            }
          }

          if (mode != MODE_COPYING_REST)
            throw new TaskAbortException("Could not split up XML: Last mode was " + mode,
                new Exception("bad xml"));

          mainOS.close();
          filesOS.close();
        } catch (IOException e) {
          throw new TaskAbortException("Could not split XML: ", e);
        }

        if (logMINOR)
          Logger.minor(this, "Finished splitting XML");

        try {

          SAXParserFactory factory = SAXParserFactory.newInstance();
          factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
          SAXParser saxParser = factory.newSAXParser();

          // Stage 2: Parse the first bucket, find the keyword we want, find the file id's.

          InputStream is = mainBucket.getInputStream();
          StageTwoHandler stageTwoHandler = new StageTwoHandler();
          saxParser.parse(is, stageTwoHandler);
          if (logMINOR)
            Logger.minor(this, "Finished stage two XML parse");
          is.close();

          // Stage 3: Parse the second bucket, extract the <file>'s for the specific ID's.

          is = filesBucket.getInputStream();
          StageThreeHandler stageThreeHandler = new StageThreeHandler();
          saxParser.parse(is, stageThreeHandler);
          if (logMINOR)
            Logger.minor(this, "Finished stage three XML parse");
          is.close();

          Logger.minor(this, "parsing finished " + parsingSubindex.toString());
          for (FindRequest findRequest : parsingSubindex) {
            findRequest.setFinished();
          }
          parsingSubindex.clear();
        } catch (Exception err) {
          Logger.error(this, "Error parsing " + filename, err);
          throw new TaskAbortException("Could not parse XML: ", err);
        }
      }
    }

    class WordMatch {
      public WordMatch(ArrayList<FindRequest> searches, int inWordFileCount) {
        this.searches = searches;
        this.inWordFileCount = inWordFileCount;
      }

      final List<FindRequest> searches;
      int inWordFileCount;
    }

    class FileMatch {
      public FileMatch(String id2, HashMap<Integer, String> termpositions2,
          WordMatch thisWordMatch) {
        id = id2;
        termpositions = termpositions2;
        word = thisWordMatch;
      }

      final String id;
      final HashMap<Integer, String> termpositions;
      final WordMatch word;
    }

    Map<String, ArrayList<FileMatch>> idToFileMatches = new HashMap<String, ArrayList<FileMatch>>();

    int totalFileCount = -1;

    // Parse the main XML file, including the keywords list.
    // We do not see the files list.
    class StageTwoHandler extends DefaultHandler {

      private boolean processingWord;

      // Requests and matches being made
      private List<FindRequest> requests;
      private List<FindRequest> wordMatches;

      private int inWordFileCount;

      // About the file tag being processed
      private StringBuilder characters;

      private String match;

      private ArrayList<FileMatch> fileMatches = new ArrayList<FileMatch>();

      private String id;

      private WordMatch thisWordMatch;

      StageTwoHandler() {
        this.requests = new ArrayList(parsingSubindex);
        for (FindRequest r : parsingSubindex) {
          r.setResult(new HashSet<TermPageEntry>());
        }
      }

      @Override
      public void setDocumentLocator(Locator value) {

      }

      @Override
      public void endDocument() throws SAXException {}

      @Override
      public void startDocument() throws SAXException {
        // Do nothing
      }

      @Override
      public void startElement(String nameSpaceURI, String localName, String rawName,
          Attributes attrs) throws SAXException {
        if (requests.size() == 0 && (wordMatches == null || wordMatches.size() == 0))
          return;
        if (rawName == null) {
          rawName = localName;
        }
        String elt_name = rawName;

        if (elt_name.equals("keywords"))
          processingWord = true;

        /*
         * looks for the word in the given subindex file if the word is found then the parser
         * fetches the corresponding fileElements
         */
        if (elt_name.equals("word")) {
          try {
            fileMatches.clear();
            wordMatches = null;
            match = attrs.getValue("v");
            if (requests != null) {

              for (Iterator<FindRequest> it = requests.iterator(); it.hasNext();) {
                FindRequest r = it.next();
                if (match.equals(r.getSubject())) {
                  if (wordMatches == null)
                    wordMatches = new ArrayList<FindRequest>();
                  wordMatches.add(r);
                  it.remove();
                  Logger.minor(this, "found word match " + wordMatches);
                }
              }
              if (wordMatches != null) {
                if (attrs.getValue("fileCount") != null)
                  inWordFileCount = Integer.parseInt(attrs.getValue("fileCount"));
                thisWordMatch =
                    new WordMatch(new ArrayList<FindRequest>(wordMatches), inWordFileCount);
              }
            }
          } catch (Exception e) {
            throw new SAXException(e);
          }
        }

        if (elt_name.equals("file")) {
          if (processingWord == true && wordMatches != null) {
            try {
              id = attrs.getValue("id");
              characters = new StringBuilder();
            } catch (Exception e) {
              Logger.error(this, "Index format may be outdated " + e.toString(), e);
            }

          }
        }
      }

      @Override
      public void characters(char[] ch, int start, int length) {
        if (processingWord && wordMatches != null && characters != null) {
          characters.append(ch, start, length);
        }
      }

      @Override
      public void endElement(String namespaceURI, String localName, String qName) {
        if (processingWord && wordMatches != null && qName.equals("file")) {
          HashMap<Integer, String> termpositions = null;
          if (characters != null) {
            String[] termposs = characters.toString().split(",");
            termpositions = new HashMap<Integer, String>();
            for (String pos : termposs) {
              try {
                termpositions.put(Integer.valueOf(pos), null);
              } catch (NumberFormatException e) {
                Logger.error(this, "Position in index not an integer :" + pos, e);
              }
            }
            characters = null;
          }

          FileMatch thisFile = new FileMatch(id, termpositions, thisWordMatch);

          ArrayList<FileMatch> matchList = idToFileMatches.get(id);
          if (matchList == null) {
            matchList = new ArrayList<FileMatch>();
            idToFileMatches.put(id, matchList);
          }
          if (logMINOR)
            Logger.minor(this, "Match: id=" + id + " for word " + match);
          matchList.add(thisFile);
        }

      }

    }

    class StageThreeHandler extends DefaultHandler {

      @Override
      public void setDocumentLocator(Locator value) {

      }

      @Override
      public void endDocument() throws SAXException {}

      @Override
      public void startElement(String nameSpaceURI, String localName, String rawName,
          Attributes attrs) throws SAXException {

        if (idToFileMatches.isEmpty())
          return;
        if (rawName == null) {
          rawName = localName;
        }
        String elt_name = rawName;

        if (elt_name.equals("files")) {
          String fileCount = attrs.getValue("", "totalFileCount");
          if (fileCount != null)
            totalFileCount = Integer.parseInt(fileCount);
          Logger.minor(this, "totalfilecount = " + totalFileCount);
        }

        if (elt_name.equals("file")) {
          try {
            String id = attrs.getValue("id");

            ArrayList<FileMatch> matches = idToFileMatches.get(id);

            if (matches != null) {

              for (FileMatch match : matches) {

                String key = attrs.getValue("key");
                int l = attrs.getLength();
                String title = null;
                int wordCount = -1;
                if (l >= 3) {
                  try {
                    title = attrs.getValue("title");
                  } catch (Exception e) {
                    Logger.error(this, "Index Format not compatible " + e.toString(), e);
                  }
                  try {
                    String wordCountString = attrs.getValue("wordCount");
                    if (wordCountString != null) {
                      wordCount = Integer.parseInt(attrs.getValue("wordCount"));
                    }
                  } catch (Exception e) {
                    // Logger.minor(this, "No wordcount found " + e.toString(), e);
                  }
                }

                for (FindRequest req : match.word.searches) {

                  Set<TermPageEntry> result = req.getUnfinishedResult();
                  float relevance = 0;

                  if (logDEBUG)
                    Logger.debug(this,
                        "termcount "
                            + (match.termpositions == null ? 0 : match.termpositions.size())
                            + " filewordcount = " + wordCount);
                  if (match.termpositions != null && match.termpositions.size() > 0
                      && wordCount > 0) {
                    relevance = (float) (match.termpositions.size() / (float) wordCount);
                    if (totalFileCount > 0 && match.word.inWordFileCount > 0)
                      relevance *=
                          Math.log((float) totalFileCount / (float) match.word.inWordFileCount);
                    if (logDEBUG)
                      Logger.debug(this,
                          "Set relevance of " + title + " to " + relevance + " - " + key);
                  }

                  TermPageEntry pageEntry = new TermPageEntry(req.getSubject(), relevance,
                      new FreenetURI(key), title, match.termpositions);
                  result.add(pageEntry);
                  // Logger.minor(this, "added "+inFileURI+ " to "+ match);
                }

              }
            }

          } catch (Exception e) {
            Logger.error(this, "File id and key could not be retrieved. May be due to format clash",
                e);
          }
        }
      }

    }

  }

  @Override
  public void onResume(ClientContext context) throws ResumeFailedException {
    // Ignore. Requests not persistent.
  }

  @Override
  public RequestClient getRequestClient() {
    return Library.REQUEST_CLIENT;
  }

}
