/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library;

import freenet.pluginmanager.PluginReplySender;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.io.BucketTools;
import freenet.support.io.Closer;
import freenet.support.io.FileBucket;
import freenet.support.io.LineReadingInputStream;
import freenet.support.io.NativeThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;

import plugins.Library.client.FreenetArchiver;
import plugins.Library.index.ProtoIndex;
import plugins.Library.index.ProtoIndexComponentSerialiser;
import plugins.Library.index.ProtoIndexSerialiser;
import plugins.Library.index.TermEntry;
import plugins.Library.search.Search;
import plugins.Library.ui.WebInterface;
import plugins.Library.util.SkeletonBTreeSet;
import plugins.Library.util.TaskAbortExceptionConvertor;
import plugins.Library.util.concurrent.Executors;
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
		synchronized(handlingSync) {
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
		if(oldToMerge != null && oldToMerge.length > 0) {
			System.out.println("Found "+oldToMerge.length+" buckets of old index data to merge...");
			Runnable r = new Runnable() {

				public void run() {
					synchronized(handlingSync) {
						for(String filename : oldToMerge) {
							File f = new File(filename);
							toHandle.add(new FileBucket(f, true, false, false, false, true));
						}
					}
					innerHandle();
				}
				
			};
			pr.getNode().executor.execute(r, "Library: handle index data from previous run");
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

	private Object handlingSync = new Object();
	private boolean runningHandler = false;
	
	private final ArrayList<Bucket> toHandle = new ArrayList<Bucket>();
	static final int MAX_HANDLING_COUNT = 5; 
	// When pushing is broken, allow max handling to reach this level before stalling forever to prevent running out of disk space.
	private int PUSH_BROKEN_MAX_HANDLING_COUNT = 10;
	// Don't use too much disk space, take into account fact that XMLSpider slows down over time.
	
	private boolean pushBroken;
	
	ProtoIndexSerialiser srl = null;
	FreenetURI lastUploadURI = null;
	ProtoIndex idx;
	FreenetURI privURI;
	FreenetURI pubURI;
	long edition;
	long pushNumber;
	static final String LAST_URL_FILENAME = "library.index.lastpushed.chk";
	static final String PRIV_URI_FILENAME = "library.index.privkey";
	static final String PUB_URI_FILENAME = "library.index.pubkey";
	static final String EDITION_FILENAME = "library.index.next-edition";
	
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
			} catch (IOException e1) {
				System.err.println("Unable to back up push data #"+pn+" : "+e1);
				e1.printStackTrace();
				Logger.error(this, "Unable to back up push data #"+pn, e1);
				output = data;
			}
			
			synchronized(handlingSync) {
				boolean waited = false;
				while(toHandle.size() > MAX_HANDLING_COUNT && !pushBroken) {
					Logger.error(this, "XMLSpider feeding us data too fast, waiting for background process to finish. Ahead of us in the queue: "+toHandle.size());
					try {
						waited = true;
						handlingSync.wait();
					} catch (InterruptedException e) {
						// Ignore
					}
				}
				toHandle.add(output);
				if(pushBroken) {
					if(toHandle.size() < PUSH_BROKEN_MAX_HANDLING_COUNT)
						// We have written the data, it will be recovered after restart.
						Logger.error(this, "Pushing is broken, failing");
					else {
						// Wait forever to prevent running out of disk space.
						// XMLSpider is single threaded.
						// FIXME: Use an error return or a throwable to shut down XMLSpider.
						while(true) {
							try {
								handlingSync.wait();
							} catch (InterruptedException e) {
								// Ignore
							}
						}
					}
					return;
				}
				if(waited)
					Logger.error(this, "Waited for previous handler to go away, moving on...");
				if(runningHandler) return; // Already running, no need to restart it.
			}
			Runnable r = new Runnable() {
				
				public void run() {
					innerHandle();
				}
				
			};
			pr.getNode().executor.execute(r, "Library: Handle data from XMLSpider");
			
		} else {
			Logger.error(this, "Unknown command : \""+params.get("command"));
		}

	}
	
	public void innerHandle() {
		boolean first = true;
		while(true) {
		final Bucket data;
		synchronized(handlingSync) {
			if(first && runningHandler) {
				Logger.error(this, "Already running a handler!");
				return;
			} else if((!first) && (!runningHandler)) {
				Logger.error(this, "Already running yet runningHandler is false?!");
				return;
			}
			first = false;
			if(toHandle.size() == 0) {
				if(logMINOR) Logger.minor(this, "Nothing to handle");
				runningHandler = false;
				handlingSync.notifyAll();
				return;
			}
			data = toHandle.remove(0);
			handlingSync.notifyAll();
			runningHandler = true;
		}
		try {
		// FIXME symlink issues with writing straight to files?
		// FIXME backup issues with writing straight to files? Factor out and do it properly.
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
				privURI = new FreenetURI(br.readLine()).setDocName("index"); // Else InsertableClientSSK doesn't like it.
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
				InsertableClientSSK key = InsertableClientSSK.createRandom(pr.getNode().random, "index");
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
		innerInnerHandle(data);
		} catch (Throwable t) {
			// Failed.
			synchronized(handlingSync) {
				runningHandler = false;
				pushBroken = true;
				handlingSync.notifyAll();
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
	
	protected void innerInnerHandle(Bucket data) {
			
			if(FreenetArchiver.getCacheDir() == null) {
				File dir = new File("library-spider-pushed-data-cache");
				dir.mkdir();
				FreenetArchiver.setCacheDir(dir);
			}
			
			if(srl == null) {
				srl = ProtoIndexSerialiser.forIndex(lastUploadURI);
				
				try {
					idx = new ProtoIndex(new FreenetURI("CHK@yeah"), "test", null, null, 0L);
				} catch (java.net.MalformedURLException e) {
					throw new AssertionError(e);
				}
				ProtoIndexComponentSerialiser.get().setSerialiserFor(idx);
			}
			
			FileWriter w = null;
			// FIXME do a disk-tree-to-disk-tree merge??? Would allow much bigger data ...
			newtrees = new HashMap<String, SortedSet<TermEntry>>();
			terms = new TreeSet<String>();
			try {
			int entriesAdded = 0;
			try {
				Logger.normal(this, "Bucket of buffer received, "+data.size()+" bytes, not implemented yet, saved to file : buffer");
				File f = new File("buffer");
				f.createNewFile();
				w = new FileWriter(f);
				InputStream is = data.getInputStream();
				SimpleFieldSet fs = new SimpleFieldSet(new LineReadingInputStream(is), 1024, 512, true, true, true);
				idx.setName(fs.get("index.title"));
				idx.setOwnerEmail(fs.get("index.owner.email"));
				idx.setOwner(fs.get("index.owner.name"));
				idx.setTotalPages(fs.getLong("totalPages", -1));
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
			
			// Do the upload
			
			// async merge
			Closure<Map.Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException> clo = new
			Closure<Map.Entry<String, SkeletonBTreeSet<TermEntry>>, TaskAbortException>() {
				/*@Override**/ public void invoke(Map.Entry<String, SkeletonBTreeSet<TermEntry>> entry) throws TaskAbortException {
					String key = entry.getKey();
					SkeletonBTreeSet<TermEntry> tree = entry.getValue();
					if(logMINOR) Logger.minor(this, "Processing: "+key+" : "+tree);
					//System.out.println("handling " + key + ((tree == null)? " (new)":" (old)"));
					if (tree == null) {
						entry.setValue(tree = makeEntryTree());
					}
					assert(tree.isBare());
					tree.update(newtrees.get(key), null);
					newtrees.remove(key);
					assert(tree.isBare());
					if(logMINOR) Logger.minor(this, "Updated: "+key+" : "+tree);
					//System.out.println("handled " + key);
				}
			};
			try {
			long mergeStartTime = System.currentTimeMillis();
			assert(idx.ttab.isBare());
			idx.ttab.update(terms, null, clo, new TaskAbortExceptionConvertor());
			// Synchronize anyway so garbage collector knows about it.
			synchronized(this) {
				newtrees = null;
				terms = null;
			}
			assert(idx.ttab.isBare());
			PushTask<ProtoIndex> task4 = new PushTask<ProtoIndex>(idx);
			srl.push(task4);
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
				data.free();
			} catch (IOException e) {
				Logger.error(this, "Failed to write URL of uploaded index: "+uri, e);
				System.out.println("Failed to write URL of uploaded index: "+uri+" : "+e);
			} finally {
				Closer.close(fos);
			}
			
			// Upload to USK
			FreenetURI privUSK = privURI.setKeyType("USK").setDocName("index").setSuggestedEdition(edition);
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
				synchronized(handlingSync) {
					pushBroken = true;
				}
			}
			} finally {
				synchronized(this) {
					newtrees = null;
					terms = null;
				}
			}
	}

	protected static SkeletonBTreeSet<TermEntry> makeEntryTree() {
		SkeletonBTreeSet<TermEntry> tree = new SkeletonBTreeSet<TermEntry>(ProtoIndex.BTREE_NODE_MIN);
		ProtoIndexComponentSerialiser.get().setSerialiserFor(tree);
		return tree;
	}



}
