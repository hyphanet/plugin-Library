package freenet.library.index;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import freenet.library.index.TermEntry.EntryType;
import freenet.library.io.FreenetURI;
import freenet.library.io.ObjectStreamReader;
import freenet.library.io.ObjectStreamWriter;
import freenet.library.io.serial.FileArchiver;
import freenet.library.io.serial.LiveArchiver;
import freenet.library.io.serial.Serialiser.PullTask;
import freenet.library.io.serial.Serialiser.PushTask;
import freenet.library.io.serial.Translator;
import freenet.library.ArchiverFactory;
import freenet.library.FactoryRegister;
import freenet.library.Priority;
import freenet.library.util.SkeletonBTreeMap;
import freenet.library.util.SkeletonBTreeSet;
import freenet.library.util.exec.SimpleProgress;
import freenet.library.util.exec.TaskAbortException;

import junit.framework.TestCase;

public class ProtoIndexSerialiserTest extends TestCase {
	private MockLiveArchiver mockLiveArchiver;
	private MockArchiverFactory mockArchiverFactory;
	private ProtoIndexSerialiser tested_object;
	private ProtoIndex mockProtoIndex;

    /**
     * For pull, the meta is a String containing the contents of the stream.
     */
	static class MockLiveArchiver implements LiveArchiver<Map<String, Object>, SimpleProgress> {
		ObjectStreamReader<Map<String, Object>> reader;
		ObjectStreamWriter<Map<String, Object>> writer;
		MockLiveArchiver(ObjectStreamReader<Map<String, Object>> r,
				ObjectStreamWriter<Map<String, Object>> w) {
			reader = r;
			writer = w;
		}
		
		byte[] bytesToParse;
		String createdOutput;
		int archiverResultNumber = 1;
		
		@Override
		public void pull(
				freenet.library.io.serial.Serialiser.PullTask<Map<String, Object>> task)
				throws TaskAbortException {
			assertNotNull(bytesToParse);
			InputStream is = new ByteArrayInputStream(bytesToParse);
			try {
				task.data = reader.readObject(is);
			} catch (IOException e) {
				throw new TaskAbortException("byte array unparseable", e);
			}
		}

		@Override
		public void push(
				freenet.library.io.serial.Serialiser.PushTask<Map<String, Object>> task)
				throws TaskAbortException {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			try {
				writer.writeObject(task.data, os);
			} catch (IOException e) {
				throw new TaskAbortException("Could not write", e);
			}
			createdOutput = os.toString();
		}

		@Override
		public void pullLive(
				freenet.library.io.serial.Serialiser.PullTask<Map<String, Object>> task,
				SimpleProgress p) throws TaskAbortException {
			fail("Not yet implemented"); // TODO
		}

		@Override
		public void pushLive(
				freenet.library.io.serial.Serialiser.PushTask<Map<String, Object>> task,
				SimpleProgress p) throws TaskAbortException {
        	if (p != null) {
        		p.addPartKnown(1, true);
        	}
			push(task);
			try {
				task.meta = new FreenetURI("CHK@" + (archiverResultNumber++) + ",7,A6");
			} catch (MalformedURLException e) {
				throw new TaskAbortException("URL problem", e);
			}
	        if (p != null) {
	        	p.addPartDone();
	        }
		}

		@Override
		public void waitForAsyncInserts() throws TaskAbortException {
			// Do nothing
		}
	}

	class MockArchiverFactory implements ArchiverFactory {

		@Override
		public <T, S extends ObjectStreamWriter & ObjectStreamReader> LiveArchiver<T, SimpleProgress> newArchiver(
				S rw, String mime, int size, Priority priorityLevel) {
			assertNotNull(rw);
			assertEquals(ProtoIndexComponentSerialiser.yamlrw, rw);
			assertNotNull(mime);
			assertNotSame(0, size);
			assertEquals(Priority.Bulk, priorityLevel);
			return (LiveArchiver<T, SimpleProgress>) new MockLiveArchiver(rw, rw);
		}

		@Override
		public <T, S extends ObjectStreamWriter & ObjectStreamReader> LiveArchiver<T, SimpleProgress> newArchiver(
				S rw, String mime, int size,
				LiveArchiver<T, SimpleProgress> archiver) {
			fail("Not called by the tests.");
			return null;
		}
	}

	protected void setUp() throws Exception {
		super.setUp();
		mockArchiverFactory = new MockArchiverFactory();
		FactoryRegister.register(mockArchiverFactory);
		mockLiveArchiver = new MockLiveArchiver(
				ProtoIndexComponentSerialiser.yamlrw,
				ProtoIndexComponentSerialiser.yamlrw);

		tested_object = new ProtoIndexSerialiser(mockLiveArchiver);
		
		FreenetURI reqID = null;
		mockProtoIndex = new ProtoIndex(reqID, "name", "owner", "email", 0);
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testProtoIndexSerialiser() {
		assertNotNull(tested_object);
	}

	private void assertProtoIndexSerialiserURI(ProtoIndexSerialiser result) {
		assertNotNull(result);
		assertNotNull(result.getChildSerialiser());
		assertTrue(result.getChildSerialiser() instanceof MockLiveArchiver);
	}

	public void testForIndexURIAsObjectPriority() throws MalformedURLException {
		FreenetURI uri = new FreenetURI("CHK@");
		Object obj = uri;
		Priority priority = Priority.Bulk;

		ProtoIndexSerialiser result = ProtoIndexSerialiser.forIndex(obj, priority);

		assertProtoIndexSerialiserURI(result);
	}

	private void assertProtoIndexSerialiserFile(ProtoIndexSerialiser result) {
		assertNotNull(result);
		assertNotNull(result.getChildSerialiser());
		assertTrue(result.getChildSerialiser() instanceof FileArchiver);
	}

	public void testForIndexFileAsObjectPriority() {
		File file = new File("file");
		Object obj = file;
		Priority priority = Priority.Bulk;

		ProtoIndexSerialiser result = ProtoIndexSerialiser.forIndex(obj, priority);

		assertProtoIndexSerialiserFile(result);
	}

	public void testForIndexUnmatchedObjectPriority() throws MalformedURLException {
		Object obj = new Object();
		Priority priority = Priority.Bulk;

		try {
			ProtoIndexSerialiser.forIndex(obj, priority);
			fail("Should have thrown.");
		} catch (UnsupportedOperationException e) {
			// OK.
		}
	}

	public void testForIndexFreenetURIPriority() throws MalformedURLException {
		FreenetURI uri = new FreenetURI("CHK@");
		Priority priority = Priority.Bulk;

		ProtoIndexSerialiser result = ProtoIndexSerialiser.forIndex(uri, priority);

		assertProtoIndexSerialiserURI(result);
	}

	public void testForIndexFile() {
		final File prefix = new File("prefix");
		ProtoIndexSerialiser result = ProtoIndexSerialiser.forIndex(prefix);

		assertProtoIndexSerialiserFile(result);
	}

	public void testGetChildSerialiser() {
		final LiveArchiver<Map<String, Object>, SimpleProgress> result = tested_object.getChildSerialiser();

		assertNotNull(result);
	}

	public void testGetTranslator() {
		final Translator<ProtoIndex, Map<String, Object>> translator = tested_object.getTranslator();
		assertNotNull(translator);
		
		translator.app(mockProtoIndex);
	}

	public void testPull() throws TaskAbortException, MalformedURLException {
		final FreenetURI req_id = new FreenetURI("CHK@");
		PullTask<ProtoIndex> task = new PullTask<ProtoIndex>(req_id);
		final String name = "New Spider index.";
		final long totalPages = 17;
		mockLiveArchiver.bytesToParse = (
				"serialVersionUID: " + ProtoIndex.serialVersionUID + "\n" +
				"serialFormatUID: " + ProtoIndexComponentSerialiser.FMT_DEFAULT	+ "\n" +
				"totalPages: " + totalPages + "\n" +
				"name: " + name + "\n" +
				"utab:\n" + 
				"  node_min: 1024\n" + 
				"  size: 0\n" + 
				"  entries: {}\n" +
				"ttab:\n" +
				"  node_min: 1024\n" + 
				"  size: 2470\n" + 
				"  entries:\n" +
				"    adam: !BinInfo {? &id001 !!binary \"abcdef==\" : 1}\n" +
				"  subnodes:\n" +
				"    !FreenetURI 'CHK@123,456,A789': 1234\n" +
				"    !FreenetURI 'CHK@456,678,A890': 1235\n" +
				"").getBytes();
		task.data = mockProtoIndex;

		tested_object.pull(task);

		assertEquals(req_id, task.data.reqID);
		assertEquals(name, task.data.name);
		assertEquals(totalPages, task.data.totalPages);
		assertEquals(new SkeletonBTreeMap<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>(1024),
				task.data.utab);
		SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> x = task.data.ttab;
	}

	public void testPushEmpty() throws TaskAbortException {
		PushTask<ProtoIndex> task = new PushTask<ProtoIndex>(mockProtoIndex);
		
		tested_object.push(task);

		assertTrue(mockLiveArchiver.createdOutput.contains("serialVersionUID: " + ProtoIndex.serialVersionUID));
		final String emptyBTree = "\n  node_min: 1024\n  size: 0\n  entries: {}\n";
		assertTrue(mockLiveArchiver.createdOutput.contains("\nutab:" + emptyBTree));
		assertTrue(mockLiveArchiver.createdOutput.contains("\nttab:" + emptyBTree));
	}

	public void testPushContents() throws TaskAbortException, MalformedURLException {
        ProtoIndexSerialiser srl = ProtoIndexSerialiser.forIndex(new FreenetURI("CHK@"), Priority.Bulk);
        LiveArchiver<Map<String,Object>,SimpleProgress> archiver = 
            (LiveArchiver<Map<String,Object>,SimpleProgress>)(srl.getChildSerialiser());
        ProtoIndexComponentSerialiser leafsrl = ProtoIndexComponentSerialiser.get(ProtoIndexComponentSerialiser.FMT_DEFAULT, archiver);

		PushTask<ProtoIndex> task = new PushTask<ProtoIndex>(mockProtoIndex);

		final int ENTRIES = 10000;
		for (int i = 0; i < ENTRIES; i++) {
			final SkeletonBTreeSet<TermEntry> value = new SkeletonBTreeSet<TermEntry>(100);
			value.add(new TermPageEntry("a", 1, new FreenetURI("CHK@1,2,A3"), "title", null));
	        leafsrl.setSerialiserFor(value);
			value.deflate();
	
			mockProtoIndex.ttab.put("a" + i, value);
		}

		leafsrl.setSerialiserFor(mockProtoIndex);
        mockProtoIndex.ttab.deflate();

        mockLiveArchiver.waitForAsyncInserts();
        
        tested_object.push(task);

		assertTrue(mockLiveArchiver.createdOutput.contains("serialVersionUID: " + ProtoIndex.serialVersionUID));
		final String emptyBTree = "\n  node_min: 1024\n  size: 0\n  entries: {}\n";
		assertTrue(mockLiveArchiver.createdOutput.contains("\nutab:" + emptyBTree));
		final String countBTreeProlog = "\n  node_min: 1024\n  size: " + ENTRIES + "\n  entries:\n";
		assertTrue(mockLiveArchiver.createdOutput.contains("\nttab:" + countBTreeProlog));
	}
}
