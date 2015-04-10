package freenet.library.uploader;

import java.io.File;

class IndexPeeker {
	private File directory;
	private String center = null;
	
	IndexPeeker(File dir) {
		directory = dir;
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
		if (center == null) {
			System.out.println("Grouping around " + subj);
			center = subj;
		}
		if (center.substring(0, 1).equals(subj.substring(0, 1))) {
			return true;
		}
		return false;
	}
}
