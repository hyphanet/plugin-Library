/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library;

import freenet.pluginmanager.PluginReplySender;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.io.Closer;
import freenet.support.io.NativeThread;

import java.io.IOException;
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
import freenet.keys.FreenetURI;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import plugins.Library.index.TermEntryReaderWriter;
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
	private boolean handling;
	
	ProtoIndexSerialiser srl = null;
	FreenetURI lastUploadURI = null;
	ProtoIndex idx;
	
	static final String LAST_URL_FILENAME = "library.index.lastpushed.chk";
	
	public void handle(PluginReplySender replysender, SimpleFieldSet params, final Bucket data, int accesstype) {
		if("pushBuffer".equals(params.get("command"))){

			// Process data off-thread, but only one load at a time.
			// Hence it won't stall XMLSpider unless we get behind.
			
			synchronized(handlingSync) {
				while(handling) {
					Logger.error(this, "XMLSpider feeding us data too fast, waiting for background process to finish");
					try {
						handlingSync.wait();
					} catch (InterruptedException e) {
						// Ignore
					}
				}
				handling = true;
				Logger.error(this, "Waited for previous handler to go away, moving on...");
			}
			if(lastUploadURI == null) {
				File f = new File(LAST_URL_FILENAME);
				FileInputStream fis = null;
				try {
					fis = new FileInputStream(f);
					BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
					lastUploadURI = new FreenetURI(br.readLine());
					System.out.println("Continuing from last index URI: "+lastUploadURI);
				} catch (IOException e) {
					// Ignore
				} finally {
					Closer.close(fis);
				}
			}
			try {
				Runnable r = new Runnable() {

					public void run() {
						
						try {
						
						if(FreenetArchiver.getCacheDir() == null) {
							FreenetArchiver.setCacheDir(new File("library-spider-pushed-data-cache"));
						}
						
						if(srl == null) {
							srl = ProtoIndexSerialiser.forIndex(lastUploadURI);
							
							try {
								idx = new ProtoIndex(new FreenetURI("CHK@yeah"), "test");
							} catch (java.net.MalformedURLException e) {
								throw new AssertionError(e);
							}
							ProtoIndexComponentSerialiser.get().setSerialiserFor(idx);
						}
						
						FileWriter w = null;
						final Map<String, SortedSet<TermEntry>> newtrees = new HashMap<String, SortedSet<TermEntry>>();
						final SortedSet<String> terms = new TreeSet<String>();
						int entriesAdded = 0;
						try {
							Logger.normal(this, "Bucket of buffer received, "+data.size()+" bytes, not implemented yet, saved to file : buffer");
							File f = new File("buffer");
							f.createNewFile();
							w = new FileWriter(f);
							InputStream is = data.getInputStream();
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
								//System.out.println("handling " + key + ((tree == null)? " (new)":" (old)"));
								if (tree == null) {
									entry.setValue(tree = makeEntryTree());
								}
								assert(tree.isBare());
								tree.update(newtrees.get(key), null);
								assert(tree.isBare());
								//System.out.println("handled " + key);
							}
						};
						try {
						long mergeStartTime = System.currentTimeMillis();
						assert(idx.ttab.isBare());
						idx.ttab.update(terms, null, clo);
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
						} catch (IOException e) {
							Logger.error(this, "Failed to write URL of uploaded index: "+uri, e);
							System.out.println("Failed to write URL of uploaded index: "+uri+" : "+e);
						} finally {
							Closer.close(fos);
						}
						} catch (TaskAbortException e) {
							Logger.error(this, "Failed to upload index for spider: "+e, e);
							System.err.println("Failed to upload index for spider: "+e);
							e.printStackTrace();
						}
						
						} finally {
							synchronized(handlingSync) {
								handling = false;
								handlingSync.notifyAll();
							}
						}						
					}
					
				};
				new NativeThread(r, "Library: Handle data from XMLSpider", NativeThread.NORM_PRIORITY, false).start();
			} catch (RuntimeException t) {
				synchronized(handlingSync) {
					handling = false;
				}
				throw t;
			} catch (Error t) {
				synchronized(handlingSync) {
					handling = false;
				}
				throw t;
			}
		} else {
			Logger.error(this, "Unknown command : \""+params.get("command"));
		}
	}
	
	protected static SkeletonBTreeSet<TermEntry> makeEntryTree() {
		SkeletonBTreeSet<TermEntry> tree = new SkeletonBTreeSet<TermEntry>(ProtoIndex.BTREE_NODE_MIN);
		ProtoIndexComponentSerialiser.get().setSerialiserFor(tree);
		return tree;
	}



}
