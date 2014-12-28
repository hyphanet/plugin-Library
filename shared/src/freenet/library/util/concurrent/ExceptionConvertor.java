package freenet.library.util.concurrent;

public interface ExceptionConvertor<X extends Exception> {
	
	public X convert(RuntimeException e);

}
