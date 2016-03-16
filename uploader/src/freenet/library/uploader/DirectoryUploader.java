package freenet.library.uploader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;

import freenet.library.Priority;
import freenet.library.index.ProtoIndex;
import freenet.library.index.ProtoIndexComponentSerialiser;
import freenet.library.index.ProtoIndexSerialiser;
import freenet.library.index.TermEntry;
import freenet.library.io.FreenetURI;
import freenet.library.io.serial.LiveArchiver;
import freenet.library.io.serial.Serialiser.PullTask;
import freenet.library.io.serial.Serialiser.PushTask;
import freenet.library.util.SkeletonBTreeMap;
import freenet.library.util.SkeletonBTreeSet;
import freenet.library.util.TaskAbortExceptionConvertor;
import freenet.library.util.exec.SimpleProgress;
import freenet.library.util.exec.TaskAbortException;
import freenet.library.util.func.Closure;

import net.pterodactylus.fcp.ClientPut;
import net.pterodactylus.fcp.FcpAdapter;
import net.pterodactylus.fcp.FcpConnection;
import net.pterodactylus.fcp.PutFailed;
import net.pterodactylus.fcp.PutSuccessful;
import net.pterodactylus.fcp.URIGenerated;
import net.pterodactylus.fcp.UploadFrom;

class DirectoryUploader implements Runnable {
        
    FcpConnection connection;
    File directory;

    DirectoryUploader(FcpConnection c, File d) {
        connection = c;
        directory = d;
    }
        
    public void run() {
    	mergeToFreenet(directory);
    }
        
    private String lastUploadURI;
    private boolean uskUploadDone;

    static final int MAX_HANDLING_COUNT = 5; 
    // When pushing is broken, allow max handling to reach this level
    // before stalling forever to prevent running out of disk space.
        
    /** The temporary on-disk index. We merge stuff into this until it
     * exceeds a threshold size, then we create a new diskIdx and
     * merge the old one into the idxFreenet. */
    ProtoIndex idxDisk;

    /** idxDisk gets merged into idxFreenet this long after the last
     * merge completed. */
    static final long MAX_TIME = 24*60*60*1000L;

    /** idxDisk gets merged into idxFreenet after this many incoming
     * updates from Spider. */
    static final int MAX_UPDATES = 16;

    /** idxDisk gets merged into idxFreenet after it has grown to this
     * many terms.  Note that the entire main tree of terms (not the
     * sub-trees with the positions and urls in) must fit into memory
     * during the merge process. */
    static final int MAX_TERMS = 100*1000;

    /** idxDisk gets merged into idxFreenet after it has grown to this
     * many terms.  Note that the entire main tree of terms (not the
     * sub-trees with the positions and urls in) must fit into memory
     * during the merge process. */
    static final int MAX_TERMS_NOT_UPLOADED = 10*1000;

    /** Maximum size of a single entry, in TermPageEntry count, on
     * disk. If we exceed this we force an insert-to-freenet and move
     * on to a new disk index. The problem is that the merge to
     * Freenet has to keep the whole of each entry in RAM. This is
     * only true for the data being merged in - the on-disk index -
     * and not for the data on Freenet, which is pulled on
     * demand. SCALABILITY */
    static final int MAX_DISK_ENTRY_SIZE = 10000;

    static final String DISK_DIR_PREFIX = "library-temp-index-";
    /** Directory the current idxDisk is saved in. */
    File idxDiskDir;
        
    ProtoIndexSerialiser srl = null;
    String lastDiskIndexName;
    /** The uploaded index on Freenet. This never changes, it just
     * gets updated. */
    ProtoIndex idxFreenet;
        
    // private final SpiderIndexURIs spiderIndexURIs;
        
    long pushNumber;
    static final String LAST_URL_FILENAME = "library.index.lastpushed.chk";
    static final String PRIV_URI_FILENAME = "library.index.privkey";
    static final String PUB_URI_FILENAME = "library.index.pubkey";
    static final String EDITION_FILENAME = "library.index.last-edition";
        
    static final String LAST_DISK_FILENAME = "library.index.lastpushed.disk";
        
    static final String BASE_FILENAME_PUSH_DATA = "library.index.data.";
        
    ProtoIndexSerialiser srlDisk = null;
        
    private boolean writeStringTo(File filename, String uri) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(filename);
            OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
            osw.write(uri);
            osw.close();
            fos = null;
            return true;
        } catch (IOException e) {
            System.out.println("Failed to write to "+filename+" : "+uri+" : "+e);
            return false;
        } finally {
            try {
            	if (fos != null) {
            		fos.close();
            	}
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
    }

    static String readStringFrom(File file) {
        String ret;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            ret = br.readLine();
            fis.close();
            fis = null;
            return ret;
        } catch (IOException e) {
            // Ignore
            return null;
        } finally {
    		try {
    			if (fis != null) { 
    				fis.close();
    			}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
    }  


    static final String INDEX_DOCNAME = "index.yml";
        
    private ProtoIndexComponentSerialiser leafsrl;
        
    /** Merge a disk dir to an on-Freenet index. Usually called on
     * startup, i.e. we haven't just created the on-disk index so we
     * need to setup the ProtoIndex etc. */
    protected void mergeToFreenet(File diskDir) {
        ProtoIndexSerialiser s = ProtoIndexSerialiser.forIndex(diskDir);
        LiveArchiver<Map<String,Object>,SimpleProgress> archiver = 
            (LiveArchiver<Map<String,Object>,SimpleProgress>)(s.getChildSerialiser());
        ProtoIndexComponentSerialiser leaf = ProtoIndexComponentSerialiser.get(ProtoIndexComponentSerialiser.FMT_FILE_LOCAL, archiver);
        String f = DirectoryUploader.readStringFrom(new File(diskDir, LAST_DISK_FILENAME));
        if(f == null) {
            if(diskDir.list().length == 0) {
                System.err.println("Directory " + diskDir + " is empty - removing. Nothing to merge.");
                diskDir.delete();
                return;
            }
            // Ignore
            System.err.println("Unable to merge old data "+diskDir);
            return;
        } else {
            System.out.println("Continuing old bucket: "+f);
        }

        ProtoIndex idxDisk = null;
        try {
            PullTask<ProtoIndex> pull = new PullTask<ProtoIndex>(f);
            System.out.println("Pulling previous index "+f+" from disk so can update it.");
            s.pull(pull);
            System.out.println("Pulled previous index "+f+" from disk - updating...");
            idxDisk = pull.data;
            if(idxDisk.getSerialiser().getLeafSerialiser() != archiver)
                throw new IllegalStateException("Different serialiser: "+idxDisk.getSerialiser()+" should be "+archiver);
        } catch (TaskAbortException e) {
            System.err.println("Failed to download previous index for spider update: "+e);
            e.printStackTrace();
            return;
        }
        mergeToFreenet(idxDisk, diskDir);
    }

    /** Delete everything in a directory. Only use this when we are
     * *very sure* there is no important data below it! */
    private static boolean removeAll(File wd) {
        if(!wd.isDirectory()) {
            System.out.println("DELETING FILE "+wd);
            if(!wd.delete() && wd.exists()) {
                System.err.println("Could not delete file: " + wd);
                return false;
            }
        } else {
            for(File subfile: wd.listFiles()) {
                if(!removeAll(subfile)) {
                    return false;
                }
            }
            if(!wd.delete()) {
                System.err.println("Could not delete directory: " + wd);
                return false;
            }
        }
        return true;
    }

    private final Object inflateSync = new Object();
        
    /** Merge from an on-disk index to an on-Freenet index.
     * @param diskToMerge The on-disk index.
     * @param diskDir The folder the on-disk index is stored in.
     */
    protected void mergeToFreenet(ProtoIndex diskToMerge, File diskDir) {
        if (lastUploadURI == null) {
            lastUploadURI = readStringFrom(new File(LAST_URL_FILENAME));
        }
        setupFreenetCacheDir();
                
        makeFreenetSerialisers();
                        
        updateOverallMetadata(diskToMerge);
                
        final SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> newtrees = diskToMerge.ttab;
                
        // Do the upload
                
        // async merge
        Closure<Map.Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException> clo =
            createMergeFromTreeClosure(newtrees);
        try {
            long mergeStartTime = System.currentTimeMillis();
            assert(idxFreenet.ttab.isBare());
            Iterator<String> it =
                diskToMerge.ttab.keySetAutoDeflate().iterator();
            TreeSet<String> terms = new TreeSet<String>();
            while(it.hasNext()) terms.add(it.next());
            System.out.println("Merging "
                               + terms.size()
                               + " terms from disk to Freenet...");
            assert(terms.size() == diskToMerge.ttab.size());
            assert(idxFreenet.ttab.isBare());
            assert(diskToMerge.ttab.isBare());
            long entriesAdded = terms.size();
            // Run the actual merge.
            System.out.println("Start update");
            idxFreenet.ttab.update(terms, null, clo, new TaskAbortExceptionConvertor());
            assert(idxFreenet.ttab.isBare());
            // Deflate the main tree.
            System.out.println("Start deflate");
            newtrees.deflate();
            assert(diskToMerge.ttab.isBare());
                        
            // Push the top node to a CHK.
            PushTask<ProtoIndex> task4 = new PushTask<ProtoIndex>(idxFreenet);
            task4.meta = "Unknown";
            System.out.println("Start pushing");
            srl.push(task4);

            // Now wait for the inserts to finish. They are started
            // asynchronously in the above merge.
            LiveArchiver<Map<String, Object>, SimpleProgress> arch = srl.getChildSerialiser();
            System.out.println("Start waiting");
            arch.waitForAsyncInserts();
            System.out.println("Done waiting");
                        
            long mergeEndTime = System.currentTimeMillis();
            System.out.println(entriesAdded + " entries merged in " + (mergeEndTime-mergeStartTime) + " ms, root at " + task4.meta);
            String uri = (String) task4.meta;
            lastUploadURI = uri;
            if(writeStringTo(new File(LAST_URL_FILENAME), uri)) {
                newtrees.deflate();
                diskToMerge = null;
                terms = null;
                System.out.println("Finished with disk index "+diskDir);
                removeAll(diskDir);
            }
                        
            // Create the USK to redirect to the CHK at the top of the index.
            uploadUSKForFreenetIndex(uri);
                        
        } catch (TaskAbortException e) {
            System.err.println("Failed to upload index for spider: "+e);
            e.printStackTrace();
        }
    }

	static String readFileLine(final String filename) {
		File f = new File(filename);
		FileInputStream fis;
		try {
			fis = new FileInputStream(f);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException();
		}
		BufferedReader br = null;
		String line;
		try {
			br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
			line = br.readLine();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException();
		} finally {
			try {
				if (br != null) {
					br.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return line;
	}
    
    protected void writeFileLine(String filename, String string) {
		File f = new File(filename);
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(f);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException();
		}
		BufferedWriter bw = null; 
		try {
			bw = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"));
			bw.write(string);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException();
		} finally {
			try {
				if (bw != null) {
					bw.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
	}

    private void uploadUSKForFreenetIndex(String uri) {
    	String insertURI = readFileLine(PRIV_URI_FILENAME);
    	String keyPart = insertURI.substring("freenet:SSK@".length());
    	int lastEdition = Integer.parseInt(readFileLine(EDITION_FILENAME));
        final ClientPut usk = new ClientPut("USK@" + keyPart + "/" + (lastEdition + 1), 
        									"USKupload",
        									UploadFrom.redirect);
        usk.setTargetURI(uri);
        uskUploadDone = false;
        FcpAdapter fcpListener = new FcpAdapter() {
                public void receivedPutFailed(FcpConnection fcpConnection, PutFailed result) {
            		assert fcpConnection == connection;
        			assert result != null;
                    System.out.println("Could not upload USK");
                    uskUploadDone = true;
                    synchronized (usk) {
                        usk.notifyAll();
                    }
                }

                public void receivedPutSuccessful(FcpConnection fcpConnection, PutSuccessful result) {
            		assert fcpConnection == connection;
        			assert result != null;
                    System.out.println("USK uploaded");
                    uskUploadDone = true;
                    synchronized (usk) {
                        usk.notifyAll();
                    }
                }
                
            	public void receivedURIGenerated(FcpConnection fcpConnection, URIGenerated uriGenerated) {
            		assert fcpConnection == connection;
        			assert uriGenerated != null;
        			System.out.println("URI generated " + uriGenerated.getURI());
        			int editionStartPos = uriGenerated.getURI().lastIndexOf('/') + 1;
        			writeFileLine(EDITION_FILENAME, uriGenerated.getURI().substring(editionStartPos));
            	}

            };
		connection.addFcpListener(fcpListener);

        try {
            connection.sendMessage(usk);
            while (!uskUploadDone) {
            	synchronized (usk) {
                    usk.wait();            		
            	}
            }
        } catch (InterruptedException e) {
            System.err.println("Could not upload USK");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("IO Exception when uploading USK");
            System.exit(1);
        } finally {
            connection.removeFcpListener(fcpListener);        	
        }
    }


	/** Create a Closure which will merge the subtrees from one index
     * (on disk) into the subtrees of another index (on Freenet). It
     * will be called with each subtree from the on-Freenet index, and
     * will merge data from the relevant on-disk subtree. Both
     * subtrees are initially deflated, and should be deflated when we
     * leave the method, to avoid running out of memory.
     * @param newtrees The on-disk tree of trees to get data from.
     * @return
     */
    private Closure<Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException> createMergeFromTreeClosure(final SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> newtrees) {
        return new
            Closure<Map.Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException>() {
            /*@Override**/ public void invoke(Map.Entry<String, SkeletonBTreeSet<TermEntry>> entry) throws TaskAbortException {
                String key = entry.getKey();
                SkeletonBTreeSet<TermEntry> tree = entry.getValue();
                boolean newTree = false;
                if (tree == null) {
                    entry.setValue(tree = makeEntryTree(leafsrl));
                    newTree = true;
                }
                assert(tree.isBare());
                SortedSet<TermEntry> data;
                // Can't be run in parallel.
                synchronized(inflateSync) {
                    newtrees.inflate(key, true);
                    SkeletonBTreeSet<TermEntry> entries;
                    entries = newtrees.get(key);
                    // CONCURRENCY: Because the lower-level trees are
                    // packed by the top tree, the bottom trees
                    // (SkeletonBTreeSet's) are not independant of
                    // each other. When the newtrees inflate above
                    // runs, it can deflate a tree that is still in
                    // use by another instance of this
                    // callback. Therefore we must COPY IT AND DEFLATE
                    // IT INSIDE THE LOCK.
                    entries.inflate();
                    data = new TreeSet<TermEntry>(entries);
                    entries.deflate();
                    assert(entries.isBare());
                }
                if (tree != null) {
                    if (newTree) {
                        tree.addAll(data);
                        assert(tree.size() == data.size());
                    } else {
                        tree.update(data, null);
                        // Note that it is possible for data.size() +
                        // oldSize != tree.size(), because we might be
                        // merging data we've already merged.  But
                        // most of the time it will add up.
                    }
                    tree.deflate();
                    assert(tree.isBare());
                }
            }
        };
    }

    /** Update the overall metadata for the on-Freenet index from the
     * on-disk index. */
    private void updateOverallMetadata(ProtoIndex diskToMerge) {
        idxFreenet.setName(diskToMerge.getName());
        idxFreenet.setOwnerEmail(diskToMerge.getOwnerEmail());
        idxFreenet.setOwner(diskToMerge.getOwner());
        // This is roughly accurate, it might not be exactly so if we
        // process a bit out of order.
        idxFreenet.setTotalPages(diskToMerge.getTotalPages() + Math.max(0,idxFreenet.getTotalPages()));
    }

    /** Setup the serialisers for uploading to Freenet. These convert
     * tree nodes to and from blocks on Freenet, essentially. */
    private void makeFreenetSerialisers() {
        if(srl == null) {
            srl = ProtoIndexSerialiser.forIndex(lastUploadURI, Priority.Bulk);
            LiveArchiver<Map<String,Object>,SimpleProgress> archiver = 
                (LiveArchiver<Map<String,Object>,SimpleProgress>)(srl.getChildSerialiser());
            leafsrl = ProtoIndexComponentSerialiser.get(ProtoIndexComponentSerialiser.FMT_DEFAULT, archiver);
            if(lastUploadURI == null) {
                try {
					idxFreenet = new ProtoIndex(new FreenetURI("CHK@"), "test", null, null, 0L);
				} catch (MalformedURLException e) {
					throw new AssertionError(e);
				}
                // FIXME more hacks: It's essential that we use the
                // same FreenetArchiver instance here.
                leafsrl.setSerialiserFor(idxFreenet);
            } else {
                try {
                    PullTask<ProtoIndex> pull = new PullTask<ProtoIndex>(lastUploadURI);
                    System.out.println("Pulling previous index "+lastUploadURI+" so can update it.");
                    srl.pull(pull);
                    System.out.println("Pulled previous index "+lastUploadURI+" - updating...");
                    idxFreenet = pull.data;
                    if(idxFreenet.getSerialiser().getLeafSerialiser() != archiver)
                        throw new IllegalStateException("Different serialiser: "+idxFreenet.getSerialiser()+" should be "+leafsrl);
                } catch (TaskAbortException e) {
                    System.err.println("Failed to download previous index for spider update: "+e);
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    /** Set up the on-disk cache, which keeps a copy of everything we
     * upload to Freenet, so we won't need to re-download it, which
     * can be very slow and doesn't always succeed. */
    private void setupFreenetCacheDir() {
    	File dir = new File(UploaderPaths.LIBRARY_CACHE);
    	dir.mkdir();
    }

    protected static SkeletonBTreeSet<TermEntry> makeEntryTree(ProtoIndexComponentSerialiser leafsrl) {
        SkeletonBTreeSet<TermEntry> tree = new SkeletonBTreeSet<TermEntry>(ProtoIndex.BTREE_NODE_MIN);
        leafsrl.setSerialiserFor(tree);
        return tree;
    }
}
