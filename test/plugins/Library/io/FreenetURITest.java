package plugins.Library.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Test;

import static org.junit.Assert.*;

public class FreenetURITest {

	@Test
	public void getUSKRootTest() throws MalformedURLException {
		FreenetURI to = new FreenetURI("USK@aa,bb,Acc/file/12345/meta");
		assertEquals("USK@aa,bb,Acc/file", to.getRoot());
	}

	@Test
	public void toYamlTest() throws IOException {
		FreenetURI freenetURI = new FreenetURI("USK@aa,bb,Acc/file/12345/meta");
		YamlReaderWriter yamlReaderWriter = new YamlReaderWriter();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		yamlReaderWriter.writeObject(freenetURI, outputStream);
		assertEquals("!FreenetURI 'USK@aa,bb,Acc/file/12345/meta'" + System.lineSeparator(), outputStream.toString());
	}
}
