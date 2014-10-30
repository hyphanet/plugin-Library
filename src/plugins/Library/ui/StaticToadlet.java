/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */


package plugins.Library.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.URI;
import java.net.URL;

import java.util.Date;

import freenet.client.DefaultMIMETypes;
import freenet.client.HighLevelSimpleClient;

import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;

import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;

import plugins.Library.Library;

/**
 * Encapsulates a WebPage in a Toadlet
 * @author MikeB
 */
public class StaticToadlet extends Toadlet {
    public StaticToadlet(HighLevelSimpleClient client) {
        super(client);
    }

    /**
     * Get the path to this page
     */
    @Override
    public String path() {
        return ROOT_URL;
    }

    public static final String ROOT_URL = "/library/static/";
    public static final String ROOT_PATH = "staticfiles/";

    public void handleMethodGET(URI uri, final HTTPRequest httprequest, final ToadletContext ctx)
            throws ToadletContextClosedException, IOException, RedirectException {
        String path = uri.getPath();

        if ( !path.startsWith(ROOT_URL)) {
            return;  // doh!
        }

        path = path.substring(ROOT_URL.length());

        ClassLoader origClassLoader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(Library.class.getClassLoader());

        try {
            InputStream strm = getClass().getResourceAsStream(ROOT_PATH + path);

            if (strm == null) {
                this.sendErrorPage(ctx, 404, "Not found",
                                   "Could not find " +
                                   httprequest.getPath().substring(path().length()) + " in " +
                                   path());

                return;
            }

            Bucket data = ctx.getBucketFactory().makeBucket(strm.available());
            OutputStream os = data.getOutputStream();
            byte[] cbuf = new byte[4096];

            while (true) {
                int r = strm.read(cbuf);

                if (r == -1) {
                    break;
                }

                os.write(cbuf, 0, r);
            }

            strm.close();
            os.close();
            ctx.sendReplyHeaders(200, "OK", null, DefaultMIMETypes.guessMIMEType(path, false),
                                 data.size());
            ctx.writeData(data);
        } finally {
            Thread.currentThread().setContextClassLoader(origClassLoader);
        }
    }
}
