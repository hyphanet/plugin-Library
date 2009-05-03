package plugins.XMLLibrarian;

import freenet.client.events.ClientEventListener;
import freenet.client.events.ClientEvent;
import freenet.client.async.ClientContext;
import freenet.node.RequestClient;
import com.db4o.ObjectContainer;
import freenet.client.HighLevelSimpleClient;
import freenet.client.FetchContext;
import freenet.support.MultiValueTable;


/**
 * Stores and provides access to status of searches
 * @author MikeB
 */
public class Progress implements ClientEventListener, RequestClient
{
    private final String search;
    private FetchContext fcontext;
    private boolean retrieved;
    private boolean complete;
    private String msg;
    private String result;
    private String eventDescription;

    public Progress(String search, String initialmsg, HighLevelSimpleClient hlsc){
        this.search = search;
        retrieved = false;
        complete = false;
        msg = initialmsg;
        
        fcontext = hlsc.getFetchContext();
        fcontext.eventProducer.addEventListener(this);
    }
    
    public boolean persistent() {
        return false;
    }

    public void removeFrom(ObjectContainer container) {
        throw new UnsupportedOperationException();
    }

    
    public FetchContext getFetchContext(){
        return fcontext;
    }

    public void set(String _msg){
        msg = _msg;
        retrieved = false;
    }

    public void done(String msg){
        done(msg, null);
    }
    public void done(String msg, String result){
        retrieved = false;
        complete = true;
        this.msg = msg;
        this.result = (result==null) ? "" : result;
    }
    
    public boolean isdone(){
        return complete;
    }

    // TODO better status format
    public String get(String format){
        // probably best to do this with a lock
        // look for a progress update
        while(retrieved && !complete)   // whilst theres no new msg
            try{
                Thread.sleep(500);
            }catch(java.lang.InterruptedException e){

            }
        retrieved = true;
        
        if(format.equals("html")){
            if(complete){
                return "<html><body>"+msg+" - <a href=\"/plugins/plugins.XMLLibrarian.XMLLibrarian?search="+search+"\" target=\"_parent\">Click to reload &amp; view results</a><br />"+eventDescription+"</body></html>";
            }else
                return "<html><head><meta http-equiv=\"refresh\" content=\"1\" /></head><body>"+msg+"<br />"+eventDescription+"</body></html>";
        }else{
            if(complete)
                return msg+"<br />"+eventDescription;
            else
                return "."+msg+"<br />"+eventDescription;
        }
    }

    public String getresult(){
        while(!complete)   // whilst theres no results
            try{
                Thread.sleep(500);
            }catch(java.lang.InterruptedException e){

            }
        return result;
    }


    /**
     * Hears an event.
     * @param container The database context the event was generated in.
     * NOTE THAT IT MAY NOT HAVE BEEN GENERATED IN A DATABASE CONTEXT AT ALL:
     * In this case, container will be null, and you should use context to schedule a DBJob.
     **/
    public void receive(ClientEvent ce, ObjectContainer maybeContainer, ClientContext context){
        if(eventDescription != ce.getDescription()){
            eventDescription = ce.getDescription();
            retrieved = false;
        }
    }

    /**
     * Called when the EventProducer gets removeFrom(ObjectContainer).
     * If the listener is the main listener which probably called removeFrom(), it should do nothing.
     * If it's a tag-along but request specific listener, it may need to remove itself.
     */
	public void onRemoveEventProducer(ObjectContainer container){

    }



}
