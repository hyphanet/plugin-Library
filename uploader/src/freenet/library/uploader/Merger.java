/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package freenet.library.uploader;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.UnknownHostException;

import net.pterodactylus.fcp.ClientHello;
import net.pterodactylus.fcp.CloseConnectionDuplicateClientName;
import net.pterodactylus.fcp.FcpAdapter;
import net.pterodactylus.fcp.FcpConnection;
import net.pterodactylus.fcp.FcpMessage;
import net.pterodactylus.fcp.NodeHello;

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
                new DirectoryUploader(connection, new File(directory, dirsToMerge[0])).run();
                return;
            }

            String[] filesToMerge = directory.list(new FilenameFilter() {
                                
                    public boolean accept(File arg0, String arg1) {
                        if(!(arg1.toLowerCase().startsWith(UploaderPaths.BASE_FILENAME_PUSH_DATA))) return false;
                        File f = new File(arg0, arg1);
                        if(!f.isFile()) return false;
                        if(f.length() == 0) { f.delete(); return false; }
                        return true;
                    }
                                
                });

            System.out.println("There are " + filesToMerge.length + " files to merge.");
            for (String s : filesToMerge) {
                System.out.println("File: " + s);
            }

        } finally {
            if (connection != null) {
                connection.close();
            }
            System.exit(exitStatus);
        }
    }
}
