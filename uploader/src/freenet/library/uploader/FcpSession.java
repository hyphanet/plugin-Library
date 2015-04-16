package freenet.library.uploader;

import java.io.IOException;
import java.net.UnknownHostException;

import net.pterodactylus.fcp.ClientHello;
import net.pterodactylus.fcp.CloseConnectionDuplicateClientName;
import net.pterodactylus.fcp.FcpAdapter;
import net.pterodactylus.fcp.FcpConnection;
import net.pterodactylus.fcp.FcpMessage;
import net.pterodactylus.fcp.NodeHello;

public class FcpSession {
	
	private FcpAdapter closeListener;
	private FcpConnection connection;
	private int exitStatus;

	public FcpSession() {
		exitStatus = 0;

		closeListener = new FcpAdapter() {
            public void connectionClosed(FcpConnection fcpConnection, Throwable throwable) {
                System.out.println("Connection Closed - Aborting.");
                System.exit(1);
            }
        };

        try {
            connection = new FcpConnection("127.0.0.1");
            connection.connect();
        } catch (UnknownHostException e) {
            System.err.println("Cannot connect to Node");
            exitStatus = 1;
            return;
        } catch (IOException e) {
            System.err.println("Cannot connect to Node");
            exitStatus = 1;
            return;
        }
        final String clientName = "SpiderMerger";
        final FcpMessage hello = new ClientHello(clientName);
        FcpAdapter helloListener = new FcpAdapter() {
                public void receivedNodeHello(FcpConnection c, NodeHello nh) {
                    synchronized (hello) {
                        hello.notify();
                    }
                }

                public void receivedCloseConnectionDuplicateClientName(FcpConnection fcpConnection, CloseConnectionDuplicateClientName closeConnectionDuplicateClientName) {
                    System.out.println("Another " + clientName + " connected - Aborting.");
                    System.exit(1);
                }
            };
        connection.addFcpListener(helloListener);

        connection.addFcpListener(closeListener);

        synchronized (hello) {
            try {
                connection.sendMessage(hello);
                hello.wait();
            } catch (InterruptedException e) {
                System.err.println("Waiting for connection interrupted.");
                exitStatus = 1;
                return;
            } catch (IOException e) {
                System.err.println("Hello cannot write.");
                exitStatus = 1;
                return;
            } finally {
            	connection.removeFcpListener(helloListener);
            }
        }
        helloListener = null;
        System.out.println("Connected");

	}
	
	public void close() {
		if (closeListener != null) {
			connection.removeFcpListener(closeListener);
		}
        if (connection != null) {
            connection.close();
        }
	}

	public FcpConnection getConnection() {
		return connection;
	}

	public int getStatus() {
		return exitStatus;
	}
}
