package plugins.Library;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;

import freenet.keys.FreenetURI;
import freenet.keys.InsertableClientSSK;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.io.Closer;

class IdentityIndexURIs {
	private FreenetURI privURI;
	private FreenetURI pubURI;
		
	private long edition;
	private final File workingDir;
	
	private final PluginRespirator pr;
	private FreenetURI suggestedInsertURI;
	
	static final String PRIV_URI_FILENAME = "library.index.privkey"; //Used by CuratorIndexURI
	static final String PUB_URI_FILENAME = "library.index.pubkey"; //Used by CuratorIndexURI

	IdentityIndexURIs(PluginRespirator pr, File workingDir, String iURI) throws MalformedURLException {
		this.pr = pr;
		this.workingDir = workingDir;
		this.suggestedInsertURI = new FreenetURI(iURI);
	}
	
	synchronized long setEdition(long newEdition) {
		if(newEdition < edition) return edition;
		else return edition = newEdition;
	}
	
	synchronized FreenetURI loadSSKURIs() { //MULTIPLE IDENTITIES (+) leuchtkaefer add params
		if(privURI == null) {
			
			File f = new File(workingDir,PRIV_URI_FILENAME); //MULTIPLE IDENTITIES (+) leuchtkaefer
			FileInputStream fis = null;
			InsertableClientSSK privkey = null;
			boolean newPrivKey = false;
			try {
				fis = new FileInputStream(f);
				BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
				privURI = new FreenetURI(br.readLine()).setDocName("index.yml"); // Else InsertableClientSSK doesn't like it. MULTIPLE IDENTITIES (+) leuchtkaefer
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

			if(privURI == null) { //TODO Leuchtkaefer I need to use the privURI passed by Curator
				
				InsertableClientSSK key;
				try {
					key = InsertableClientSSK.create(suggestedInsertURI.sskForUSK());
					privURI = key.getInsertURI();
					pubURI = key.getURI();
					newPrivKey = true;
					System.out.println("Created new keypair, pubkey is "+pubURI);
				} catch (MalformedURLException e) {
					e.printStackTrace();
					key = InsertableClientSSK.createRandom(pr.getNode().random, "index.yml"); //TODO leuchtkaefer it is probably wrong to publish in random key
				} 
			}
			FileOutputStream fos = null;
			if(newPrivKey) {
				try {
					File fPriv = new File(workingDir,PRIV_URI_FILENAME);
					fos = new FileOutputStream(fPriv); //MULTIPLE IDENTITIES (+) leuchtkaefer
					OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
					osw.write(privURI.toASCIIString());//MULTIPLE IDENTITIES (+) leuchtkaefer
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
				File fPub = new File(workingDir,PUB_URI_FILENAME);
				fos = new FileOutputStream(fPub);//MULTIPLE IDENTITIES (+) leuchtkaefer
				OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
				osw.write(pubURI.toASCIIString());//MULTIPLE IDENTITIES (+) leuchtkaefer
				osw.close();
				fos = null;
			} catch (IOException e) {
				Logger.error(this, "Failed to write new pubkey", e);
				System.out.println("Failed to write new pubkey: "+e);
			} finally {
				Closer.close(fos);
			}
			try {
				File fEd = new File(workingDir,IdentityIndexUploader.EDITION_FILENAME);
				fis = new FileInputStream(fEd);
				BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
				try {
					edition = Long.parseLong(br.readLine());
				} catch (NumberFormatException e) {
					edition = Long.valueOf(0); 
				}
				System.out.println("Edition: "+edition);
				fis.close();
				fis = null;
			} catch (IOException e) {
				// Ignore
				edition = Long.valueOf(0); 
			} finally {
				Closer.close(fis);
			}
		}
		return privURI;
	}

	synchronized FreenetURI getPrivateUSK() {
		return loadSSKURIs().setKeyType("USK").setDocName(IdentityIndexUploader.INDEX_DOCNAME).setSuggestedEdition(edition);//MULTIPLE IDENTITIES (+) leuchtkaefer
	}

	/** Will return edition -1 if no successful uploads so far, otherwise the correct edition. */
	synchronized FreenetURI getPublicUSK() {//MULTIPLE IDENTITIES (+) leuchtkaefer
		loadSSKURIs();//MULTIPLE IDENTITIES (+) leuchtkaefer
		return pubURI.setKeyType("USK").setDocName(IdentityIndexUploader.INDEX_DOCNAME).setSuggestedEdition(getLastUploadedEdition()); 
	}

	private synchronized long getLastUploadedEdition() {
		/** If none uploaded, return -1, otherwise return the last uploaded version. */
		return edition-1;
	}

}
