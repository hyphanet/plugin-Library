package plugins.XMLLibrarian;

import freenet.client.events.ClientEventListener;
import freenet.client.events.ClientEvent;
import freenet.client.async.ClientContext;
import com.db4o.ObjectContainer;


/**
 * Stores and provides access to status of searches
 * @author MikeB
 */
public class Progress implements ClientEventListener
{
    private boolean retrieved;
    private boolean complete;
    private String msg;
    private String result;
    private String eventDescription;

    public Progress(String initialmsg){
        retrieved = false;
        complete = false;
        msg = initialmsg;
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

    // TODO better status format
    public String get(boolean pushonupdate){
        // probably best to do this with a lock
        if(pushonupdate)   // look for a progress update
            while(retrieved)   // whilst theres no new msg
                try{
                    Thread.sleep(500);
                }catch(java.lang.InterruptedException e){

                }
        retrieved = true;
        if(complete)
            return msg+"<br />"+eventDescription;
        else
            return "."+msg+"<br />"+eventDescription;
    }

    public String getresult(){
        return result;
    }


    /**
     * Hears an event.
     * @param container The database context the event was generated in.
     * NOTE THAT IT MAY NOT HAVE BEEN GENERATED IN A DATABASE CONTEXT AT ALL:
     * In this case, container will be null, and you should use context to schedule a DBJob.
     **/
    public void receive(ClientEvent ce, ObjectContainer maybeContainer, ClientContext context){
        eventDescription = ce.getDescription();
        retrieved = false;
    }

    /**
     * Called when the EventProducer gets removeFrom(ObjectContainer).
     * If the listener is the main listener which probably called removeFrom(), it should do nothing.
     * If it's a tag-along but request specific listener, it may need to remove itself.
     */
	public void onRemoveEventProducer(ObjectContainer container){

    }



}