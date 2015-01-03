package freenet.library;

public class FactoryRegister {
	private static ArchiverFactory archiver = null;
	
	public static void register(ArchiverFactory factory) {
		archiver = factory;
	}

	public static ArchiverFactory getArchiverFactory() {
		assert archiver != null;
		return archiver;
	}
}
