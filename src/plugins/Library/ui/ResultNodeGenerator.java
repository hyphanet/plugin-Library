/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */


package plugins.Library.ui;

import plugins.Library.index.TermEntry;
import plugins.Library.index.TermIndexEntry;
import plugins.Library.index.TermPageEntry;
import plugins.Library.index.TermTermEntry;

import freenet.keys.FreenetURI;

import freenet.support.HTMLNode;
import freenet.support.Logger;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Class for parsing and formatting search results, once isDone() return true, the nodes are ready to use
 *
 * @author MikeB
 */
public class ResultNodeGenerator implements Runnable {
    private TreeMap<String, TermPageGroupEntry> groupmap;
    private TreeMap<TermPageEntry, Boolean> pageset;
    private TreeSet<TermTermEntry> relatedTerms;
    private TreeSet<TermIndexEntry> relatedIndexes;
    private Set<TermEntry> result;
    private boolean groupusk;
    private boolean showold;
    private boolean js;
    private boolean done;
    private HTMLNode pageEntryNode;
    private RuntimeException exception;

    /**
     * Create the generator
     * @param result Set of TermEntrys to format
     * @param groupusk whether the sites and editions should be grouped
     * @param showold whether to show older editions
     * @param js whether
     */
    public ResultNodeGenerator(Set<TermEntry> result, boolean groupusk, boolean showold,
                               boolean js) {
        this.result = result;
        this.groupusk = groupusk;
        this.showold = showold;
        this.js = js;
    }

    public synchronized void run() {
        if (done) {
            throw new IllegalStateException("ResultNodeGenerator can only be run once.");
        }

        try {
            parseResult();
            generatePageEntryNode();
        } catch (RuntimeException e) {
            exception =
                e;  // Exeptions thrown here are stored in case this is being run in a thread, in this case it is thrown in isDone() or iterator()

            throw e;
        }

        done = true;
        result = null;
    }

    /**
     * Return the generated HTMLNode of PageEntrys, only call this after checking isDone()
     * @throws RuntimeException if a RuntimeException was caught while generating the node
     */
    public HTMLNode getPageEntryNode() {
        if (exception != null) {
            throw new RuntimeException("RuntimeException thrown in ResultNodeGenerator thread",
                                       exception);
        }

        return pageEntryNode;
    }

    /**
     * Whether this ResultNodegenerator has finished formatting and get methods can be called
     * @throws RuntimeException if a RuntimeException was caught while generating the node
     */
    public boolean isDone() {
        if (exception != null) {
            throw new RuntimeException("RuntimeException thrown in ResultNodeGenerator thread",
                                       exception);
        }

        return done;
    }

    /**
     * Parse result into generator
     */
    private void parseResult() {
        groupmap = new TreeMap();

        if ( !groupusk) {
            pageset = new TreeMap(RelevanceComparator.comparator);
        }

        relatedTerms = new TreeSet(RelevanceComparator.comparator);
        relatedIndexes = new TreeSet(RelevanceComparator.comparator);

        Iterator<TermEntry> it = result.iterator();

        while (it.hasNext()) {
            TermEntry o = (TermEntry) it.next();

            if (o instanceof TermPageEntry) {
                TermPageEntry pageEntry = (TermPageEntry) o;

                // Put pages into a group hirearchy : USK key/docnames --> USK editions --> Pages
                String sitebase;
                long uskEdition = Long.MIN_VALUE;

                // Get the key and name
                FreenetURI uri;

                uri = pageEntry.page;

                // convert usk's
                if (uri.isSSKForUSK()) {
                    uri = uri.uskForSSK();

                    // Get the USK edition
                    uskEdition = uri.getEdition();
                }

                // Get the site base name, key + documentname - uskversion
                sitebase =
                    uri.setMetaString(null).setSuggestedEdition(0).toString().replaceFirst("/0",
                                      "");
                Logger.minor(this, sitebase);

                // Add site
                if ( !groupmap.containsKey(sitebase)) {
                    groupmap.put(sitebase, new TermPageGroupEntry(sitebase));
                }

                TermPageGroupEntry siteGroup = groupmap.get(sitebase);

                // Add page
                siteGroup.addPage(uskEdition, pageEntry);
            } else if (o instanceof TermTermEntry) {
                relatedTerms.add((TermTermEntry) o);
            } else if (o instanceof TermIndexEntry) {
                relatedIndexes.add((TermIndexEntry) o);
            } else {
                Logger.error(this, "Unknown TermEntry type : " + o.getClass().getName());
            }
        }

        if ( !groupusk) {  // Move entries from the groupmap to the pageset, marking whether thaey are the newest
            for (Iterator<TermPageGroupEntry> it2 = groupmap.values().iterator(); it2.hasNext(); ) {
                TermPageGroupEntry groupentry = it2.next();
                SortedMap<Long, SortedSet<TermPageEntry>> editions =
                    groupentry.getEditions();  // The editions of each site
                SortedSet<TermPageEntry> newest =
                    editions.get(editions.lastKey());  // get the newest edition

                for (Iterator<SortedSet<TermPageEntry>> editionIterator =
                        groupentry.getEditions().values().iterator();
                        editionIterator.hasNext(); ) {
                    SortedSet<TermPageEntry> edition =
                        editionIterator.next();  // Iterate through all the editions

                    if ( !showold &&
                            (edition != newest)) {  // If not showing old, skip all but newest
                        continue;
                    }

                    for (TermPageEntry termPageEntry : edition) {
                        pageset.put(
                            termPageEntry,
                            edition ==
                            newest);  // Add pages, marking whether they are from the newest edition
                    }
                }
            }

            groupmap = null;
        }
    }

    private HTMLNode generateIndexEntryNode() {
        return new HTMLNode("#", "TermIndexEntry code not done yet");
    }

    private HTMLNode generateTermEntryNode() {
        return new HTMLNode("#", "TermTermEntry code not done yet");
    }

    /**
     * Generate node of page results from this generator
     */
    private void generatePageEntryNode() {
        pageEntryNode = new HTMLNode("div", "id", "results");

        int results = 0;

        // Loop to separate results into SSK groups
        if (groupmap != null) {  // Produce grouped list of pages
            SortedSet<TermPageGroupEntry> groupSet = new TreeSet(RelevanceComparator.comparator);

            groupSet.addAll(groupmap.values());

            // Loop over keys
            Iterator<TermPageGroupEntry> it2 = groupSet.iterator();

            while (it2.hasNext()) {
                TermPageGroupEntry group = it2.next();
                String keybase = group.subj;
                SortedMap<Long, SortedSet<TermPageEntry>> siteMap = group.getEditions();
                HTMLNode siteNode = pageEntryNode.addChild("div", "style", "padding-bottom: 6px;");

                // Create a block for old versions of this SSK
                HTMLNode siteBlockOldOuter = siteNode.addChild("div", new String[] { "id",
                        "style" }, new String[] { "result-hiddenblock-" + keybase,
                        ( !showold ? "display:none" : "") });

                // put title on block if it has more than one version in it
                if (siteMap.size() > 1) {
                    siteBlockOldOuter.addChild("a", new String[] { "onClick", "name" },
                                               new String[] { "toggleResult('" + keybase + "')",
                            keybase }).addChild("h3", "class", "result-grouptitle",
                                                keybase.replaceAll("\\b.*/(.*)", "$1"));
                }

                // inner block for old versions to be hidden
                HTMLNode oldEditionContainer = siteBlockOldOuter.addChild("div",
                                                   new String[] { "class",
                        "style" }, new String[] { "result-hideblock",
                        "border-left: thick black;" });

                // Loop over all editions in this site
                Iterator<Long> it3 = siteMap.keySet().iterator();

                while (it3.hasNext()) {
                    long version = it3.next();
                    boolean newestVersion = !it3.hasNext();

                    if (newestVersion) {  // put older versions in block, newest outside block
                        oldEditionContainer = siteNode;
                    }

                    HTMLNode versionCell;
                    HTMLNode versionNode;

                    if ((siteMap.get(version).size() > 1) || (siteMap.size() > 1)) {

                        // table for this version
                        versionNode = oldEditionContainer.addChild("table",
                                new String[] { "class" }, new String[] { "librarian-result" });

                        HTMLNode grouptitle = versionNode.addChild("tr").addChild("td",
                                                  new String[] { "padding",
                                "colspan" }, new String[] { "0", "3" });

                        grouptitle.addChild(
                            "h4", "class", (newestVersion
                                            ? "result-editiontitle-new"
                                            : "result-editiontitle-old"), keybase.replaceAll(
                                                "\\b.*/(.*)", "$1") + ((version >= 0)
                                                    ? "-" + version : ""));

                        // Put link to show hidden older versions block if necessary
                        if (newestVersion && !showold && js && (siteMap.size() > 1)) {
                            grouptitle.addChild("a", new String[] { "href", "onClick" },
                                                new String[] { "#" + keybase,
                                    "toggleResult('" + keybase + "')" }, "       [" +
                                    (siteMap.size() - 1) + " older matching versions]");
                        }

                        HTMLNode versionrow = versionNode.addChild("tr");

                        versionrow.addChild("td", "width", "8px");

                        // draw black line down the side of the version
                        versionrow.addChild("td", new String[] { "class" },
                                            new String[] { "sskeditionbracket" });
                        versionCell = versionrow.addChild("td", "style", "padding-left:15px");
                    } else {
                        versionCell = oldEditionContainer;
                    }

                    // loop over each result in this version
                    Iterator<TermPageEntry> it4 = siteMap.get(version).iterator();

                    while (it4.hasNext()) {
                        versionCell.addChild(termPageEntryNode(it4.next(), newestVersion));
                        results++;
                    }
                }
            }
        } else {  // Just produce sorted list of results
            for (Iterator<Entry<TermPageEntry, Boolean>> it = pageset.entrySet().iterator();
                    it.hasNext(); ) {
                Entry<TermPageEntry, Boolean> entry = it.next();
                TermPageEntry termPageEntry = entry.getKey();
                boolean newestVersion = entry.getValue();

                pageEntryNode.addChild("div").addChild(termPageEntryNode(termPageEntry,
                        newestVersion));
                results++;
            }
        }

        pageEntryNode.addChild("p").addChild("span", "class", "librarian-summary-found",
                               "Found " + results + " results");
    }

    /**
     * Returns an {@link HTMLNode} representation of a {@link TermPageEntry} for display in a browser
     * @param entry
     * @param newestVersion if set, the result is shown in full brightness, if unset the result is greyed out
     */
    private HTMLNode termPageEntryNode(TermPageEntry entry, boolean newestVersion) {
        FreenetURI uri = entry.page;
        String showtitle = entry.title;
        String showurl = uri.toShortString();

        if ((showtitle == null) || (showtitle.trim().length() == 0)) {
            showtitle = showurl;
        }

        String realurl = "/" + uri.toString();
        HTMLNode pageNode = new HTMLNode("div", new String[] { "class", "style" },
                                new String[] { "result-entry",
                "" });

        pageNode.addChild("a", new String[] { "href", "class", "title" }, new String[] { realurl,
                (newestVersion ? "result-title-new" : "result-title-old"),
                (entry.rel > 0)
                ? "Relevance : " + (entry.rel * 100) + "%" : "Relevance unknown" }, showtitle);

        // create usk url
        if (uri.isSSKForUSK()) {
            String realuskurl = "/" + uri.uskForSSK().toString();

            pageNode.addChild("a", new String[] { "href", "class", "title" },
                              new String[] { realuskurl,
                    (newestVersion ? "result-uskbutton-new" : "result-uskbutton-old"),
                    realuskurl }, "[ USK ]");
        }

        pageNode.addChild("br");
        pageNode.addChild("a", new String[] { "href", "class", "title" }, new String[] { realurl,
                (newestVersion ? "result-url-new" : "result-url-old"), uri.toString() }, showurl);

        return pageNode;
    }
}
