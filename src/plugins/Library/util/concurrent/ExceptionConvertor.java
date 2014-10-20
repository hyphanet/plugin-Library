package plugins.Library.util.concurrent;

public interface ExceptionConvertor<X extends Exception> {
    public X convert(RuntimeException e);
}
