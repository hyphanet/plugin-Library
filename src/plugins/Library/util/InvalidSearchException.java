package plugins.Library.util;



/**
 * Exception when something is wrong with search parameters but nothing else
 * @author MikeB
 */
public class InvalidSearchException extends Exception{
    public InvalidSearchException(String s){
		super (s);
	}

	public InvalidSearchException(String string, Exception ex) {
		super(string, ex);
	}
}
