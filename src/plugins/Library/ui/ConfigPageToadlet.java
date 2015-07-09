/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.ui;

import plugins.Library.Library;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageNode;
import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.node.NodeClientCore;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;

/**
 * Encapsulates the ConfigPage in a Toadlet
 * @author Debora Wöpcke
 */
public class ConfigPageToadlet extends Toadlet {
    static final String PATH = "/config/library/";
	private NodeClientCore core;
    private final Library library;
    private final PluginRespirator pr;

    public ConfigPageToadlet(HighLevelSimpleClient client,
			     Library library,
			     NodeClientCore core,
			     PluginRespirator pr) {
	super(client);
	this.core = core;
	this.library = library;
	this.pr = pr;
    }

    @Override
    public String path() {
	return PATH;
    }

    public String menu() {
	return "FProxyToadlet.categoryBrowsing";
    }

    /** post commands */
    private static enum Commands {
	/** saves selected bookmarks */
	select,

	/** adds a new index to the bookmarks in Library */
	addbookmark,

	/** deletes a bookmark from the Library, requires an integer parameter between 0 and the number of bookmarks */
	removebookmark
    }


    /**
     * Class containing errors to be shown in the page.
     */
    private class ConfigPageError {
	String message;

	// Key error
	boolean keyError;
	String key;
	String uri;

	/**
	 * Constructor for key error.
	 */
	ConfigPageError(String m, String k, String u) {
	    keyError = true;
	    message = m;
	    key = k;
	    uri = u;
	}
    }

    /**
     * @param ctx
     */
    private void configForm(final ToadletContext ctx,
			    final ConfigPageError pageError) {
	PageNode p = ctx.getPageMaker().getPageNode("Configure indices (" +
						    Library.plugName +
						    ")",
						    ctx);
	p.headNode.addChild("link",
			    new String[]{"rel", "href", "type"},
			    new String[]{"stylesheet",
					 path() + "static/style.css",
					 "text/css"});

	HTMLNode pageNode = p.outer;
	HTMLNode contentNode = p.content;

	HTMLNode searchForm = pr.addFormChild(contentNode, path(), "searchform");
	MultiValueTable<String, String> headers = new MultiValueTable();
	HTMLNode indexeslist = searchForm.addChild("ul", "class",
						   "index-bookmark-list",
						   "Select indexes");
	for (String bm : library.bookmarkKeys()) {
	    HTMLNode bmItem = indexeslist.addChild("li");
	    bmItem.addChild("input",
			    new String[]{"name",
					 "type",
					 "value",
					 "title",
					 (library.selectedIndices.contains(bm) ?
					  "checked" :
					  "size" )
			    },
			    new String[]{"~"+bm,
					 "checkbox",
					 bm,
					 "Index uri : "+library.getBookmark(bm),
					 "1" },
			    bm);
	    bmItem.addChild("input",
			    new String[]{"name",
					 "type",
					 "value",
					 "title",
					 "class"
			    },
			    new String[]{Commands.removebookmark+bm,
					 "submit",
					 "X",
					 "Delete this bookmark",
					 "index-bookmark-delete"
			    });
	    String bookmark = library.getBookmark(bm);
	    if (bookmark != null) {
		try {
		    FreenetURI uri = new FreenetURI(bookmark);
		    if (uri.isUSK()) {
			bmItem.addChild("#", "(" + uri.getEdition() + ")");
		    }
		} catch (MalformedURLException e) {
		    // Don't add index.
		}
	    }
	}

	indexeslist.addChild("li").addChild("input",
			new String[]{"name",
				     "type",
				     "value",
				     "title",
				     "class"
			},
			new String[]{Commands.select.toString(),
				     "submit",
				     "Save",
				     "Save selected indices",
				     "index-bookmark-select"
			});


	HTMLNode bmItem = indexeslist.addChild("li");
	if (pageError != null && pageError.keyError) {
		bmItem.addChild("div",
				new String[]{"class"},
				new String[]{"index"},
				pageError.message);
	}

	bmItem.addChild("div",
			new String[]{"class"},
			new String[]{"index"},
			"Token:");
	
	String keyValue = "";
	if (pageError != null && pageError.key != null) {
		keyValue = pageError.key;
	}
	bmItem.addChild("input",
			new String[]{"name",
				     "type",
				     "class",
				     "title",
				     "value",
				     "size",
				     "maxsize"
			},
			new String[]{"addindexname",
				     "text",
				     "index",
				     "Token of the index",
				     keyValue,
				     "32",
				     "32"
			});
	String uriValue = "";
	if (pageError != null && pageError.uri != null) {
		uriValue = pageError.uri;
	}
	bmItem.addChild("div",
			new String[]{"class"},
			new String[]{"index"},
			"Key:");
	bmItem.addChild("input",
			new String[]{"name",
				     "type",
				     "class",
				     "title",
				     "value",
				     "size",
				     "maxsize"
			},
			new String[]{"addindexuri",
				     "text",
				     "index",
				     "Key of the index",
				     uriValue,
				     "100",
				     "256"
			});
	bmItem.addChild("input",
			new String[]{"name",
				     "type",
				     "value",
				     "title",
				     "class"
			},
			new String[]{Commands.addbookmark.toString(),
				     "submit",
				     "Add",
				     "Create this index",
				     "index-bookmark-add"
			});

	// write reply
	try {
	    writeHTMLReply(ctx, 200, "OK", headers, pageNode.generate());
	} catch (ToadletContextClosedException e) {
	    throw new RuntimeException(e);
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}
    }

    @Override
    public void handleMethodGET(URI uri,
				final HTTPRequest request,
				final ToadletContext ctx) {
	configForm(ctx, null);
    }

    public void handleMethodPOST(URI uri,
				 HTTPRequest request,
				 final ToadletContext ctx)
	throws ToadletContextClosedException, IOException, RedirectException {

	boolean hasFormPassword = ctx.hasFormPassword(request);
	boolean userAccess = ctx.isAllowedFullAccess();

	PageNode p = ctx.getPageMaker().getPageNode(Library.plugName, ctx);
	HTMLNode pageNode = p.outer;
	MultiValueTable<String, String> headers = new MultiValueTable();
	boolean locationIsSet = false;

	if(userAccess && hasFormPassword) {
	    for (String bm : library.bookmarkKeys()) {
		if (request.isPartSet("~" + bm)) {
		    library.selectedIndices.add(bm);
		} else {
		    library.selectedIndices.remove(bm);
		}
	    }

	    if (request.isPartSet(Commands.select.toString())) {
		headers.put("Location", MainPage.path());
		locationIsSet = true;
	    }

	    for (String bm : library.bookmarkKeys()) {
		if (request.isPartSet(Commands.removebookmark + bm)) {
		    library.removeBookmark(bm);
		    break;
		}
	    }

	    if (request.isPartSet(Commands.addbookmark.toString())) {
		String addindexname = request.getPartAsStringFailsafe("addindexname", 32).trim();
		String addindexuri = request.getPartAsStringFailsafe("addindexuri", 256).trim();
		if (addindexname.length() == 0) {
		    configForm(ctx,
			       new ConfigPageError("Incorrect Token, too short",
						   addindexname,
						   addindexuri));
		    return;
		}

		if (library.addBookmark(addindexname, addindexuri) == null) {
		    configForm(ctx, new ConfigPageError("Incorrect URI.",
							addindexname,
							addindexuri));
		    return;
		}
	    }
 	}

	if (!locationIsSet) {
	    headers.put("Location", path());
	}
	writeHTMLReply(ctx, 303, "See complete list", headers, pageNode.generate());
    }
}
