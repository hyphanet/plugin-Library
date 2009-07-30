package plugins.Library.ui;

import freenet.support.HTMLNode;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

/**
 * Interface for all web pages
 * 
 * @author j16sdiz (1024D/75494252)
 */
interface WebPage {

	public WebPage clone();

	public String name();

	public String path();

	public void processGetRequest(HTTPRequest request);

	public abstract void processPostRequest(HTTPRequest request, HTMLNode contentNode, boolean userRequest);

	public String supportedMethods();

	public abstract void writeContent(HTMLNode contentNode, MultiValueTable<String, String> headers);

}