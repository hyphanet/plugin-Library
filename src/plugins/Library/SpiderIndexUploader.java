package plugins.Library;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.io.BucketTools;
import freenet.support.io.FileBucket;


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

	private final SpiderIndexURIs spiderIndexURIs;
	
	private long pushNumber;
	static final String PRIV_URI_FILENAME = "library.index.privkey";
	static final String PUB_URI_FILENAME = "library.index.pubkey";
	static final String EDITION_FILENAME = "library.index.last-edition";
	
	static final String BASE_FILENAME_PUSH_DATA = "library.index.data.";
	
    static final String INDEX_DOCNAME = "index.yml";

	public void start() {
		System.out.println("Started pass-though spider uploader.");
	}

	public void handlePushBuffer(SimpleFieldSet params, Bucket data) {
		if(data.size() == 0) {
			Logger.error(this, "Bucket of data ("+data+") to push is empty", new Exception("error"));
			System.err.println("Bucket of data ("+data+")to push from Spider is empty");
			data.free();
			return;
		}
		
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
		}
		
        // Stall Spider if we get behind.
		int countFilesToMerge = 0;
		int tooManyFilesToMerge = 0;
		do {
			if (tooManyFilesToMerge > 0) {
				System.out.println("There are " + countFilesToMerge + " files to merge...stalling spider.");				
				try {
					Thread.sleep(
							tooManyFilesToMerge * 
							tooManyFilesToMerge *
							100 *
							1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
	        String[] filesToMerge = new File(".").list(new FilenameFilter() {
	            
	            public boolean accept(File arg0, String arg1) {
	                if(!(arg1.toLowerCase().startsWith(BASE_FILENAME_PUSH_DATA))) return false;
	                File f = new File(arg0, arg1);
	                if(!f.isFile()) return false;
	                if(f.length() == 0) { f.delete(); return false; }
	                return true;
	            }
	                        
	        });
	
	        countFilesToMerge = filesToMerge.length;
	        tooManyFilesToMerge = countFilesToMerge - 20;
		} while (tooManyFilesToMerge > 0);
		System.out.println("There are " + countFilesToMerge + " files to merge.");
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
