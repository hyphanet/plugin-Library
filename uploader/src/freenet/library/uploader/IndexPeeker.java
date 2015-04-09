package freenet.library.uploader;

class IndexPeeker {

	class Section {

		boolean contains(String subj) {
			if (subj.substring(0, 1).equals("delta".substring(0, 1)))
				return true;
			return false;
		}

	}

	boolean onTop(String subj) {
		// TODO Auto-generated method stub
		return false;
	}

	Section getSectionFor(String string) {
		return new Section();
	}

}
