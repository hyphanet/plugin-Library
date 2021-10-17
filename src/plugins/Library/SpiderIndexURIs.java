package plugins.Library;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import freenet.keys.FreenetURI;
import freenet.keys.InsertableClientSSK;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.io.Closer;

class SpiderIndexURIs {
	private FreenetURI privURI;
	private FreenetURI pubURI;
	private long edition;
	private final PluginRespirator pr;
	
	SpiderIndexURIs(PluginRespirator pr) {
		this.pr = pr;
	}
	
	synchronized FreenetURI loadSSKURIs() {
		if(privURI == null) {
			File f = new File(SpiderIndexUploader.PRIV_URI_FILENAME);
			FileInputStream fis = null;
			InsertableClientSSK privkey = null;
			boolean newPrivKey = false;
			try {
				fis = new FileInputStream(f);
				BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
				privURI = new FreenetURI(br.readLine()).setDocName("index.yml"); // Else InsertableClientSSK doesn't like it.
				privkey = InsertableClientSSK.create(privURI);
				Logger.debug(this, "Read old privkey");
				this.pubURI = privkey.getURI();
				Logger.debug(this, "Recovered URI from disk, pubkey is "+pubURI);
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
				Logger.debug(this, "Created new keypair, pubkey is "+pubURI);
			}
			FileOutputStream fos = null;
			if(newPrivKey) {
				try {
					fos = new FileOutputStream(new File(SpiderIndexUploader.PRIV_URI_FILENAME));
					OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
					osw.write(privURI.toASCIIString());
					osw.close();
					fos = null;
				} catch (IOException e) {
					Logger.error(this, "Failed to write new private key : ", e);
				} finally {
					Closer.close(fos);
				}
			}
			try {
				fos = new FileOutputStream(new File(SpiderIndexUploader.PUB_URI_FILENAME));
				OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
				osw.write(pubURI.toASCIIString());
				osw.close();
				fos = null;
			} catch (IOException e) {
				Logger.error(this, "Failed to write new pubkey", e);
			} finally {
				Closer.close(fos);
			}
//<<<<<<< HEAD
//			try {
//				fis = new FileInputStream(new File(SpiderIndexUploader.EDITION_FILENAME));
//				BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
//				try {
//					edition = Long.parseLong(br.readLine());
//				} catch (NumberFormatException e) {
//					edition = 0;
//				}
//				Logger.debug(this, "Edition: "+edition);
//				fis.close();
//				fis = null;
//			} catch (IOException e) {
//				// Ignore
//				edition = 0;
//			} finally {
//				Closer.close(fis);
//			}
//=======
//>>>>>>> debbiedub/fcp-uploader
		}
		return privURI;
	}

	synchronized FreenetURI getPrivateUSK() {
		return loadSSKURIs().setKeyType("USK").setDocName(SpiderIndexUploader.INDEX_DOCNAME).setSuggestedEdition(getLastUploadedEdition());
	}

	/** Will return edition -1 if no successful uploads so far, otherwise the correct edition. */
	synchronized FreenetURI getPublicUSK() {
		loadSSKURIs();
		return pubURI.setKeyType("USK").setDocName(SpiderIndexUploader.INDEX_DOCNAME).setSuggestedEdition(getLastUploadedEdition());
	}

	private synchronized long getLastUploadedEdition() {
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(new File(SpiderIndexUploader.EDITION_FILENAME));
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
			try {
				edition = Long.parseLong(br.readLine());
			} catch (NumberFormatException e) {
				Logger.error(this, "Failed to parse edition", e);
			}
			fis.close();
			fis = null;
		} catch (IOException e) {
			Logger.error(this, "Failed to read edition", e);
		} finally {
			Closer.close(fis);
		}
		return edition;
	}

}