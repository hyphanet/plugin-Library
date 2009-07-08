package plugins.XMLLibrarian;



/**
 * Exception when something is wrong with search parameters but nothing else
 * @author MikeB
 */
public class InvalidSearchException extends Exception{
    public InvalidSearchException(String s){
		super (s);
	}

	InvalidSearchException(String string, Exception ex) {
		super(string, ex);
	}
}
