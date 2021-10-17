package freenet.library.uploader;

import junit.framework.TestCase;

public class MergerComparatorTest extends TestCase {

	public void testComparator() {
		assertTrue(Merger.comparator.compare("a", "b") < 0);
		assertTrue(Merger.comparator.compare("b", "a") > 0);
		assertTrue(Merger.comparator.compare("a", "a") == 0);
		
		assertTrue(Merger.comparator.compare("3", "5") < 0);
		assertTrue(Merger.comparator.compare("7", "5") > 0);
		assertTrue(Merger.comparator.compare("4", "4") == 0);

		assertTrue(Merger.comparator.compare("a", "ab") < 0);
		assertTrue(Merger.comparator.compare("ab", "a") > 0);
		
		assertTrue(Merger.comparator.compare("abc4", "abc00004") == 0);
		assertTrue(Merger.comparator.compare("abc4", "abc00005") < 0);
		assertTrue(Merger.comparator.compare("abc5", "abc00004") > 0);
		assertTrue(Merger.comparator.compare("abc00003", "abc4") < 0);
		assertTrue(Merger.comparator.compare("abc00004", "abc3") > 0);

		assertTrue(Merger.comparator.compare("abc4a", "abc00004a") == 0);
		assertTrue(Merger.comparator.compare("abc4a", "abc00004b") < 0);
		assertTrue(Merger.comparator.compare("abc4b", "abc00004a") > 0);
	}

}
