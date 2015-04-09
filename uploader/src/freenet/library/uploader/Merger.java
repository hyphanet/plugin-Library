/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package freenet.library.uploader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.pterodactylus.fcp.ClientHello;
import net.pterodactylus.fcp.CloseConnectionDuplicateClientName;
import net.pterodactylus.fcp.FcpAdapter;
import net.pterodactylus.fcp.FcpConnection;
import net.pterodactylus.fcp.FcpMessage;
import net.pterodactylus.fcp.NodeHello;

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
 *      Rewrite all files with the rest of the terms.
 * <ol> Merge that index.
 * <ol> Done.
 */
final public class Merger {
	
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

        System.out.println("Separate program started.");
        //if (!cwd.matches(".*/plugins")) {
        //    System.err.println("Should be started in the freenet directory.");
        //    System.exit(1);
        //}
        
        // Now we are in the Freenet directory. 
        // The rest of the work is done here.
        FcpConnection connection = null;

        FcpAdapter closeListener = new FcpAdapter() {
            public void connectionClosed(FcpConnection fcpConnection, Throwable throwable) {
                System.out.println("Connection Closed - Aborting.");
                System.exit(1);
            }
        };

        try {
            try {
                connection = new FcpConnection("127.0.0.1");
                connection.connect();
            } catch (UnknownHostException e) {
                System.err.println("Cannot connect to Node");
                exitStatus = 1;
                return;
            } catch (IOException e) {
                System.err.println("Cannot connect to Node");
                exitStatus = 1;
                return;
            }
            final String clientName = "SpiderMerger";
            final FcpMessage hello = new ClientHello(clientName);
            FcpAdapter helloListener = new FcpAdapter() {
                    public void receivedNodeHello(FcpConnection c, NodeHello nh) {
                        synchronized (hello) {
                            hello.notify();
                        }
                    }

                    public void receivedCloseConnectionDuplicateClientName(FcpConnection fcpConnection, CloseConnectionDuplicateClientName closeConnectionDuplicateClientName) {
                        System.out.println("Another " + clientName + " connected - Aborting.");
                        System.exit(1);
                    }
                };
            connection.addFcpListener(helloListener);

            connection.addFcpListener(closeListener);

            synchronized (hello) {
                try {
                    connection.sendMessage(hello);
                    hello.wait();
                } catch (InterruptedException e) {
                    System.err.println("Waiting for connection interrupted.");
                    exitStatus = 1;
                    return;
                } catch (IOException e) {
                    System.err.println("Hello cannot write.");
                    exitStatus = 1;
                    return;
                } finally {
                                connection.removeFcpListener(helloListener);
                }
            }
            helloListener = null;
            System.out.println("Connected");

            UploaderLibrary.init(connection);
            FactoryRegister.register(UploaderLibrary.getInstance());
            
            final String[] dirsToMerge;
            File directory = new File(".");
                        dirsToMerge = directory.list(new FilenameFilter() {
                                
                    public boolean accept(File arg0, String arg1) {
                        if(!(arg1.toLowerCase().startsWith(UploaderPaths.DISK_DIR_PREFIX))) return false;
                        return true;
                    }
                                
                });

            System.out.println("There are " + dirsToMerge.length + " old directories to merge.");
            if (dirsToMerge.length > 0) {
                System.out.println("Merging the first one.");
                new DirectoryUploader(connection, 
                                      new File(directory, dirsToMerge[0])).run();
                return;
            }

            String filteredFilesBaseFilename = UploaderPaths.BASE_FILENAME_PUSH_DATA + "filtered.";
            // Calculate the next name
            int lastFoundFiltered = 0;
            for (String filename : getMatchingFiles(directory, filteredFilesBaseFilename)) {
            	int numberFound = Integer.parseInt(filename.substring(filteredFilesBaseFilename.length()));
            	if (numberFound > lastFoundFiltered) {
            		lastFoundFiltered = numberFound;
            	}
            }
            System.out.println("Last found: " + lastFoundFiltered);

            String[] filesToMerge = getMatchingFiles(directory, UploaderPaths.BASE_FILENAME_PUSH_DATA);

            System.out.println("There are " + filesToMerge.length + " files to merge.");

            DirectoryCreator creator = new DirectoryCreator(directory);
            IndexPeeker peeker = new IndexPeeker();
            Set<File> toBeRemoved = new HashSet<File>();
            TermEntryFileWriter notMerged = null;
            
            for (String s : filesToMerge) {
                System.out.println("File: " + s);
				File file = new File(s);
            	FileInputStream fileInputStream;
				try {
					fileInputStream = new FileInputStream(file);
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return;
				}
				TermEntryReaderIterator teri = new TermEntryReaderIterator(new DataInputStream(fileInputStream));
				Iterator<TermEntry> iterator = teri.iterator();
				IndexPeeker.Section section = peeker.getSectionFor("a");
				while (iterator.hasNext()) {
					TermEntry tt = iterator.next();
					if (peeker.onTop(tt.subj) ||
							section.contains(tt.subj)) {
						creator.putEntry(tt);
					} else {
						if (notMerged == null) {
							lastFoundFiltered ++;
				            String filteredFilename = filteredFilesBaseFilename + lastFoundFiltered;
							notMerged = new TermEntryFileWriter(teri.getHeader(), new File(directory, filteredFilename));
						}
						notMerged.write(tt);
						if (notMerged.isFull()) {
							notMerged.close();
							notMerged = null;
						}
					}
				}
				toBeRemoved.add(file);
            }
			notMerged.close();
			notMerged = null;
			creator.done();
            for (File file : toBeRemoved) {
				System.out.println("Removing file " + file);
            	file.delete();
            }
        } catch (TaskAbortException e) {
			e.printStackTrace();
			return;
		} finally {
            connection.removeFcpListener(closeListener);
            if (connection != null) {
                connection.close();
            }
        }
        System.out.println("Upload completed.");
        System.exit(exitStatus);
    }
}
