package freenet.library.uploader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

import freenet.library.index.TermEntry;
import freenet.library.io.YamlReaderWriter;
import freenet.library.util.SkeletonBTreeMap;
import freenet.library.util.SkeletonBTreeSet;

class IndexPeeker {
	private File directory;
	private LinkedHashMap<String, Object> topTtab;
	private Set<String> topElements;
	private boolean selected = false;
	private String before;
	private String after;

	private static final SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> newtrees =
			new SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>>(12);

	IndexPeeker(File dir) {
		directory = dir;
		String lastCHK = DirectoryUploader.readStringFrom(new File(directory, UploaderPaths.LAST_URL_FILENAME));
		String rootFilename = directory + "/" + UploaderPaths.LIBRARY_CACHE + "/" + lastCHK;
		try {
			LinkedHashMap<String, Object> top = (LinkedHashMap<String, Object>) new YamlReaderWriter().readObject(new FileInputStream(new File(rootFilename)));
			LinkedHashMap<String, Object> ttab = (LinkedHashMap<String, Object>) top.get("ttab");
			topTtab = (LinkedHashMap<String, Object>) ttab.get("entries");
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		topElements = new HashSet<String>(topTtab.keySet());
	}

	private static int compare(String a, String b) {
		return SkeletonBTreeMap.compare(a, b, newtrees.comparator());
	}
	
	/**
	 * If the subj is to be included.
	 * 
	 * If subj is on top, include it.
	 * Let the first subj decide what part of the tree we match.
	 * Include subsequent terms if they are in the same part of the tree.
	 * 
	 * @param subj The term to include.
	 * @return true if the term is included.
	 */
	boolean include(String subj) {
		if (topElements.contains(subj)) {
			return true;
		}
		if (!selected) {
			System.out.println("Grouping around " + subj);
			String previous = null;
			String next = null;
			for (String iter : topTtab.keySet()) {
				next = iter;
				if (compare(subj, next) < 0) {
					break;
				}
				previous = iter;
				next = null;
			}
			before = previous;
			after = next;
			selected = true;
			topTtab = null;
		}
		if ((before == null || compare(before, subj) < 0) &&
				(after == null || compare(subj, after) < 0)) {
			return true;
		}
		return false;
	}
}
