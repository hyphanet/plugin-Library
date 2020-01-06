package plugins.Library.io;

import java.net.MalformedURLException;

import junit.framework.TestCase;

public class FreenetURITest extends TestCase {
	public void testGetUSKRoot() throws MalformedURLException {
		FreenetURI to = new FreenetURI("USK@aa,bb,Acc/file/12345/meta");
		
		assertEquals("USK@aa,bb,Acc/file", to.getRoot());
	}
}
