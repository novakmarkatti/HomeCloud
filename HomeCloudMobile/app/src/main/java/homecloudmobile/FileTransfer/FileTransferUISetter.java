package homecloudmobile.FileTransfer;

import java.util.ArrayList;

public interface FileTransferUISetter {

    // Server
    void serverStarted();
    void serverStopped();
    void clientConnected();
    void clientDisconnected();
    void setListViewToServer( String elem );

    // Client
    void clientStarted();
    void clientStopped();
    void connectedToServer();
    void setListViewToClient( ArrayList<String> elements );

}
