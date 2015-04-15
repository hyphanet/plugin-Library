package freenet.library.uploader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.Map.Entry;

import freenet.library.index.ProtoIndex;
import freenet.library.index.ProtoIndexComponentSerialiser;
import freenet.library.index.ProtoIndexSerialiser;
import freenet.library.index.TermEntry;
import freenet.library.io.serial.LiveArchiver;
import freenet.library.io.serial.Serialiser.PushTask;
import freenet.library.util.SkeletonBTreeSet;
import freenet.library.util.exec.SimpleProgress;
import freenet.library.util.exec.TaskAbortException;

class DirectoryCreator {
	private ProtoIndex idxDisk;
	private ProtoIndexComponentSerialiser leafsrlDisk;
	private int countTerms;
	private ProtoIndexSerialiser srlDisk;
	private File newIndexDir;
	private String nextIndexDirName;

	DirectoryCreator(File directory) {
        int nextIndexDirNumber = 0;
        do {
        	nextIndexDirNumber ++;
        	nextIndexDirName = UploaderPaths.DISK_DIR_PREFIX + nextIndexDirNumber;
        	newIndexDir = new File(directory, nextIndexDirName);
        } while (newIndexDir.exists());
        System.out.println("Writing into directory " + nextIndexDirName);
        newIndexDir.mkdir();
        srlDisk = ProtoIndexSerialiser.forIndex(newIndexDir);
        LiveArchiver<Map<String,Object>, SimpleProgress> archiver = 
        		(LiveArchiver<Map<String,Object>, SimpleProgress>) srlDisk.getChildSerialiser();
        leafsrlDisk = ProtoIndexComponentSerialiser.get(ProtoIndexComponentSerialiser.FMT_FILE_LOCAL, archiver);
        idxDisk = new ProtoIndex("CHK@", "test", null, null, 0L);
        leafsrlDisk.setSerialiserFor(idxDisk);

		countTerms = 0;
	}

	private static boolean writeStringTo(File filename, String uri) {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(filename);
			OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
			osw.write(uri.toString());
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void putEntry(TermEntry tt) throws TaskAbortException {
		SkeletonBTreeSet<TermEntry> tree;
		if (idxDisk.ttab.containsKey(tt.subj)) {
			// merge
			tree = idxDisk.ttab.get(tt.subj);
		} else {
			tree = new SkeletonBTreeSet<TermEntry>(ProtoIndex.BTREE_NODE_MIN);
			leafsrlDisk.setSerialiserFor(tree);
		}
		tree.add(tt);
		// tree.deflate();
		// assert(tree.isBare());
		idxDisk.ttab.put(tt.subj, tree);
		countTerms++;
	}

	public void done() throws TaskAbortException {
		for (Entry<String, SkeletonBTreeSet<TermEntry>> entry : idxDisk.ttab.entrySet()) {
			SkeletonBTreeSet<TermEntry> tree = entry.getValue();
			tree.deflate();
			assert(tree.isBare());
			idxDisk.ttab.put(entry.getKey(), tree);
		}
		idxDisk.ttab.deflate();
		assert(idxDisk.ttab.isBare());
		PushTask<ProtoIndex> task4 = new PushTask<ProtoIndex>(idxDisk);
		srlDisk.push(task4);
		String uri = (String) task4.meta;
		writeStringTo(new File(newIndexDir, UploaderPaths.LAST_DISK_FILENAME), uri);
		System.out.println("Created new directory " + nextIndexDirName + 
				", file root at " + uri +
				" with " + countTerms + " terms.");
	}
}
