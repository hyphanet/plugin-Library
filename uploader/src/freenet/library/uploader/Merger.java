/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package freenet.library.uploader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.pterodactylus.fcp.FcpConnection;

import freenet.library.FactoryRegister;
import freenet.library.index.TermEntry;
import freenet.library.util.BTreeMap.PairIterable;
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
 *      Rewrite all files with the rest of the terms.
 * <ol> Merge that index.
 * <ol> Done.
 */
final public class Merger {
	
	private static FcpSession session;

	private static final String SELECTED = UploaderPaths.BASE_FILENAME_DATA + "selected.";
	private static final String FILTERED = UploaderPaths.BASE_FILENAME_DATA + "filtered.";
	private static final String PROCESSED = UploaderPaths.BASE_FILENAME_DATA + "processed.";

	static String[] getMatchingFiles(File directory,
			final String baseFilename) {
		return directory.list(new FilenameFilter() {
		                    
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
        } catch (TaskAbortException e) {
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
        String[] selectedFilesToMerge = getMatchingFiles(directory, SELECTED);
        System.out.println("There is " + selectedFilesToMerge.length + " selected files.");

        String [] filteredFilesToMerge = getMatchingFiles(directory, FILTERED);
        System.out.println("There is " + filteredFilesToMerge.length + " filtered files.");

        String [] processedFilesToMerge = getMatchingFiles(directory, PROCESSED);
        System.out.println("There is " + processedFilesToMerge.length + " processed files.");

        String[] newFilesToMerge = getMatchingFiles(directory, UploaderPaths.BASE_FILENAME_PUSH_DATA);
        System.out.println("There is " + newFilesToMerge.length + " new files.");

        // Calculate the next number of filtered and processed files.
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
        
        DirectoryCreator creator = new DirectoryCreator(directory);

        Map<IndexPeeker, TermEntryFileWriter> writers =
        		new HashMap<IndexPeeker, TermEntryFileWriter>();
        IndexPeeker peeker = new IndexPeeker(directory);

		Set<File> toBeRemoved = new HashSet<File>();
		List<String> filesToMerge = new ArrayList<String>();
		String restBase;
		if (selectedFilesToMerge.length > 0) {
			filesToMerge.add(selectedFilesToMerge[0]);
			restBase = PROCESSED;
        } else {
            for (int i = 0; i < filteredFilesToMerge.length; i++) {
            	filesToMerge.add(filteredFilesToMerge[i]);
            }
            restBase = FILTERED;
        }
        for (int i = 0; i < processedFilesToMerge.length; i++) {
        	filesToMerge.add(processedFilesToMerge[i]);
        }
        for (int i = 0; i < newFilesToMerge.length; i++) {
        	filesToMerge.add(newFilesToMerge[i]);
        }
        TermEntryFileWriter notMerged = null;

        int totalTerms = 0;
        int movedTerms = 0;

        for (String s : filesToMerge) {
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
				if (peeker.include(tt.subj)) {
					creator.putEntry(tt);
					movedTerms ++;
					continue;
				}
				
				if (selectedFilesToMerge.length == 0) {
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
					} else if (writers.size() < 50) {
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
		            String restFilename = restBase + lastFoundNumber;
					notMerged = new TermEntryFileWriter(teri.getHeader(), new File(directory, restFilename));
				}
				notMerged.write(tt);
				if (notMerged.isFull()) {
					notMerged.close();
					notMerged = null;
				}
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
        double percentage = new Double(movedTerms).doubleValue() / new Double(totalTerms).doubleValue() * 100.0;
        System.out.format("Processed %d/%d terms (%.2f%%).%n",
        				  movedTerms,
        				  totalTerms,
        				  percentage);
	}
}
