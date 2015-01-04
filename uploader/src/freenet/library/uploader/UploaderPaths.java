package freenet.library.uploader;

public class UploaderPaths {
	static final int MAX_HANDLING_COUNT = 5; 
	// When pushing is broken, allow max handling to reach this level before stalling forever to prevent running out of disk space.
	

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
	static final String DISK_DIR_PREFIX = "library-temp-index-";
	
	static final String LAST_URL_FILENAME = "library.index.lastpushed.chk";
	static final String PRIV_URI_FILENAME = "library.index.privkey";
	static final String PUB_URI_FILENAME = "library.index.pubkey";
	static final String EDITION_FILENAME = "library.index.next-edition";
	
	static final String LAST_DISK_FILENAME = "library.index.lastpushed.disk";
	
	static final String BASE_FILENAME_PUSH_DATA = "library.index.data.";
	
	static final String LIBRARY_CACHE = "library-spider-pushed-data-cache";	
}
