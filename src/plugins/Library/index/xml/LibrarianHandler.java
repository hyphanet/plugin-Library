/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */


package plugins.Library.index.xml;

import plugins.Library.index.TermPageEntry;

import freenet.support.Logger;

import freenet.keys.FreenetURI;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;

/**
 * Required for using SAX parser on XML indices
 *
 * @author swati
 * @author MikeB
 *
 */
public class LibrarianHandler extends DefaultHandler {
    private boolean processingWord;

    // The data about the pages referenced in this subindex

    /** file id -> uri */
    private HashMap<String, String> uris;

    /** file id -> title */
    private HashMap<String, String> titles;

    /** file id -> wordCount */
    private HashMap<String, Integer> wordCounts;

    // About the whole index
    private int totalFileCount;

    // Requests and matches being made
    private List<FindRequest> requests;
    private List<FindRequest> wordMatches;
    private int               inWordFileCount;

    // About the file tag being processed
    private StringBuilder   characters;
    private String          inFileTitle;
    private FreenetURI      inFileURI;
    private int             inFileWordCount;
    static volatile boolean logMINOR;
    static volatile boolean logDEBUG;

    static {
        Logger.registerClass(LibrarianHandler.class);
    }

    /**
     * Construct a LibrarianHandler to look for many terms
     * @param requests the requests wanting to be resolved by this LibrarianHandler,
     *        results are written back to them
     * @throws java.lang.Exception
     */
    public LibrarianHandler(List<FindRequest> requests) {
        this.requests = new ArrayList<FindRequest>(requests);

        for (FindRequest r : requests) {
            r.setResult(new HashSet<TermPageEntry>());
        }
    }

    @Override
    public void setDocumentLocator(Locator value) {}

    @Override
    public void endDocument() throws SAXException {}

    @Override
    public void startDocument() throws SAXException {
        if ((uris == null) || (titles == null)) {
            uris       = new HashMap<String, String>();
            titles     = new HashMap<String, String>();
            wordCounts = new HashMap<String, Integer>();
        }
    }

    @Override
    public void startElement(String nameSpaceURI, String localName, String rawName,
                             Attributes attrs)
            throws SAXException {
        if ((requests.size() == 0) && (wordMatches.size() == 0)) {
            return;
        }

        if (rawName == null) {
            rawName = localName;
        }

        String elt_name = rawName;

        if (elt_name.equals("files")) {
            processingWord = false;

            String fileCount = attrs.getValue("", "totalFileCount");

            if (fileCount != null) {
                this.totalFileCount = Integer.parseInt(fileCount);
            }

            if (logMINOR) {
                Logger.minor(this, "totalfilecount = " + this.totalFileCount);
            }
        }

        if (elt_name.equals("keywords")) {
            processingWord = true;
        }

        /*
         * looks for the word in the given subindex file if the word is found then the parser
         * fetches the corresponding fileElements
         */
        if (elt_name.equals("word")) {
            try {
                wordMatches = null;

                String match = attrs.getValue("v");

                if (requests != null) {
                    wordMatches = new ArrayList<FindRequest>();

                    for (Iterator<FindRequest> it = requests.iterator(); it.hasNext(); ) {
                        FindRequest r = it.next();

                        if (match.equals(r.getSubject())) {
                            wordMatches.add(r);
                            it.remove();

                            if (logMINOR) {
                                Logger.minor(this, "found word match " + wordMatches);
                            }
                        }
                    }

                    if (attrs.getValue("fileCount") != null) {
                        inWordFileCount = Integer.parseInt(attrs.getValue("fileCount"));
                    }
                }
            } catch (Exception e) {
                throw new SAXException(e);
            }
        }

        if (elt_name.equals("file")) {
            if ((processingWord == true) && (wordMatches != null)) {
                try {
                    String suri = uris.get(attrs.getValue("id"));

                    inFileURI = new FreenetURI(suri);

                    synchronized (this) {
                        if (titles.containsKey(attrs.getValue("id"))) {
                            inFileTitle = titles.get(attrs.getValue("id"));

                            if ((suri).equals(inFileTitle)) {
                                inFileTitle = null;
                            }
                        } else {
                            inFileTitle = null;
                        }

                        if (wordCounts.containsKey(attrs.getValue("id"))) {
                            inFileWordCount = wordCounts.get(attrs.getValue("id")).intValue();
                        } else {
                            inFileWordCount = -1;
                        }

                        characters = new StringBuilder();
                    }
                } catch (Exception e) {
                    Logger.error(this, "Index format may be outdated " + e.toString(), e);
                }
            } else if (processingWord == false) {
                try {
                    String id  = attrs.getValue("id");
                    String key = attrs.getValue("key");
                    int    l   = attrs.getLength();
                    String title;

                    synchronized (this) {
                        if (l >= 3) {
                            try {
                                title = attrs.getValue("title");
                                titles.put(id, title);
                            } catch (Exception e) {
                                Logger.error(this, "Index Format not compatible " + e.toString(),
                                             e);
                            }

                            try {
                                String wordCountString = attrs.getValue("wordCount");

                                if (wordCountString != null) {
                                    int wordCount = Integer.parseInt(attrs.getValue("wordCount"));

                                    wordCounts.put(id, wordCount);
                                }
                            } catch (Exception e) {

                                // if(logMINOR) Logger.minor(this, "No wordcount found " +
                                // e.toString(), e);
                            }
                        }

                        uris.put(id, key);
                    }
                } catch (Exception e) {
                    Logger.error(
                        this, "File id and key could not be retrieved. May be due to format clash",
                        e);
                }
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (processingWord && (wordMatches != null) && (characters != null)) {
            characters.append(ch, start, length);
        }
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qName) {
        if (processingWord && (wordMatches != null)) {
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

            for (FindRequest match : wordMatches) {
                Set<TermPageEntry> result    = match.getUnfinishedResult();
                float              relevance = 0;

//              if(logMINOR) Logger.minor(this, "termcount "+termpositions.size()+" filewordcount =
//                                               "+inFileWordCount);
                if ((termpositions != null) && (termpositions.size() > 0) &&
                        (inFileWordCount > 0)) {
                    relevance = (float) (termpositions.size() / (float) inFileWordCount);

                    if ((totalFileCount > 0) && (inWordFileCount > 0)) {
                        relevance *= Math.log((float) totalFileCount / (float) inWordFileCount);
                    }

                    // if(logMINOR) Logger.minor(this, "Set relevance of "+pageEntry.getTitle()+
                    // " to "+pageEntry.rel+" - "+pageEntry.toString());
                }

                TermPageEntry pageEntry = new TermPageEntry(match.getSubject(), relevance,
                                              inFileURI, inFileTitle, termpositions);

                result.add(pageEntry);

                // if(logMINOR) Logger.minor(this, "added "+inFileURI+ " to "+ match);
            }
        }
    }
}
