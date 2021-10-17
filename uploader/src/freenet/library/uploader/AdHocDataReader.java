package freenet.library.uploader;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import plugins.Library.io.FreenetURI;
import plugins.Library.io.YamlReaderWriter;
import plugins.Library.io.serial.Packer;
import plugins.Library.io.serial.Packer.BinInfo;

class AdHocDataReader {
    /** Logger. */
    private static final Logger logger = Logger.getLogger(AdHocDataReader.class.getName());

    interface UriProcessor {
        public FreenetURI getURI();
        public int getLevel();
        public boolean processUri(FreenetURI uri);
	public void childrenSeen(int level, int foundChildren);
	public void uriSeen();
	public void stringSeen();
    }

    /**
     * Convert an object from the yaml to a FreenetURI.
     * 
     * The object can be a FreenetURI already (new style) or a string.
     *
     * @param obj
     * @return a FreenetURI
     * @throws MalformedURLException
     */
    private FreenetURI getFreenetURI(Object obj, UriProcessor uriProcessor) throws MalformedURLException {
        FreenetURI u;
        if (obj instanceof FreenetURI) {
            u = (FreenetURI) obj;
            uriProcessor.uriSeen();
        } else {
            u = new FreenetURI((String) obj);
            uriProcessor.stringSeen();
        }
        return u;
    }

   

    private int processBinInfoValues(Map<String, BinInfo> entries, UriProcessor uriProcessor)
        throws MalformedURLException {
        int foundChildren = 0;
        for (BinInfo value : entries.values()) {
            try {
                if (uriProcessor.processUri(getFreenetURI(value.getID(), uriProcessor))) {
                    foundChildren ++;
                }

            } catch (ClassCastException e) {
                throw new RuntimeException("Cannot process BinInfo value " + value.getID() + " for " + uriProcessor.getURI(), e);
            }
        }
        return foundChildren;
    }

    private int processSubnodes(Map<String, Object> map, UriProcessor uriProcessor)
        throws MalformedURLException {
        int foundChildren = 0; 
        Map<Object, Object> subnodes =
            (Map<Object, Object>) map.get("subnodes");
        for (Object key : subnodes.keySet()) {
            if (uriProcessor.processUri(getFreenetURI(key, uriProcessor))) {
                foundChildren ++;
            }
        }
        return foundChildren;
    }
    
    void readAndProcessYamlData(InputStream inputStream, UriProcessor uriProcessor, int page_level)
        throws IOException {
        int foundChildren = 0;
        try {
            Object readObject = new YamlReaderWriter().readObject(inputStream); 
            Map<String, Object> map = ((LinkedHashMap<String, Object>) readObject);
            if (map.containsKey("ttab") &&
                map.containsKey("utab") &&
                map.containsKey("totalPages")) {
                Map<String, Object> map2 = (Map<String, Object>) map.get("ttab");
                if (map2.containsKey("entries")) {
                    Map<String, BinInfo> entries =
                        (Map<String, Packer.BinInfo>) map2.get("entries");
                    foundChildren += processBinInfoValues(entries, uriProcessor);
                    if (logger.isLoggable(Level.FINER)) {
                        Map<Object, Object> subnodes =
                            (Map<Object, Object>) map2.get("subnodes");
                        logger.log(Level.FINER, "Contains ttab.entries (level {0}) with {1} subnodes", new Object[] {
                                uriProcessor.getLevel(),
                                subnodes.size(),
                            });
                    }
                    foundChildren += processSubnodes(map2, uriProcessor);
                    return;
                }
            }
            if (map.containsKey("lkey") &&
                map.containsKey("rkey") &&
                map.containsKey("entries")) {
                // Must separate map and array!
                if (map.containsKey("subnodes")) {
                    throw new RuntimeException("This parsing is not complex enough to handle subnodes for terms for " +
                                               uriProcessor.getURI());
                }
                if (map.get("entries") instanceof Map) {
                    Map<String, BinInfo> entries =
                        (Map<String, Packer.BinInfo>) map.get("entries");
                    logger.log(Level.FINE,
                               "Contains from {1} to {2} (level {0}) with {3} entries.",
                               new Object[] {
                                   uriProcessor.getLevel(),
                                   map.get("lkey"),
                                   map.get("rkey"),
                                   entries.size()
                               });
                    foundChildren += processBinInfoValues(entries, uriProcessor);
                    return;
                }
                if (map.get("entries") instanceof ArrayList) {
                    // Assuming this is a list of TermPageEntries.
                    logger.log(Level.FINE,
                               "Contains from {1} to {2} (level {0}) with page entries.",
                               new Object[] {
                                   uriProcessor.getLevel(),
                                   map.get("lkey"),
                                   map.get("rkey")
                               });
                    return;
                }
            }
            Entry<String, Object> entry = map.entrySet().iterator().next();
            if (entry.getValue() instanceof Map) {
                Map<String, Object> map2 = (Map<String, Object>) entry.getValue();
                if (map2.containsKey("node_min")
                    && map2.containsKey("size")
                    && map2.containsKey("entries")) {
                    String first = null;
                    String last = null;
                    for (Entry<String, Object> contents : map.entrySet()) {
                        if (contents.getValue() instanceof Map) {
                            if (first == null) {
                                first = contents.getKey();
                            }
                            last = contents.getKey();
                            Map<String, Object> map3 = (Map<String, Object>) contents.getValue();
                            if (map3.containsKey("subnodes")) {
                                foundChildren += processSubnodes(map3, uriProcessor);
                            }
                            continue;
                        }
                        throw new RuntimeException("Cannot process entries. Entry for " + contents.getKey() + " is not String=Map for " +
                                                   uriProcessor.getURI());
                    }
                    logger.log(Level.FINER, "Starts with entry for {1} and ended with entry {2} (level {0}).", new Object[] {
                            uriProcessor.getLevel(),
                            first,
                            last,
                        });
                    return;
                }
            }
            logger.severe("Cannot understand contents: " + map);
            System.exit(1);
        } finally {
            uriProcessor.childrenSeen(page_level, foundChildren);
            logger.exiting(AdHocDataReader.class.toString(),
                           "receivedAllData added " + foundChildren + " to the queue.");
        }

    }
}
