package freenet.library.uploader;

class IndexPeeker {

	class Section {

		private String center;

		public Section(String string) {
			center = string;
		}

		boolean contains(String subj) {
			if (subj.substring(0, 1).equals(center.substring(0, 1)))
				return true;
			return false;
		}

	}

	boolean onTop(String subj) {
		// TODO Auto-generated method stub
		return false;
	}

	Section getSectionFor(String string) {
		System.out.println("Grouping around " + string);
		return new Section(string);
	}

}
