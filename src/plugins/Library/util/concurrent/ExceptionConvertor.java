package plugins.Library.util.concurrent;

public interface ExceptionConvertor<X extends Exception> {
	
	X convert(RuntimeException e);
}
