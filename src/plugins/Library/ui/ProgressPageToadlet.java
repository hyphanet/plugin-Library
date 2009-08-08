package plugins.Library.ui;


import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;
import java.io.IOException;
import java.net.URI;
import plugins.Library.Library;
import plugins.Library.index.Request.RequestState;
import plugins.Library.search.Search;
import plugins.Library.serial.TaskAbortException;



/**
 * Generates the main search page
 *
 * @author MikeB
 */
class ProgressPageToadlet extends Toadlet {
	private final Library library;
	private final PluginRespirator pr;

	private Search search = null;
	private boolean showold = false;
	

	ProgressPageToadlet(HighLevelSimpleClient client, Library library, PluginRespirator pr) {
		super(client);
		this.library = library;
		this.pr = pr;
	}

	@Override
	public void handleGet(URI uri, final HTTPRequest request, final ToadletContext ctx)
	throws ToadletContextClosedException, IOException, RedirectException {
		ClassLoader origClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(Library.class.getClassLoader());
		try {
			processGetRequest(request);
			// write reply
			writeReply(ctx, 200, "text/xml", "OK", null, progressxml());
		} finally {
			Thread.currentThread().setContextClassLoader(origClassLoader);
		}
	}

	/**
	 * Process a get request, the only parameters allowed for a get request are
	 * request id (for an ongoing request) and formatting parameters
	 */
	public void processGetRequest(HTTPRequest request){
		showold = request.isParameterSet("showold");

		if (request.isParameterSet("request")){
			search = Search.getSearch(request.getIntParam("request"));
		}
	}


	/**
	 * Return progress and results on a request in xml format for ajax
	 * @param searchquery
	 * @param indexuri
	 * @param showold
	 * @return
	 */
	String progressxml() {
		HTMLNode resp = new HTMLNode("pagecontent");
		HTMLNode progress;
		// If search is happening, return it's progress
		if(search!=null){
			progress = new HTMLNode("table", new String[]{"id", "class"}, new String[]{"progress-table", "progress-table"});
				progress.addChild(MainPage.progressBar(search));
		}else
			progress = new HTMLNode("#", "No search for this, something went wrong");
		// If it's finished, return it's results
		if(search != null && search.getState()==RequestState.FINISHED)
			try {
				ResultNodeGenerator nodeGenerator = new ResultNodeGenerator(search.getResult(), true);
				resp.addChild("result").addChild(nodeGenerator.generatePageEntryNode(showold, true));
				resp.addChild("progress", "RequestState", "FINISHED", "Search complete");
			} catch (TaskAbortException ex) {
				MainPage.addError(resp.addChild("error", "RequestState",  "ERROR"), ex.getCause());
			} catch (RuntimeException ex) {
				MainPage.addError(resp.addChild("error", "RequestState",  "ERROR"), ex);
			}
		else
			resp.addChild("progress", "RequestState",  (search==null)?"":search.getState().toString()).addChild(progress);
		return "<?xml version='1.0' encoding='ISO-8859-1'?>\n"+resp.generate();
	}

	
	public String path() {
		return "/library/xml";
	}
	
	public String supportedMethods() {
		return "GET";
	}
}
