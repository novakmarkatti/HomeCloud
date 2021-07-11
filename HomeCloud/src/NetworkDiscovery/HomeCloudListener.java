package NetworkDiscovery;

/* A  NetworkDiscovery -UI valtoztatasa szerver es kliens oldalrol */
public interface HomeCloudListener {

    // Client
    void discoveryStarted();
    void discoveryStopped();
    void discoveredServer(String address);

    // Server
    void serverStarted();
    void serverStopped();
    void clientConnected();
    void connectionEstablished( String address );
}