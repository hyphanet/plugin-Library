/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package freenet.library.uploader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.pterodactylus.fcp.FcpConnection;

import freenet.library.FactoryRegister;
import freenet.library.index.TermEntry;
import freenet.library.util.exec.TaskAbortException;

/**
 * Standalone program to do the merging.
 *
 * The ambition is to avoid having the merger running in the freenet process,
 * instead run it as a separate java program.
 * 
 * It reads and removes the files created by the plugin (by Spider) and
 * delivers data using FCP.
 *
 * Initially it is the same jar used as plugin and for the separate process.
 *
 *
 * Initial logic:
 * Run once to merge once. If more than one of these merge jobs are
 * started at the time, the FcpConnection will fail to open since
 * they use the same name.
 * <ol> Check if there are directories to merge. If so, merge the first of them (order is not important). Done!
 * <ol> If there are no files to merge. Done!
 * <ol> Fetch the index top to get the top fan-out.
 * <ol> Get the first term in the first and create an index with all
 *      the contents from all the files with all terms from the same index.
 * <ol> While processing, group into some selected files. The rest goes
 *      into the filtered files.
 * <ol> Merge that index.
 * <ol> If there are selected files, get the first term from the first one of them instead.
 * <ol> If there is room for more in the same go, get the next selected file.
 * <ol> Rewrite all not filtered files getting the matched terms.
 * <ol> Done.
 */
final public class Merger {
    
    private static FcpSession session;

    private static final String SELECTED = UploaderPaths.BASE_FILENAME_DATA + "selected.";
    private static final String FILTERED = UploaderPaths.BASE_FILENAME_DATA + "filtered.";
    private static final String PROCESSED = UploaderPaths.BASE_FILENAME_DATA + "processed.";
    
    static final Comparator<String> comparator = new StringNumberComparator();
    
    static class StringNumberComparator implements Comparator<String> {
        @Override
        public int compare(String a, String b) {
            int ai;
            int bi;
            for (ai = 0, bi = 0; ai < a.length() && bi < b.length(); ai++, bi++) {
                if (a.substring(ai, ai + 1).matches("[0-9]")
                        && a.substring(bi, bi + 1).matches("[0-9]")) {
                    int aii;
                    for (aii = ai + 1; aii < a.length(); aii++) {
                        if (!a.substring(aii, aii + 1).matches("[0-9]")) {
                            break;
                        }
                    }
                    int bii;
                    for (bii = bi + 1; bii < b.length(); bii++) {
                        if (!b.substring(bii, bii + 1).matches("[0-9]")) {
                            break;
                        }
                    }
                    try {
                        int ret = Integer.valueOf(a.substring(ai, aii)).compareTo(
                                          Integer.valueOf(b.substring(bi, bii)));
                        if (ret != 0) {
                            return ret;
                        }
                        
                        ai = aii - 1;
                        bi = bii - 1;
                        continue;
                    } catch (NumberFormatException e) {
                        continue;
                    }
                }
                int ret = a.charAt(ai) - b.charAt(bi);
                if (ret != 0) {
                    return ret;
                }
            }
            if (ai < a.length()) {
                return 1;
            }
            if (bi < b.length()) {
                return -1;
            }
            return 0;
        }
    }

    /**
     * Return an array with the filenames in order.
     */
    static String[] getMatchingFiles(File directory,
            final String baseFilename) {
        String[] array = directory.list(new FilenameFilter() {
                            
                public boolean accept(File arg0, String arg1) {
                    if (!(arg1.toLowerCase().startsWith(baseFilename))) {
                        return false;
                    }
                    File f = new File(arg0, arg1);
                    if (!f.isFile()) {
                        return false;
                    }
                    if (f.length() == 0) {
                        f.delete();
                        return false;
                    }
                    return true;
                }
            });
        Arrays.sort(array, comparator);
        return array;
    }


    public static void main(String[] argv) {
        int exitStatus = 0;

        //if (!cwd.matches(".*/plugins")) {
        //    System.err.println("Should be started in the freenet directory.");
        //    System.exit(1);
        //}
        
        // Now we are in the Freenet directory. 
        // The rest of the work is done here.
        FcpConnection connection = null;

        try {
            String[] dirsToMerge = null;
            File directory = new File(".");
            for (String arg : argv) {
                if (new File(directory, arg).isDirectory()) {
                    dirsToMerge = new String[1];
                    dirsToMerge[0] = arg;
                } else {
                    System.out.println("No such directory " + arg);
                }
                break;
            }
            if (dirsToMerge == null) {
                dirsToMerge = directory.list(new FilenameFilter() {
                                    
                    public boolean accept(File arg0, String arg1) {
                        if(!(arg1.toLowerCase().startsWith(UploaderPaths.DISK_DIR_PREFIX))) return false;
                        return true;
                    }
                                    
                });
            }

            if (dirsToMerge.length > 0) {
                System.out.println("Merging directory " + dirsToMerge[0]);
                session = new FcpSession();
                connection = session.getConnection();
                UploaderLibrary.init(connection);
                FactoryRegister.register(UploaderLibrary.getInstance());
                
                File directoryToMerge = new File(directory, dirsToMerge[0]);
                new DirectoryUploader(connection, directoryToMerge).run();
                System.out.println("Upload completed.");
                return;
            }

            createMergeDirectory(directory);
        } catch (TaskAbortException | IllegalStateException | IOException e) {
            e.printStackTrace();
            exitStatus = 1;
        } finally {
            if (session != null) {
                session.close();
                if (exitStatus == 0) {
                    exitStatus = session.getStatus();
                }
            }
        }
        System.exit(exitStatus);
    }


    private static void createMergeDirectory(File directory) throws TaskAbortException {
        final String[] selectedFilesToMerge = getMatchingFiles(directory, SELECTED);
        System.out.println("There is " + selectedFilesToMerge.length + " selected files.");

        final String [] filteredFilesToMerge = getMatchingFiles(directory, FILTERED);
        System.out.println("There is " + filteredFilesToMerge.length + " filtered files.");

        final String [] processedFilesToMerge = getMatchingFiles(directory, PROCESSED);
        System.out.println("There is " + processedFilesToMerge.length + " processed files.");

        final String[] newFilesToMerge = getMatchingFiles(directory, UploaderPaths.BASE_FILENAME_PUSH_DATA);
        System.out.println("There is " + newFilesToMerge.length + " new files.");

        // Calculate the last number of filtered and processed files.
        int lastFoundNumber = 0;
        for (String filename : filteredFilesToMerge) {
            int numberFound = Integer.parseInt(filename.substring(FILTERED.length()));
            if (numberFound > lastFoundNumber) {
                lastFoundNumber = numberFound;
            }
        }
        for (String filename : processedFilesToMerge) {
            int numberFound = Integer.parseInt(filename.substring(PROCESSED.length()));
            if (numberFound > lastFoundNumber) {
                lastFoundNumber = numberFound;
            }
        }
        System.out.println("Last found: " + lastFoundNumber);

        int lastSelected = 0;
        for (String filename : selectedFilesToMerge) {
            int numberFound = Integer.parseInt(filename.substring(SELECTED.length()));
            if (numberFound > lastSelected) {
                lastSelected = numberFound;
            }
        }
        
        final DirectoryCreator creator = new DirectoryCreator(directory);

        Map<IndexPeeker, TermEntryFileWriter> writers =
                new HashMap<IndexPeeker, TermEntryFileWriter>();
        IndexPeeker creatorPeeker = new IndexPeeker(directory);

        Set<File> toBeRemoved = new HashSet<File>();
        
        class ProcessedFilenames implements Iterator<String> {
            String restBase;
            boolean createSelectedFiles = false;
            boolean processingSelectedFile = false;
            int movedTerms = 0;
            private boolean doSelected = false;
            private boolean doAllSelected = false;
            private boolean doFiltered = false;
            private boolean doProcessed = false;
            private boolean doNew = true;
            private int nextSelected = 0;
            private int nextFiltered = 0;
            private int nextProcessed = 0;
            private int nextNew = 0;

            ProcessedFilenames() {
                if (selectedFilesToMerge.length > 0) {
                    doSelected = true;
                    if (processedFilesToMerge.length > 1
                            && processedFilesToMerge.length * selectedFilesToMerge.length > filteredFilesToMerge.length) {
                        createSelectedFiles = true;
                        doAllSelected = true;
                        doFiltered = true;
                        restBase = FILTERED;
                    } else {
                        restBase = PROCESSED;
                    }
                } else {
                    createSelectedFiles = true;
                    doFiltered = true;
                    restBase = FILTERED;
                }
                doProcessed = true;
                doNew = true;
            }

            @Override
            public boolean hasNext() {
                if (doSelected && 
                        nextSelected < selectedFilesToMerge.length) {
                	return true;
                }
                if (doAllSelected && nextSelected < selectedFilesToMerge.length) {
                    return true;
                }
                if (doFiltered && nextFiltered < filteredFilesToMerge.length) {
                    return true;
                }
                if (doProcessed && nextProcessed < processedFilesToMerge.length) {
                    return true;
                }
                if (doNew && nextNew < newFilesToMerge.length) {
                    return true;
                }
                return false;
            }

            @Override
            public String next() {
                processingSelectedFile = false;
                if (doSelected && 
                        nextSelected < selectedFilesToMerge.length) {
                    processingSelectedFile = true;
                    doSelected = false;
                    return selectedFilesToMerge[nextSelected++];
                } else if (doAllSelected && nextSelected < selectedFilesToMerge.length) {
                    return selectedFilesToMerge[nextSelected++];
                } else if (doFiltered && nextFiltered < filteredFilesToMerge.length) {
                    return filteredFilesToMerge[nextFiltered++];
                } else if (doProcessed && nextProcessed < processedFilesToMerge.length) {
                    return processedFilesToMerge[nextProcessed++];
                } else if (doNew && nextNew < newFilesToMerge.length) {
                    return newFilesToMerge[nextNew++];
                } else {
                    throw new IllegalArgumentException("next() called after hasNext() returned false.");
                }
            }

            @Override
            public void remove() {
                throw new IllegalArgumentException("Not implemented");
            }
        };
        final ProcessedFilenames processedFilenames = new ProcessedFilenames();
        TermEntryFileWriter notMerged = null;

        int totalTerms = 0;

        for (String s : new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                return processedFilenames;
            }
        }) {
            System.out.println("File: " + s);
            File file = new File(s);
            FileInputStream fileInputStream;
            try {
                fileInputStream = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }
            TermEntryReaderIterator teri = new TermEntryReaderIterator(new DataInputStream(fileInputStream));
            Iterator<TermEntry> iterator = teri.iterator();
            while (iterator.hasNext()) {
                TermEntry tt = iterator.next();
                totalTerms ++;
                if (creatorPeeker.include(tt.subj)) {
                    creator.putEntry(tt);
                    processedFilenames.movedTerms ++;
                    continue;
                }
                
                if (processedFilenames.createSelectedFiles) {
                    // They are all to be sorted.
                    boolean found = false;
                    for (Map.Entry<IndexPeeker, TermEntryFileWriter> entry : writers.entrySet()) {
                        if (entry.getKey().include(tt.subj)) {
                            entry.getValue().write(tt);
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        continue;                       
                    } else if (writers.size() < 10 * (filteredFilesToMerge.length + processedFilesToMerge.length)) {
                        lastSelected ++;
                        String selectedFilename = SELECTED + lastSelected;
                        IndexPeeker p = new IndexPeeker(directory);
                        TermEntryFileWriter t = new TermEntryFileWriter(teri.getHeader(),
                                new File(directory, selectedFilename));
                        if (p.include(tt.subj)) {
                            writers.put(p, t);
                            t.write(tt);
                        }
                        continue;
                    }
                }
                if (notMerged == null) {
                    lastFoundNumber ++;
                    String restFilename = processedFilenames.restBase + lastFoundNumber;
                    notMerged = new TermEntryFileWriter(teri.getHeader(), new File(directory, restFilename));
                }
                notMerged.write(tt);
                if (notMerged.isFull()) {
                    notMerged.close();
                    notMerged = null;
                }
            }
            if (processedFilenames.processingSelectedFile) {
                System.out.println("Items: " + processedFilenames.movedTerms +
                        " Entries: " + creator.size());
            }
            toBeRemoved.add(file);
        }
        if (notMerged != null) {
            notMerged.close();
            notMerged = null;
        }
        creator.done();
        for (File file : toBeRemoved) {
            System.out.println("Removing file " + file);
            file.delete();
        }
        double percentage = new Double(processedFilenames.movedTerms).doubleValue() / new Double(totalTerms).doubleValue() * 100.0;
        System.out.format("Processed %d/%d terms (%.2f%%).%n",
                          processedFilenames.movedTerms,
                          totalTerms,
                          percentage);
    }
}
