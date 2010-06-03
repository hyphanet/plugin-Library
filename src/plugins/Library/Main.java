/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library;

import freenet.pluginmanager.PluginReplySender;
import freenet.support.MutableBoolean;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.io.BucketTools;
import freenet.support.io.Closer;
import freenet.support.io.FileBucket;
import freenet.support.io.FileUtil;
import freenet.support.io.LineReadingInputStream;
import freenet.support.io.NativeThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;

import plugins.Library.client.FreenetArchiver;
import plugins.Library.index.ProtoIndex;
import plugins.Library.index.ProtoIndexComponentSerialiser;
import plugins.Library.index.ProtoIndexSerialiser;
import plugins.Library.index.TermEntry;
import plugins.Library.index.TermPageEntry;
import plugins.Library.search.Search;
import plugins.Library.ui.WebInterface;
import plugins.Library.util.SkeletonBTreeMap;
import plugins.Library.util.SkeletonBTreeSet;
import plugins.Library.util.TaskAbortExceptionConvertor;
import plugins.Library.util.concurrent.Executors;
import plugins.Library.util.exec.SimpleProgress;
import plugins.Library.util.exec.TaskAbortException;
import plugins.Library.util.func.Closure;

import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Executor;
import freenet.client.InsertException;
import freenet.keys.FreenetURI;
import freenet.keys.InsertableClientSSK;
import freenet.l10n.BaseL10n.LANGUAGE;

import freenet.pluginmanager.FredPluginFCP;
import freenet.support.Logger;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import plugins.Library.index.TermEntryReaderWriter;
import plugins.Library.index.xml.LibrarianHandler;
import plugins.Library.io.serial.LiveArchiver;
import plugins.Library.io.serial.Serialiser.PullTask;
import plugins.Library.io.serial.Serialiser.PushTask;

/**
 * Library class is the api for others to use search facilities, it is used by the interfaces
 * @author MikeB
 */
public class Main implements FredPlugin, FredPluginVersioned, freenet.pluginmanager.FredPluginHTTP, // TODO remove this later
		FredPluginRealVersioned, FredPluginThreadless, FredPluginL10n, FredPluginFCP {

	private static PluginRespirator pr;
	private Library library;
	private WebInterface webinterface;
	
	static volatile boolean logMINOR;
	static volatile boolean logDEBUG;
	
	static {
		Logger.registerClass(Main.class);
	}

	public static PluginRespirator getPluginRespirator() {
		return pr;
	}

	// FredPluginL10n
	public void setLanguage(freenet.l10n.BaseL10n.LANGUAGE lang) {
		// TODO implement
	}

	// FredPluginVersioned
	public String getVersion() {
		return library.getVersion() + " " + Version.vcsRevision();
	}

	// FredPluginRealVersioned
	public long getRealVersion() {
		return library.getVersion();
	}

	// FredPluginHTTP
	// TODO remove this later
	public String handleHTTPGet(freenet.support.api.HTTPRequest request) {
		Throwable th;
		try {
			Class<?> tester = Class.forName("plugins.Library.Tester");
			java.lang.reflect.Method method = tester.getMethod("runTest", Library.class, String.class);
			try {
				return (String)method.invoke(null, library, request.getParam("plugins.Library.Tester"));
			} catch (java.lang.reflect.InvocationTargetException e) {
				throw e.getCause();
			}
		} catch (ClassNotFoundException e) {
			return "<p>To use Library, go to <b>Browsing -&gt; Search Freenet</b> in the main menu in FProxy.</p><p>This page is only where the test suite would be, if it had been compiled in (give -Dtester= to ant).</p>";
		} catch (Throwable t) {
			th = t;
		}
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		th.printStackTrace(new java.io.PrintStream(bytes));
		return "<pre>" + bytes + "</pre>";
	}
	// TODO remove this later
	public String handleHTTPPost(freenet.support.api.HTTPRequest request) { return null; }

	// FredPlugin
	public void runPlugin(PluginRespirator pr) {
		Main.pr = pr;
		Executor exec = pr.getNode().executor;
		library = Library.init(pr);
		Search.setup(library, exec);
		Executors.setDefaultExecutor(exec);
		webinterface = new WebInterface(library, pr);
		webinterface.load();
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
							toMergeToDisk.add(new FileBucket(f, true, false, false, false, true));
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

	public void terminate() {
		webinterface.unload();
	}

	public String getString(String key) {
		return key;
	}

	private static String convertToHex(byte[] data) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < data.length; i++) {
			int halfbyte = (data[i] >>> 4) & 0x0F;
			int two_halfs = 0;
			do {
				if ((0 <= halfbyte) && (halfbyte <= 9))
					buf.append((char) ('0' + halfbyte));
				else
					buf.append((char) ('a' + (halfbyte - 10)));
				halfbyte = data[i] & 0x0F;
			} while (two_halfs++ < 1);
		}
		return buf.toString();
	}

	//this function will return the String representation of the MD5 hash for the input string
	public static String MD5(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] b = text.getBytes("UTF-8");
			md.update(b, 0, b.length);
			byte[] md5hash = md.digest();
			return convertToHex(md5hash);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Object freenetMergeSync = new Object();
	private boolean freenetMergeRunning = false;
	private boolean diskMergeRunning = false;
	
	private final ArrayList<Bucket> toMergeToDisk = new ArrayList<Bucket>();
	static final int MAX_HANDLING_COUNT = 5; 
	// When pushing is broken, allow max handling to reach this level before stalling forever to prevent running out of disk space.
	private int PUSH_BROKEN_MAX_HANDLING_COUNT = 10;
	// Don't use too much disk space, take into account fact that XMLSpider slows down over time.
	
	private boolean pushBroken;
	
	/** The temporary on-disk index. We merge stuff into this until it exceeds a threshold size, then
	 * we create a new diskIdx and merge the old one into the idxFreenet. */
	ProtoIndex idxDisk;
	/** idxDisk gets merged into idxFreenet this long after the last merge completed. */
	static final long MAX_TIME = 24*60*60*1000L;
	/** idxDisk gets merged into idxFreenet after this many incoming updates from Spider. */
	static final int MAX_UPDATES = 32;
	/** idxDisk gets merged into idxFreenet after it has grown to this many terms.
	 * Note that the entire main tree of terms (not the sub-trees with the positions and urls in) must
	 * fit into memory during the merge process. */
	static final int MAX_TERMS = 100*1000;
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
	FreenetURI privURI;
	FreenetURI pubURI;
	long edition;
	long pushNumber;
	static final String LAST_URL_FILENAME = "library.index.lastpushed.chk";
	static final String PRIV_URI_FILENAME = "library.index.privkey";
	static final String PUB_URI_FILENAME = "library.index.pubkey";
	static final String EDITION_FILENAME = "library.index.next-edition";
	
	static final String LAST_DISK_FILENAME = "library.index.lastpushed.disk";
	
	static final String BASE_FILENAME_PUSH_DATA = "library.index.data.";
	
	
	public void handle(PluginReplySender replysender, SimpleFieldSet params, final Bucket data, int accesstype) {
		if("pushBuffer".equals(params.get("command"))){

			if(data.size() == 0) {
				Logger.error(this, "Bucket of data ("+data+") to push is empty", new Exception("error"));
				System.err.println("Bucket of data ("+data+")to push from XMLSpider is empty");
				data.free();
				return;
			}
			
			// Process data off-thread, but only one load at a time.
			// Hence it won't stall XMLSpider unless we get behind.
			
			long pn;
			synchronized(this) {
				pn = pushNumber++;
			}
			
			final File pushFile = new File(BASE_FILENAME_PUSH_DATA+pn);
			Bucket output = new FileBucket(pushFile, false, false, false, false, true);
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
					Logger.error(this, "XMLSpider feeding us data too fast, waiting for background process to finish. Ahead of us in the queue: "+toMergeToDisk.size());
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
						// XMLSpider is single threaded.
						// FIXME: Use an error return or a throwable to shut down XMLSpider.
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
//					wrapMergeToFreenet();
					wrapMergeToDisk();
				}
				
			};
			pr.getNode().executor.execute(r, "Library: Handle data from XMLSpider");
			
		} else {
			Logger.error(this, "Unknown command : \""+params.get("command"));
		}

	}
	
	/** Merge from the Bucket chain to the on-disk idxDisk. */
	protected void wrapMergeToDisk() {
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
	// It doesn't need to be synchronized because it's always used from innerInnerHandle, which never runs in parallel.
	private Map<String, SortedSet<TermEntry>> newtrees;
	// Ditto
	private SortedSet<String> terms;
	
	ProtoIndexSerialiser srlDisk = null;
	private ProtoIndexComponentSerialiser leafsrlDisk;
	
	private long lastMergedToFreenet = -1;
	
	private void mergeToDisk(Bucket data) {
		
		boolean newIndex = false;
		
		if(idxDiskDir == null) {
			newIndex = true;
			dirNumber++;
			idxDiskDir = new File(DISK_DIR_PREFIX + Integer.toString(dirNumber));
			System.out.println("Created new disk dir for merging: "+idxDiskDir);
			if(!(idxDiskDir.mkdir() || idxDiskDir.isDirectory())) {
				Logger.error(this, "Unable to create new disk dir: "+idxDiskDir);
				synchronized(this) {
					pushBroken = true;
					return;
				}
			}
		}
		if(srlDisk == null) {
			srlDisk = ProtoIndexSerialiser.forIndex(idxDiskDir);
			LiveArchiver<Map<String,Object>,SimpleProgress> archiver = 
				(LiveArchiver<Map<String,Object>,SimpleProgress>)(srlDisk.getChildSerialiser());
			leafsrlDisk = ProtoIndexComponentSerialiser.get(ProtoIndexComponentSerialiser.FMT_FILE_LOCAL, archiver);
			if(lastDiskIndexName == null) {
				try {
					idxDisk = new ProtoIndex(new FreenetURI("CHK@yeah"), "test", null, null, 0L);
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
					return;
				}
			}
		}
		
		// Read data into newtrees and trees.
		
		FileWriter w = null;
		newtrees = new HashMap<String, SortedSet<TermEntry>>();
		terms = new TreeSet<String>();
		int entriesAdded = 0;
		try {
			Logger.normal(this, "Bucket of buffer received, "+data.size()+" bytes, saved to file : buffer");
			File f = new File("buffer");
			f.createNewFile();
			w = new FileWriter(f);
			InputStream is = data.getInputStream();
			SimpleFieldSet fs = new SimpleFieldSet(new LineReadingInputStream(is), 1024, 512, true, true, true);
			idxDisk.setName(fs.get("index.title"));
			idxDisk.setOwnerEmail(fs.get("index.owner.email"));
			idxDisk.setOwner(fs.get("index.owner.name"));
			idxDisk.setTotalPages(fs.getLong("totalPages", -1));
			try{
				while(true){	// Keep going til an EOFExcepiton is thrown
					TermEntry readObject = TermEntryReaderWriter.getInstance().readObject(is);
					SortedSet<TermEntry> set = newtrees.get(readObject.subj);
					if(set == null)
						newtrees.put(readObject.subj, set = new TreeSet<TermEntry>());
					set.add(readObject);
					terms.add(readObject.subj);
					w.write(readObject.toString()+"\n");
					entriesAdded++;
				}
			}catch(EOFException e){
				// EOF, do nothing
			}
			w.close();
			w = null;
		} catch (IOException ex) {
			java.util.logging.Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
		} finally {
			Closer.close(w);
		}
		
		if(terms.size() == 0) {
			System.out.println("Nothing to merge");
			synchronized(this) {
				newtrees = null;
				terms = null;
			}
			return;
		}
		
		
		
		// Do the upload
		
		try {
			final MutableBoolean maxDiskEntrySizeExceeded = new MutableBoolean();
			maxDiskEntrySizeExceeded.value = false;
			long mergeStartTime = System.currentTimeMillis();
			if(newIndex) {
				// created a new index, fill it with data.
				// DON'T MERGE, merge with a lot of data will deadlock.
				// FIXME throw in update() if it will deadlock.
				for(String key : terms) {
					SkeletonBTreeSet<TermEntry> tree = makeEntryTree(leafsrlDisk);
					SortedSet<TermEntry> toMerge = newtrees.get(key);
					tree.addAll(toMerge);
					if(toMerge.size() > MAX_DISK_ENTRY_SIZE)
						maxDiskEntrySizeExceeded.value = true;
					toMerge = null;
					tree.deflate();
					assert(tree.isBare());
					idxDisk.ttab.put(key, tree);
				}
				idxDisk.ttab.deflate();
			} else {
				// async merge
				Closure<Map.Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException> clo = new
				Closure<Map.Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException>() {
					/*@Override**/ public void invoke(Map.Entry<String, SkeletonBTreeSet<TermEntry>> entry) throws TaskAbortException {
						String key = entry.getKey();
						SkeletonBTreeSet<TermEntry> tree = entry.getValue();
						if(logMINOR) Logger.minor(this, "Processing: "+key+" : "+tree);
						if(tree != null)
							System.out.println("Merging data (on disk) in term "+key);
						else
							System.out.println("Adding new term to disk index:  "+key);
						//System.out.println("handling " + key + ((tree == null)? " (new)":" (old)"));
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
						//System.out.println("handled " + key);
					}
				};
				assert(idxDisk.ttab.isBare());
				System.out.println("Merging "+terms.size()+" terms, tree.size = "+idxDisk.ttab.size()+"...");
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
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(LAST_DISK_FILENAME);
				OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
				osw.write(uri.toString());
				osw.close();
				fos = null;
				data.free();
			} catch (IOException e) {
				Logger.error(this, "Failed to write filename of uploaded index: "+uri, e);
				System.out.println("Failed to write filename of uploaded index: "+uri+" : "+e);
			} finally {
				Closer.close(fos);
			}
			try {
				fos = new FileOutputStream(new File(idxDiskDir, LAST_DISK_FILENAME));
				OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
				osw.write(uri.toString());
				osw.close();
				fos = null;
			} catch (IOException e) {
				Logger.error(this, "Failed to write filename of uploaded index: "+uri, e);
				System.out.println("Failed to write filename of uploaded index: "+uri+" : "+e);
			} finally {
				Closer.close(fos);
			}
			
			// Maybe chain to mergeToFreenet ???
			
			boolean termTooBig = false;
			synchronized(maxDiskEntrySizeExceeded) {
				termTooBig = maxDiskEntrySizeExceeded.value;
			}
			
			mergedToDisk++;
			if(idxDisk.ttab.size() > MAX_TERMS || mergedToDisk > MAX_UPDATES || termTooBig || 
					(lastMergedToFreenet > 0 && (System.currentTimeMillis() - lastMergedToFreenet) > MAX_TIME)) {
				
				final ProtoIndex diskToMerge = idxDisk;
				final File dir = idxDiskDir;
				System.out.println("Exceeded threshold, starting new disk index and starting merge from disk to Freenet...");
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
			}
		} catch (TaskAbortException e) {
			Logger.error(this, "Failed to upload index for spider: "+e, e);
			System.err.println("Failed to upload index for spider: "+e);
			e.printStackTrace();
			synchronized(freenetMergeSync) {
				pushBroken = true;
			}
		}
	}

	static final String INDEX_DOCNAME = "index.yml";
	
	private ProtoIndexComponentSerialiser leafsrl;
	
	protected void mergeToFreenet(File diskDir) {
		ProtoIndexSerialiser s = ProtoIndexSerialiser.forIndex(diskDir);
		LiveArchiver<Map<String,Object>,SimpleProgress> archiver = 
			(LiveArchiver<Map<String,Object>,SimpleProgress>)(s.getChildSerialiser());
		ProtoIndexComponentSerialiser leaf = ProtoIndexComponentSerialiser.get(ProtoIndexComponentSerialiser.FMT_FILE_LOCAL, null);
		String f = null;
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(new File(diskDir, LAST_DISK_FILENAME));
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
			f = br.readLine();
			System.out.println("Continuing old bucket: "+f);
			fis.close();
			fis = null;
		} catch (IOException e) {
			// Ignore
			System.err.println("Unable to merge old data "+diskDir+" : "+e);
			e.printStackTrace();
			Logger.error(this, "Unable to merge old data "+diskDir+" : "+e, e);
		} finally {
			Closer.close(fis);
		}

		ProtoIndex idxDisk = null;
		try {
			PullTask<ProtoIndex> pull = new PullTask<ProtoIndex>(f);
			System.out.println("Pulling previous index "+f+" from disk so can update it.");
			s.pull(pull);
			System.out.println("Pulled previous index "+f+" from disk - updating...");
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
			return;
		}
		mergeToFreenet(idxDisk, diskDir);
	}

	private final Object inflateSync = new Object();
	
	protected void mergeToFreenet(ProtoIndex diskToMerge, File diskDir) {
		if(lastUploadURI == null) {
			File f = new File(LAST_URL_FILENAME);
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(f);
				BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
				lastUploadURI = new FreenetURI(br.readLine());
				System.out.println("Continuing from last index CHK: "+lastUploadURI);
				fis.close();
				fis = null;
			} catch (IOException e) {
				// Ignore
			} finally {
				Closer.close(fis);
			}
		}
		if(privURI == null) {
			File f = new File(PRIV_URI_FILENAME);
			FileInputStream fis = null;
			InsertableClientSSK privkey = null;
			boolean newPrivKey = false;
			try {
				fis = new FileInputStream(f);
				BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
				privURI = new FreenetURI(br.readLine()).setDocName("index.yml"); // Else InsertableClientSSK doesn't like it.
				privkey = InsertableClientSSK.create(privURI);
				System.out.println("Read old privkey");
				this.pubURI = privkey.getURI();
				System.out.println("Recovered URI from disk, pubkey is "+pubURI);
				fis.close();
				fis = null;
			} catch (IOException e) {
				// Ignore
			} finally {
				Closer.close(fis);
			}
			if(privURI == null) {
				InsertableClientSSK key = InsertableClientSSK.createRandom(pr.getNode().random, "index.yml");
				privURI = key.getInsertURI();
				pubURI = key.getURI();
				newPrivKey = true;
				System.out.println("Created new keypair, pubkey is "+pubURI);
			}
			FileOutputStream fos = null;
			if(newPrivKey) {
				try {
					fos = new FileOutputStream(new File(PRIV_URI_FILENAME));
					OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
					osw.write(privURI.toASCIIString());
					osw.close();
					fos = null;
				} catch (IOException e) {
					Logger.error(this, "Failed to write new private key");
					System.out.println("Failed to write new private key : "+e);
				} finally {
					Closer.close(fos);
				}
			}
			try {
				fos = new FileOutputStream(new File(PUB_URI_FILENAME));
				OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
				osw.write(pubURI.toASCIIString());
				osw.close();
				fos = null;
			} catch (IOException e) {
				Logger.error(this, "Failed to write new pubkey", e);
				System.out.println("Failed to write new pubkey: "+e);
			} finally {
				Closer.close(fos);
			}
			try {
				fis = new FileInputStream(new File(EDITION_FILENAME));
				BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
				try {
					edition = Long.parseLong(br.readLine());
				} catch (NumberFormatException e) {
					edition = 1;
				}
				System.out.println("Edition: "+edition);
				fis.close();
				fis = null;
			} catch (IOException e) {
				// Ignore
				edition = 1;
			} finally {
				Closer.close(fis);
			}
			
		}
		
			if(FreenetArchiver.getCacheDir() == null) {
				File dir = new File("library-spider-pushed-data-cache");
				dir.mkdir();
				FreenetArchiver.setCacheDir(dir);
			}
			
			if(srl == null) {
				srl = ProtoIndexSerialiser.forIndex(lastUploadURI);
				LiveArchiver<Map<String,Object>,SimpleProgress> archiver = 
					(LiveArchiver<Map<String,Object>,SimpleProgress>)(srl.getChildSerialiser());
				leafsrl = ProtoIndexComponentSerialiser.get(ProtoIndexComponentSerialiser.FMT_DEFAULT, archiver);
				if(lastUploadURI == null) {
					try {
						idxFreenet = new ProtoIndex(new FreenetURI("CHK@yeah"), "test", null, null, 0L);
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
			
			idxFreenet.setName(diskToMerge.getName());
			idxFreenet.setOwnerEmail(diskToMerge.getOwnerEmail());
			idxFreenet.setOwner(diskToMerge.getOwner());
			// This is roughly accurate, it might not be exactly so if we process a bit out of order.
			idxFreenet.setTotalPages(diskToMerge.getTotalPages());
			
			final SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> newtrees = diskToMerge.ttab;
			
			// Do the upload
			
			// async merge
			Closure<Map.Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException> clo = new
			Closure<Map.Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException>() {
				/*@Override**/ public void invoke(Map.Entry<String, SkeletonBTreeSet<TermEntry>> entry) throws TaskAbortException {
					String key = entry.getKey();
					SkeletonBTreeSet<TermEntry> tree = entry.getValue();
					if(logMINOR) Logger.minor(this, "Processing: "+key+" : "+tree);
					//System.out.println("handling " + key + ((tree == null)? " (new)":" (old)"));
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
					if(tree != null)
						
					if(newTree) {
						tree.addAll(data);
						assert(tree.size() == data.size());
						System.out.println("Added data to Freenet for term "+key+" : "+data.size());
					} else {
						int oldSize = tree.size();
						tree.update(data, null);
						// Note that it is possible for data.size() + oldSize != tree.size(), because we might be merging data we've already merged.
						// But most of the time it will add up.
						System.out.println("Merged data to Freenet in term "+key+" : "+data.size()+" + "+oldSize+" -> "+tree.size());
					}
					tree.deflate();
					assert(tree.isBare());
					if(logMINOR) Logger.minor(this, "Updated: "+key+" : "+tree);
					//System.out.println("handled " + key);
				}
			};
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
			idxFreenet.ttab.update(terms, null, clo, new TaskAbortExceptionConvertor());
			assert(idxFreenet.ttab.isBare());
			newtrees.deflate();
			assert(diskToMerge.ttab.isBare());
			
			PushTask<ProtoIndex> task4 = new PushTask<ProtoIndex>(idxFreenet);
			srl.push(task4);
			
			FreenetArchiver arch = (FreenetArchiver) srl.getChildSerialiser();
			arch.waitForAsyncInserts();
			
			long mergeEndTime = System.currentTimeMillis();
			System.out.print(entriesAdded + " entries merged in " + (mergeEndTime-mergeStartTime) + " ms, root at " + task4.meta + ", ");
			FreenetURI uri = (FreenetURI)task4.meta;
			lastUploadURI = uri;
			System.out.println("Uploaded new index to "+uri);
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(LAST_URL_FILENAME);
				OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
				osw.write(uri.toASCIIString());
				osw.close();
				fos = null;
				newtrees.deflate();
				diskToMerge = null;
				terms = null;
				System.out.println("Finished with disk index "+diskDir);
				FileUtil.removeAll(diskDir);
			} catch (IOException e) {
				Logger.error(this, "Failed to write URL of uploaded index: "+uri, e);
				System.out.println("Failed to write URL of uploaded index: "+uri+" : "+e);
			} finally {
				Closer.close(fos);
			}
			
			// Upload to USK
			FreenetURI privUSK = privURI.setKeyType("USK").setDocName(INDEX_DOCNAME).setSuggestedEdition(edition);
			try {
				FreenetURI tmp = pr.getHLSimpleClient().insertRedirect(privUSK, uri);
				edition = tmp.getEdition()+1;
				System.out.println("Uploaded index as USK to "+tmp);
				
				fos = null;
				try {
					fos = new FileOutputStream(EDITION_FILENAME);
					OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
					osw.write(Long.toString(edition));
					osw.close();
					fos = null;
				} catch (IOException e) {
					Logger.error(this, "Failed to write URL of uploaded index: "+uri, e);
					System.out.println("Failed to write URL of uploaded index: "+uri+" : "+e);
				} finally {
					Closer.close(fos);
				}
				
			} catch (InsertException e) {
				System.err.println("Failed to upload USK for index update: "+e);
				e.printStackTrace();
				Logger.error(this, "Failed to upload USK for index update", e);
			}
			
			} catch (TaskAbortException e) {
				Logger.error(this, "Failed to upload index for spider: "+e, e);
				System.err.println("Failed to upload index for spider: "+e);
				e.printStackTrace();
				synchronized(freenetMergeSync) {
					pushBroken = true;
				}
			}
	}

	protected static SkeletonBTreeSet<TermEntry> makeEntryTree(ProtoIndexComponentSerialiser leafsrl) {
		SkeletonBTreeSet<TermEntry> tree = new SkeletonBTreeSet<TermEntry>(ProtoIndex.BTREE_NODE_MIN);
		leafsrl.setSerialiserFor(tree);
		return tree;
	}



}
