/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library;

import freenet.pluginmanager.PluginReplySender;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import java.io.IOException;
import java.util.logging.Level;
import plugins.Library.index.TermEntry;
import plugins.Library.search.Search;
import plugins.Library.ui.WebInterface;
import plugins.Library.util.concurrent.Executors;

import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Executor;
import freenet.l10n.L10n.LANGUAGE;

import freenet.pluginmanager.FredPluginFCP;
import freenet.support.Logger;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import plugins.Library.index.TermEntryReaderWriter;

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
			return "Tester not compiled in; give -Dtester= to ant";
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

	public void setLanguage(LANGUAGE newLanguage) {

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

	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		if("pushBuffer".equals(params.get("command"))){
			Logger.normal(this, "Bucket of buffer received, "+data.size()+" bytes, not implemented yet, saved to file : buffer");
			File f = new File("buffer");
			try {
				f.createNewFile();
				FileWriter w = new FileWriter(f);
				InputStream is = data.getInputStream();
				try{
					while(true){	// Keep going til an EOFExcepiton is thrown
						TermEntry readObject = TermEntryReaderWriter.getInstance().readObject(is);
						w.write(readObject.toString()+"\n");
					}
				}catch(EOFException e){
					// EOF, do nothing
				}
			} catch (IOException ex) {
				java.util.logging.Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
			}
		} else {
			Logger.error(this, "Unknown command : \""+params.get("command"));
		}
	}





}
