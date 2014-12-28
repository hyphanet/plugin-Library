package plugins.Library;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;

import plugins.Library.client.FreenetArchiver;
import plugins.Library.index.ProtoIndex;
import plugins.Library.index.ProtoIndexComponentSerialiser;
import plugins.Library.index.ProtoIndexSerialiser;
import plugins.Library.index.TermEntry;
import plugins.Library.index.TermEntryReaderWriter;
import plugins.Library.io.serial.LiveArchiver;
import plugins.Library.io.serial.Serialiser.PullTask;
import plugins.Library.io.serial.Serialiser.PushTask;
import plugins.Library.util.SkeletonBTreeMap;
import plugins.Library.util.SkeletonBTreeSet;
import plugins.Library.util.TaskAbortExceptionConvertor;
import freenet.client.InsertException;
import freenet.keys.FreenetURI;
import freenet.library.util.exec.SimpleProgress;
import freenet.library.util.exec.TaskAbortException;
import freenet.library.util.func.Closure;
import freenet.node.RequestStarter;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.MutableBoolean;
import freenet.support.SimpleFieldSet;
import freenet.support.TimeUtil;
import freenet.support.api.Bucket;
import freenet.support.io.BucketTools;
import freenet.support.io.Closer;
import freenet.support.io.FileBucket;
import freenet.support.io.FileUtil;
import freenet.support.io.LineReadingInputStream;

public class SpiderIndexUploader {
	
	SpiderIndexUploader(PluginRespirator pr) {
		this.pr = pr;
		spiderIndexURIs = new SpiderIndexURIs(pr);
	}
	
	static boolean logMINOR;
	static {
		Logger.registerClass(SpiderIndexUploader.class);
	}
	
	private final PluginRespirator pr;
	private Object freenetMergeSync = new Object();
	private boolean freenetMergeRunning = false;
	private boolean diskMergeRunning = false;
	
	private final ArrayList<Bucket> toMergeToDisk = new ArrayList<Bucket>();
	static final int MAX_HANDLING_COUNT = 5; 
	// When pushing is broken, allow max handling to reach this level before stalling forever to prevent running out of disk space.
	private int PUSH_BROKEN_MAX_HANDLING_COUNT = 10;
	// Don't use too much disk space, take into account fact that Spider slows down over time.
	
	private boolean pushBroken;
	
	/** The temporary on-disk index. We merge stuff into this until it exceeds a threshold size, then
	 * we create a new diskIdx and merge the old one into the idxFreenet. */
	ProtoIndex idxDisk;

	/** idxDisk gets merged into idxFreenet this long after the last merge completed. */
	static final long MAX_TIME = 24*60*60*1000L;

	/** idxDisk gets merged into idxFreenet after this many incoming updates from Spider. */
	static final int MAX_UPDATES = 16;

	/** idxDisk gets merged into idxFreenet after it has grown to this many terms.
	 * Note that the entire main tree of terms (not the sub-trees with the positions and urls in) must
	 * fit into memory during the merge process. */
	static final int MAX_TERMS = 100*1000;

	/** idxDisk gets merged into idxFreenet after it has grown to this many terms.
	 * Note that the entire main tree of terms (not the sub-trees with the positions and urls in) must
	 * fit into memory during the merge process. */
	static final int MAX_TERMS_NOT_UPLOADED = 10*1000;

	/** Maximum size of a single entry, in TermPageEntry count, on disk. If we exceed this we force an
	 * insert-to-freenet and move on to a new disk index. The problem is that the merge to Freenet has 
	 * to keep the whole of each entry in RAM. This is only true for the data being merged in - the 
	 * on-disk index - and not for the data on Freenet, which is pulled on demand. SCALABILITY */
	static final int MAX_DISK_ENTRY_SIZE = 10000;

	/** Like pushNumber, the number of the current disk dir, used to create idxDiskDir. */
	private int dirNumber;
	static final String DISK_DIR_PREFIX = "library-temp-index-";
	/** Directory the current idxDisk is saved in. */
	File idxDiskDir;
	private int mergedToDisk;
	
	ProtoIndexSerialiser srl = null;
	FreenetURI lastUploadURI = null;
	String lastDiskIndexName;
	/** The uploaded index on Freenet. This never changes, it just gets updated. */
	ProtoIndex idxFreenet;
	
	private final SpiderIndexURIs spiderIndexURIs;
	
	long pushNumber;
	static final String LAST_URL_FILENAME = "library.index.lastpushed.chk";
	static final String PRIV_URI_FILENAME = "library.index.privkey";
	static final String PUB_URI_FILENAME = "library.index.pubkey";
	static final String EDITION_FILENAME = "library.index.next-edition";
	
	static final String LAST_DISK_FILENAME = "library.index.lastpushed.disk";
	
	static final String BASE_FILENAME_PUSH_DATA = "library.index.data.";
	
	/** Merge from the Bucket chain to the on-disk idxDisk. */
	protected void wrapMergeToDisk() {
		spiderIndexURIs.loadSSKURIs();
		boolean first = true;
		while(true) {
		final Bucket data;
		synchronized(freenetMergeSync) {
			if(pushBroken) {
				Logger.error(this, "Pushing broken");
				return;
			}
			if(first && diskMergeRunning) {
				Logger.error(this, "Already running a handler!");
				return;
			} else if((!first) && (!diskMergeRunning)) {
				Logger.error(this, "Already running yet runningHandler is false?!");
				return;
			}
			first = false;
			if(toMergeToDisk.size() == 0) {
				if(logMINOR) Logger.minor(this, "Nothing to handle");
				diskMergeRunning = false;
				freenetMergeSync.notifyAll();
				return;
			}
			data = toMergeToDisk.remove(0);
			freenetMergeSync.notifyAll();
			diskMergeRunning = true;
		}
		try {
			mergeToDisk(data);
		} catch (Throwable t) {
			// Failed.
			synchronized(freenetMergeSync) {
				diskMergeRunning = false;
				pushBroken = true;
				freenetMergeSync.notifyAll();
			}
			if(t instanceof RuntimeException)
				throw (RuntimeException)t;
			if(t instanceof Error)
				throw (Error)t;
		}
		}
	}

	// This is a member variable because it is huge, and having huge stuff in local variables seems to upset the default garbage collector.
	// It doesn't need to be synchronized because it's always used from mergeToDisk, which never runs in parallel.
	private Map<String, SortedSet<TermEntry>> newtrees;
	// Ditto
	private SortedSet<String> terms;
	
	ProtoIndexSerialiser srlDisk = null;
	private ProtoIndexComponentSerialiser leafsrlDisk;
	
	private long lastMergedToFreenet = -1;
	
	/** Merge a bucket of TermEntry's into an on-disk index. */
	private void mergeToDisk(Bucket data) {
		
		boolean newIndex = false;
		
		if(idxDiskDir == null) {
			newIndex = true;
			if(!createDiskDir()) return;
		}
		
		if(!makeDiskDirSerialiser()) return;
		
		// Read data into newtrees and trees.
		long entriesAdded = readTermsFrom(data);
		
		if(terms.size() == 0) {
			System.out.println("Nothing to merge");
			synchronized(this) {
				newtrees = null;
				terms = null;
			}
			return;
		}
		
		// Merge the new data to the disk index.
		
		try {
			final MutableBoolean maxDiskEntrySizeExceeded = new MutableBoolean();
			maxDiskEntrySizeExceeded.value = false;
			long mergeStartTime = System.currentTimeMillis();
			if(newIndex) {
			    if(createDiskIndex())
			        maxDiskEntrySizeExceeded.value = true;
			} else {
				// async merge
				Closure<Map.Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException> clo =
				    createMergeFromNewtreesClosure(maxDiskEntrySizeExceeded);
				assert(idxDisk.ttab.isBare());
				System.out.println("Merging "+terms.size()+" terms, tree.size = "+idxDisk.ttab.size()+" from "+data+"...");
				idxDisk.ttab.update(terms, null, clo, new TaskAbortExceptionConvertor());
			
			}		
			// Synchronize anyway so garbage collector knows about it.
			synchronized(this) {
				newtrees = null;
				terms = null;
			}
			assert(idxDisk.ttab.isBare());
			PushTask<ProtoIndex> task4 = new PushTask<ProtoIndex>(idxDisk);
			srlDisk.push(task4);
			
			long mergeEndTime = System.currentTimeMillis();
			System.out.print(entriesAdded + " entries merged to disk in " + (mergeEndTime-mergeStartTime) + " ms, root at " + task4.meta + ", ");
			// FileArchiver produces a String, which is a filename not including the prefix or suffix.
			String uri = (String)task4.meta;
			lastDiskIndexName = uri;
			System.out.println("Pushed new index to file "+uri);
			if(writeStringTo(new File(LAST_DISK_FILENAME), uri) &&
			        writeStringTo(new File(idxDiskDir, LAST_DISK_FILENAME), uri)) {
			    // Successfully uploaded and written new status. Can delete the incoming data.
			    data.free();
			}
			
			maybeMergeToFreenet(maxDiskEntrySizeExceeded);
		} catch (TaskAbortException e) {
			Logger.error(this, "Failed to upload index for spider: "+e, e);
			System.err.println("Failed to upload index for spider: "+e);
			e.printStackTrace();
			synchronized(freenetMergeSync) {
				pushBroken = true;
			}
		}
	}

	/** We have just written a Bucket of new data to an on-disk index. We may or may not want to
	 * upload to an on-Freenet index, depending on how big the data is etc. If we do, we will need
	 * to create a new on-disk index.
	 * @param maxDiskEntrySizeExceeded A flag object which is set (off-thread) if any single term 
	 * in the index is very large.
	 */
	private void maybeMergeToFreenet(MutableBoolean maxDiskEntrySizeExceeded) {
        // Maybe chain to mergeToFreenet ???
        
        boolean termTooBig = false;
        synchronized(maxDiskEntrySizeExceeded) {
            termTooBig = maxDiskEntrySizeExceeded.value;
        }
        
        mergedToDisk++;
        if((lastMergedToFreenet > 0 && idxDisk.ttab.size() > MAX_TERMS) || 
                (idxDisk.ttab.size() > MAX_TERMS_NOT_UPLOADED)
                || (mergedToDisk > MAX_UPDATES) || termTooBig || 
                (lastMergedToFreenet > 0 && (System.currentTimeMillis() - lastMergedToFreenet) > MAX_TIME)) {
            
            final ProtoIndex diskToMerge = idxDisk;
            final File dir = idxDiskDir;
            System.out.println("" +
			       idxDisk.ttab.size() + " terms in index, " +
			       mergedToDisk + " merges, " +
			       (lastMergedToFreenet <= 0
				? "never merged to Freenet"
				: ("last merged to Freenet "+TimeUtil.formatTime(System.currentTimeMillis() - lastMergedToFreenet)) + "ago"));

	    System.out.print("Exceeded threshold for ");
	    if (lastMergedToFreenet > 0 && idxDisk.ttab.size() > MAX_TERMS)
		System.out.print("terms, ");
	    if (idxDisk.ttab.size() > MAX_TERMS_NOT_UPLOADED)
		System.out.print("not uploaded terms, ");
	    if (mergedToDisk > MAX_UPDATES)
		System.out.print("updates, ");
	    if (termTooBig)
		System.out.print("term too big, ");
	    if (lastMergedToFreenet > 0 && (System.currentTimeMillis() - lastMergedToFreenet) > MAX_TIME)
		System.out.print("time since last merge, ");
	    System.out.println("starting new disk index and starting merge from disk to Freenet...");
            mergedToDisk = 0;
            lastMergedToFreenet = -1;
            idxDisk = null;
            srlDisk = null;
            leafsrlDisk = null;
            idxDiskDir = null;
            lastDiskIndexName = null;
            
        synchronized(freenetMergeSync) {
            while(freenetMergeRunning) {
                if(pushBroken) return;
                System.err.println("Need to merge to Freenet, but last merge not finished yet. Waiting...");
                try {
                    freenetMergeSync.wait();
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
            if(pushBroken) return;
            freenetMergeRunning = true;
        }
        
        Runnable r = new Runnable() {
            
            public void run() {
                try {
                    mergeToFreenet(diskToMerge, dir);
                } catch (Throwable t) {
                    Logger.error(this, "Merge to Freenet failed: "+t, t);
                    System.err.println("Merge to Freenet failed: "+t);
                    t.printStackTrace();
                    synchronized(freenetMergeSync) {
                        pushBroken = true;
                    }
                } finally {
                    synchronized(freenetMergeSync) {
                        freenetMergeRunning = false;
                        if(!pushBroken)
                            lastMergedToFreenet = System.currentTimeMillis();
                        freenetMergeSync.notifyAll();
                    }
                }
            }
            
        };
        pr.getNode().executor.execute(r, "Library: Merge data from disk to Freenet");
        } else {
            System.out.println("Not merging to Freenet yet: "+idxDisk.ttab.size()+" terms in index, "+mergedToDisk+" merges, "+(lastMergedToFreenet <= 0 ? "never merged to Freenet" : ("last merged to Freenet "+TimeUtil.formatTime(System.currentTimeMillis() - lastMergedToFreenet))+"ago"));
        }
    }

    private boolean writeURITo(File filename, FreenetURI uri) {
        return writeStringTo(filename, uri.toString());
    }
	
    private boolean writeStringTo(File filename, String uri) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(filename);
            OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
            osw.write(uri.toString());
            osw.close();
            fos = null;
            return true;
        } catch (IOException e) {
            Logger.error(this, "Failed to write to "+filename+" : "+uri, e);
            System.out.println("Failed to write to "+filename+" : "+uri+" : "+e);
            return false;
        } finally {
            Closer.close(fos);
        }
    }

    private String readStringFrom(File file) {
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
            Closer.close(fis);
        }
    }
    
    private FreenetURI readURIFrom(File file) {
        String s = readStringFrom(file);
        if(s != null) {
            try {
                return new FreenetURI(s);
            } catch (MalformedURLException e) {
                // Ignore.
            }
        }
        return null;
    }

    /** Create a callback object which will do the merging of individual terms. This will be called 
	 * for each term as it is unpacked from the existing on-disk index. It then merges in new data
	 * from newtrees and writes the subtree for the term back to disk. Most of the work is done in
	 * update() below. 
	 * @param maxDiskEntrySizeExceeded Will be set if any single term is so large that we need to
	 * upload to Freenet immediately. */
	private Closure<Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException> createMergeFromNewtreesClosure(final MutableBoolean maxDiskEntrySizeExceeded) {
        return new
        Closure<Map.Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException>() {
            /*@Override**/ public void invoke(Map.Entry<String, SkeletonBTreeSet<TermEntry>> entry) throws TaskAbortException {
                String key = entry.getKey();
                SkeletonBTreeSet<TermEntry> tree = entry.getValue();
                if(logMINOR) Logger.minor(this, "Processing: "+key+" : "+tree);
                if(tree != null)
                    Logger.debug(this, "Merging data (on disk) in term "+key);
                else
                    Logger.debug(this, "Adding new term to disk index:  "+key);
                if (tree == null) {
                    entry.setValue(tree = makeEntryTree(leafsrlDisk));
                }
                assert(tree.isBare());
                SortedSet<TermEntry> toMerge = newtrees.get(key);
                tree.update(toMerge, null);
                if(toMerge.size() > MAX_DISK_ENTRY_SIZE)
                    synchronized(maxDiskEntrySizeExceeded) {
                        maxDiskEntrySizeExceeded.value = true;
                    }
                toMerge = null;
                newtrees.remove(key);
                assert(tree.isBare());
                if(logMINOR) Logger.minor(this, "Updated: "+key+" : "+tree);
            }
        };
    }

    /** Create a new on-disk index from terms and newtrees.
	 * @return True if the size of any one item in the index is so large that we must upload
	 * immediately to Freenet. 
	 * @throws TaskAbortException If something broke catastrophically. */
	private boolean createDiskIndex() throws TaskAbortException {
	    boolean tooBig = false;
        // created a new index, fill it with data.
        // DON'T MERGE, merge with a lot of data will deadlock.
        // FIXME throw in update() if it will deadlock.
        for(String key : terms) {
            SkeletonBTreeSet<TermEntry> tree = makeEntryTree(leafsrlDisk);
            SortedSet<TermEntry> toMerge = newtrees.get(key);
            tree.addAll(toMerge);
            if(toMerge.size() > MAX_DISK_ENTRY_SIZE)
                tooBig = true;
            toMerge = null;
            tree.deflate();
            assert(tree.isBare());
            idxDisk.ttab.put(key, tree);
        }
        idxDisk.ttab.deflate();
        return tooBig;
    }

    /** Read the TermEntry's from the Bucket into newtrees and terms, and set up the index
	 * properties.
	 * @param data The Bucket containing TermPageEntry's etc serialised with TermEntryReaderWriter.
	 */
    private long readTermsFrom(Bucket data) {
        FileWriter w = null;
        newtrees = new HashMap<String, SortedSet<TermEntry>>();
        terms = new TreeSet<String>();
        int entriesAdded = 0;
        InputStream is = null;
        try {
            Logger.normal(this, "Bucket of buffer received, "+data.size()+" bytes");
            is = data.getInputStream();
            SimpleFieldSet fs = new SimpleFieldSet(new LineReadingInputStream(is), 1024, 512, true, true, true);
            idxDisk.setName(fs.get("index.title"));
            idxDisk.setOwnerEmail(fs.get("index.owner.email"));
            idxDisk.setOwner(fs.get("index.owner.name"));
            idxDisk.setTotalPages(fs.getLong("totalPages", -1));
            try{
                while(true){    // Keep going til an EOFExcepiton is thrown
                    TermEntry readObject = TermEntryReaderWriter.getInstance().readObject(is);
                    SortedSet<TermEntry> set = newtrees.get(readObject.subj);
                    if(set == null)
                        newtrees.put(readObject.subj, set = new TreeSet<TermEntry>());
                    set.add(readObject);
                    terms.add(readObject.subj);
                    entriesAdded++;
                }
            }catch(EOFException e){
                // EOF, do nothing
            }
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            Closer.close(is);
        }
        return entriesAdded;
    }

    /** Create a directory for an on-disk index.
     * @return False if something broke and we can't continue. */
	private boolean createDiskDir() {
        dirNumber++;
        idxDiskDir = new File(DISK_DIR_PREFIX + Integer.toString(dirNumber));
        System.out.println("Created new disk dir for merging: "+idxDiskDir);
        if(!(idxDiskDir.mkdir() || idxDiskDir.isDirectory())) {
            Logger.error(this, "Unable to create new disk dir: "+idxDiskDir);
            synchronized(this) {
                pushBroken = true;
                return false;
            }
        }
        return true;
    }

    /** Set up the serialisers for an on-disk index.
     * @return False if something broke and we can't continue. */
    private boolean makeDiskDirSerialiser() {
        if(srlDisk == null) {
            srlDisk = ProtoIndexSerialiser.forIndex(idxDiskDir);
            LiveArchiver<Map<String,Object>,SimpleProgress> archiver = 
                (LiveArchiver<Map<String,Object>,SimpleProgress>)(srlDisk.getChildSerialiser());
            leafsrlDisk = ProtoIndexComponentSerialiser.get(ProtoIndexComponentSerialiser.FMT_FILE_LOCAL, archiver);
            if(lastDiskIndexName == null) {
                try {
                    idxDisk = new ProtoIndex(new FreenetURI("CHK@"), "test", null, null, 0L);
                } catch (java.net.MalformedURLException e) {
                    throw new AssertionError(e);
                }
                // FIXME more hacks: It's essential that we use the same FileArchiver instance here.
                leafsrlDisk.setSerialiserFor(idxDisk);
            } else {
                try {
                    PullTask<ProtoIndex> pull = new PullTask<ProtoIndex>(lastDiskIndexName);
                    System.out.println("Pulling previous index "+lastDiskIndexName+" from disk so can update it.");
                    srlDisk.pull(pull);
                    System.out.println("Pulled previous index "+lastDiskIndexName+" from disk - updating...");
                    idxDisk = pull.data;
                    if(idxDisk.getSerialiser().getLeafSerialiser() != archiver)
                        throw new IllegalStateException("Different serialiser: "+idxFreenet.getSerialiser()+" should be "+leafsrl);
                } catch (TaskAbortException e) {
                    Logger.error(this, "Failed to download previous index for spider update: "+e, e);
                    System.err.println("Failed to download previous index for spider update: "+e);
                    e.printStackTrace();
                    synchronized(freenetMergeSync) {
                        pushBroken = true;
                    }
                    return false;
                }
            }
        }
        return true;
    }

    static final String INDEX_DOCNAME = "index.yml";
	
	private ProtoIndexComponentSerialiser leafsrl;
	
	/** Merge a disk dir to an on-Freenet index. Usually called on startup, i.e. we haven't just
	 * created the on-disk index so we need to setup the ProtoIndex etc. */
	protected void mergeToFreenet(File diskDir) {
		ProtoIndexSerialiser s = ProtoIndexSerialiser.forIndex(diskDir);
		LiveArchiver<Map<String,Object>,SimpleProgress> archiver = 
			(LiveArchiver<Map<String,Object>,SimpleProgress>)(s.getChildSerialiser());
		ProtoIndexComponentSerialiser leaf = ProtoIndexComponentSerialiser.get(ProtoIndexComponentSerialiser.FMT_FILE_LOCAL, archiver);
		String f = this.readStringFrom(new File(diskDir, LAST_DISK_FILENAME));
		if(f == null) {
            if(diskDir.list().length == 0) {
                System.err.println("Directory "+diskDir+" is empty. Nothing to merge.");
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
			Logger.error(this, "Failed to download previous index for spider update: "+e, e);
			System.err.println("Failed to download previous index for spider update: "+e);
			e.printStackTrace();
			synchronized(freenetMergeSync) {
				pushBroken = true;
			}
			return;
		}
		mergeToFreenet(idxDisk, diskDir);
	}

	private final Object inflateSync = new Object();
	
	/** Merge from an on-disk index to an on-Freenet index.
	 * @param diskToMerge The on-disk index.
	 * @param diskDir The folder the on-disk index is stored in.
	 */
	protected void mergeToFreenet(ProtoIndex diskToMerge, File diskDir) {
		System.out.println("Merging on-disk index to Freenet: "+diskDir);
		if(lastUploadURI == null) {
		    lastUploadURI = readURIFrom(new File(LAST_URL_FILENAME));
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
			System.out.println("Merging "+terms.size()+" terms from disk to Freenet...");
			assert(terms.size() == diskToMerge.ttab.size());
			assert(idxFreenet.ttab.isBare());
			assert(diskToMerge.ttab.isBare());
			long entriesAdded = terms.size();
			// Run the actual merge.
			idxFreenet.ttab.update(terms, null, clo, new TaskAbortExceptionConvertor());
			assert(idxFreenet.ttab.isBare());
			// Deflate the main tree.
			newtrees.deflate();
			assert(diskToMerge.ttab.isBare());
			
			// Push the top node to a CHK.
			PushTask<ProtoIndex> task4 = new PushTask<ProtoIndex>(idxFreenet);
			task4.meta = FreenetURI.EMPTY_CHK_URI;
			srl.push(task4);

			// Now wait for the inserts to finish. They are started asynchronously in the above merge.
			FreenetArchiver<Map<String, Object>> arch = 
			    (FreenetArchiver<Map<String, Object>>) srl.getChildSerialiser();
			arch.waitForAsyncInserts();
			
			long mergeEndTime = System.currentTimeMillis();
			System.out.println(entriesAdded + " entries merged in " + (mergeEndTime-mergeStartTime) + " ms, root at " + task4.meta);
			FreenetURI uri = (FreenetURI)task4.meta;
			lastUploadURI = uri;
			if(writeURITo(new File(LAST_URL_FILENAME), uri)) {
				newtrees.deflate();
				diskToMerge = null;
				terms = null;
				System.out.println("Finished with disk index "+diskDir);
				FileUtil.removeAll(diskDir);
			}
			
			// Create the USK to redirect to the CHK at the top of the index.
			uploadUSKForFreenetIndex(uri);
			
		} catch (TaskAbortException e) {
		    Logger.error(this, "Failed to upload index for spider: "+e, e);
		    System.err.println("Failed to upload index for spider: "+e);
		    e.printStackTrace();
		    synchronized(freenetMergeSync) {
		        pushBroken = true;
		    }
		}
	}

	private void uploadUSKForFreenetIndex(FreenetURI uri) {
		FreenetURI privUSK = spiderIndexURIs.getPrivateUSK();
		try {
			FreenetURI tmp = pr.getHLSimpleClient().insertRedirect(privUSK, uri);
			long ed;
			synchronized(freenetMergeSync) {
				ed = spiderIndexURIs.setEdition(tmp.getEdition()+1);
			}
			System.out.println("Uploaded index as USK to "+tmp);
			
			writeStringTo(new File(EDITION_FILENAME), Long.toString(ed));
			
		} catch (InsertException e) {
			System.err.println("Failed to upload USK for index update: "+e);
			e.printStackTrace();
			Logger.error(this, "Failed to upload USK for index update", e);
		}
	}

	/** Create a Closure which will merge the subtrees from one index (on disk) into the subtrees 
	 * of another index (on Freenet). It will be called with each subtree from the on-Freenet 
	 * index, and will merge data from the relevant on-disk subtree. Both subtrees are initially 
	 * deflated, and should be deflated when we leave the method, to avoid running out of memory.
	 * @param newtrees The on-disk tree of trees to get data from.
	 * @return
	 */
	private Closure<Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException> createMergeFromTreeClosure(final SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> newtrees) {
		return new
		Closure<Map.Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException>() {
			/*@Override**/ public void invoke(Map.Entry<String, SkeletonBTreeSet<TermEntry>> entry) throws TaskAbortException {
				String key = entry.getKey();
				SkeletonBTreeSet<TermEntry> tree = entry.getValue();
				if (logMINOR) Logger.minor(this, "Processing: "+key+" : "+tree);
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
					// CONCURRENCY: Because the lower-level trees are packed by the top tree, the bottom
					// trees (SkeletonBTreeSet's) are not independant of each other. When the newtrees 
					// inflate above runs, it can deflate a tree that is still in use by another instance
					// of this callback. Therefore we must COPY IT AND DEFLATE IT INSIDE THE LOCK.
					entries.inflate();
					data = new TreeSet<TermEntry>(entries);
					entries.deflate();
					assert(entries.isBare());
				}
				if (tree != null) {
					if (newTree) {
						tree.addAll(data);
						assert(tree.size() == data.size());
						Logger.debug(this, "Added data to Freenet for term "+key+" : "+data.size());
					} else {
						int oldSize = tree.size();
						tree.update(data, null);
						// Note that it is possible for data.size() + oldSize != tree.size(), because we might be merging data we've already merged.
						// But most of the time it will add up.
						Logger.debug(this, "Merged data to Freenet in term "+key+" : "+data.size()+" + "+oldSize+" -> "+tree.size());
					}
					tree.deflate();
					assert(tree.isBare());
					if(logMINOR) Logger.minor(this, "Updated: "+key+" : "+tree);
				}
			}
		};
	}

	/** Update the overall metadata for the on-Freenet index from the on-disk index. */
	private void updateOverallMetadata(ProtoIndex diskToMerge) {
		idxFreenet.setName(diskToMerge.getName());
		idxFreenet.setOwnerEmail(diskToMerge.getOwnerEmail());
		idxFreenet.setOwner(diskToMerge.getOwner());
		// This is roughly accurate, it might not be exactly so if we process a bit out of order.
		idxFreenet.setTotalPages(diskToMerge.getTotalPages() + Math.max(0,idxFreenet.getTotalPages()));
	}

	/** Setup the serialisers for uploading to Freenet. These convert tree nodes to and from blocks
	 * on Freenet, essentially. */
	private void makeFreenetSerialisers() {
		if(srl == null) {
			srl = ProtoIndexSerialiser.forIndex(lastUploadURI, RequestStarter.BULK_SPLITFILE_PRIORITY_CLASS);
			LiveArchiver<Map<String,Object>,SimpleProgress> archiver = 
				(LiveArchiver<Map<String,Object>,SimpleProgress>)(srl.getChildSerialiser());
			leafsrl = ProtoIndexComponentSerialiser.get(ProtoIndexComponentSerialiser.FMT_DEFAULT, archiver);
			if(lastUploadURI == null) {
				try {
					idxFreenet = new ProtoIndex(new FreenetURI("CHK@"), "test", null, null, 0L);
				} catch (java.net.MalformedURLException e) {
					throw new AssertionError(e);
				}
				// FIXME more hacks: It's essential that we use the same FreenetArchiver instance here.
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
					Logger.error(this, "Failed to download previous index for spider update: "+e, e);
					System.err.println("Failed to download previous index for spider update: "+e);
					e.printStackTrace();
					synchronized(freenetMergeSync) {
						pushBroken = true;
					}
					return;
				}
			}
		}
	}

	/** Set up the on-disk cache, which keeps a copy of everything we upload to Freenet, so we 
	 * won't need to re-download it, which can be very slow and doesn't always succeed. */
	private void setupFreenetCacheDir() {
		if(FreenetArchiver.getCacheDir() == null) {
			File dir = new File("library-spider-pushed-data-cache");
			dir.mkdir();
			FreenetArchiver.setCacheDir(dir);
		}
	}

	protected static SkeletonBTreeSet<TermEntry> makeEntryTree(ProtoIndexComponentSerialiser leafsrl) {
		SkeletonBTreeSet<TermEntry> tree = new SkeletonBTreeSet<TermEntry>(ProtoIndex.BTREE_NODE_MIN);
		leafsrl.setSerialiserFor(tree);
		return tree;
	}

	public void start() {
		final String[] oldToMerge;
		synchronized(freenetMergeSync) {
			oldToMerge = new File(".").list(new FilenameFilter() {
				
				public boolean accept(File arg0, String arg1) {
					if(!(arg1.toLowerCase().startsWith(BASE_FILENAME_PUSH_DATA))) return false;
					File f = new File(arg0, arg1);
					if(!f.isFile()) return false;
					if(f.length() == 0) { f.delete(); return false; }
					String s = f.getName().substring(BASE_FILENAME_PUSH_DATA.length());
					pushNumber = Math.max(pushNumber, Long.parseLong(s)+1);
					return true;
				}
				
			});
		}
		final String[] dirsToMerge;
		synchronized(freenetMergeSync) {
			dirsToMerge = new File(".").list(new FilenameFilter() {
				
				public boolean accept(File arg0, String arg1) {
					if(!(arg1.toLowerCase().startsWith(DISK_DIR_PREFIX))) return false;
					File f = new File(arg0, arg1);
					String s = f.getName().substring(DISK_DIR_PREFIX.length());
					dirNumber = Math.max(dirNumber, Integer.parseInt(s)+1);
					return true;
				}
				
			});
		}
		if(oldToMerge != null && oldToMerge.length > 0) {
			System.out.println("Found "+oldToMerge.length+" buckets of old index data to merge...");
			Runnable r = new Runnable() {

				public void run() {
					synchronized(freenetMergeSync) {
						for(String filename : oldToMerge) {
							File f = new File(filename);
							toMergeToDisk.add(new FileBucket(f, true, false, false, true));
						}
					}
					wrapMergeToDisk();
				}
				
			};
			pr.getNode().executor.execute(r, "Library: handle index data from previous run");
		}
		if(dirsToMerge != null && dirsToMerge.length > 0) {
			System.out.println("Found "+dirsToMerge.length+" disk trees of old index data to merge...");
			Runnable r = new Runnable() {

				public void run() {
					synchronized(freenetMergeSync) {
						while(freenetMergeRunning) {
							if(pushBroken) return;
							System.err.println("Need to merge to Freenet, but last merge not finished yet. Waiting...");
							try {
								freenetMergeSync.wait();
							} catch (InterruptedException e) {
								// Ignore
							}
						}
						if(pushBroken) return;
						freenetMergeRunning = true;
					}
					try {
						for(String filename : dirsToMerge) {
							File f = new File(filename);
							mergeToFreenet(f);
						}
					} finally {
						synchronized(freenetMergeSync) {
							freenetMergeRunning = false;
							if(!pushBroken)
								lastMergedToFreenet = System.currentTimeMillis();
							freenetMergeSync.notifyAll();
						}
					}

				}
				
			};
			pr.getNode().executor.execute(r, "Library: handle trees from previous run");
		}
	}

	public void handlePushBuffer(SimpleFieldSet params, Bucket data) {

		if(data.size() == 0) {
			Logger.error(this, "Bucket of data ("+data+") to push is empty", new Exception("error"));
			System.err.println("Bucket of data ("+data+")to push from Spider is empty");
			data.free();
			return;
		}
		
		// Process data off-thread, but only one load at a time.
		// Hence it won't stall Spider unless we get behind.
		
		long pn;
		synchronized(this) {
			pn = pushNumber++;
		}
		
		final File pushFile = new File(BASE_FILENAME_PUSH_DATA+pn);
		Bucket output = new FileBucket(pushFile, false, false, false, true);
		try {
			BucketTools.copy(data, output);
			data.free();
			System.out.println("Written data to "+pushFile);
		} catch (IOException e1) {
			System.err.println("Unable to back up push data #"+pn+" : "+e1);
			e1.printStackTrace();
			Logger.error(this, "Unable to back up push data #"+pn, e1);
			output = data;
		}
		
		synchronized(freenetMergeSync) {
			boolean waited = false;
			while(toMergeToDisk.size() > MAX_HANDLING_COUNT && !pushBroken) {
				Logger.error(this, "Spider feeding us data too fast, waiting for background process to finish. Ahead of us in the queue: "+toMergeToDisk.size());
				try {
					waited = true;
					freenetMergeSync.wait();
				} catch (InterruptedException e) {
					// Ignore
				}
			}
			toMergeToDisk.add(output);
			if(pushBroken) {
				if(toMergeToDisk.size() < PUSH_BROKEN_MAX_HANDLING_COUNT)
					// We have written the data, it will be recovered after restart.
					Logger.error(this, "Pushing is broken, failing");
				else {
					// Wait forever to prevent running out of disk space.
					// Spider is single threaded.
					// FIXME: Use an error return or a throwable to shut down Spider.
					while(true) {
						try {
							freenetMergeSync.wait();
						} catch (InterruptedException e) {
							// Ignore
						}
					}
				}
				return;
			}
			if(waited)
				Logger.error(this, "Waited for previous handler to go away, moving on...");
			//if(freenetMergeRunning) return; // Already running, no need to restart it.
			if(diskMergeRunning) return; // Already running, no need to restart it.
		}
		Runnable r = new Runnable() {
			
			public void run() {
//				wrapMergeToFreenet();
				wrapMergeToDisk();
			}
			
		};
		pr.getNode().executor.execute(r, "Library: Handle data from Spider");
	}

	public FreenetURI getPublicUSKURI() {
		return spiderIndexURIs.getPublicUSK();
	}

	public void handleGetSpiderURI(PluginReplySender replysender) {
		FreenetURI uri = getPublicUSKURI();
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putSingle("reply", "getSpiderURI");
		sfs.putSingle("publicUSK", uri.toString(true, false));
		try {
			replysender.send(sfs);
		} catch (PluginNotFoundException e) {
			// Race condition, ignore.
		}
	}



}
