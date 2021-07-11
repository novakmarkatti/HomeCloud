package FileTransfer;

import javafx.collections.ObservableList;

/* A  FileTransfer-UI valtoztatasa szerver es kliens oldalrol */
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
    void setListViewToClient( ObservableList elements );

}
