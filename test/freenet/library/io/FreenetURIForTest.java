package freenet.library.io;

import java.net.MalformedURLException;
import java.util.Random;

public class FreenetURIForTest extends FreenetURI {

	public FreenetURIForTest(String uri) throws MalformedURLException {
		super(uri);
		throw new RuntimeException("Cannot create for test.");
	}

	public static FreenetURI generateRandomCHK(Random rand) {
		throw new RuntimeException("Not implemented yet.");
	}
}
