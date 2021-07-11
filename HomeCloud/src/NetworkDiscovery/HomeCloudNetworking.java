package NetworkDiscovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.ArrayList;

public class HomeCloudNetworking {

    private final String multicastAddress;
    private final int    multicastPort;
    private final String serverName;
    private HomeCloudListener listener;
    private boolean discoveryRunning, serverRunning;
    private Thread advertisementThread = null;
    private ArrayList<String> clientsConnected;
    private Thread discoveryThread = null;
    private MulticastSocket UDPServerSocket;
    private MulticastSocket UDPClientSocket;

    // UDP Multicast cimek: 224.0.0.0 - 239.255.255.255
    public HomeCloudNetworking( String serverName, String multicastAddress, int multicastPort, HomeCloudListener listener) throws Exception {
        if (multicastAddress == null) throw new NullPointerException("address is null");
        if (listener == null) throw new NullPointerException("listener is null");
        this.serverName       = serverName;
        this.multicastAddress = multicastAddress;
        this.multicastPort    = multicastPort;
        this.listener         = listener;
        discoveryRunning = false;
        serverRunning = false;
    }

    // ------------------------------------------------------------------------
    // UDP Szerver (kuldes multicastPort+1, fogadas multicastPort+0 cimen)
    // ------------------------------------------------------------------------

    // Megmutatja , hogy a szerver fut-e
    public boolean isServerRunning() {
        return serverRunning;
    }

    /* UDP Szerver elinditasa: ha mar letezik , akkor return -> hogy ne lehessen 2x elinditani.
     * Letrehozzuk a felelos Threadet , UI-ban feltuntetjuk hogy elindult */
    public synchronized void startServer(){
        if (advertisementThread != null) return;
        serverRunning = true;
        createUDPServerThread();
        listener.serverStarted();
    }

    /* UDP Szerver megallitasa: ha nem letezik , akkor return -> hogy ne lehessen megallitani mikor nem is fut
     * Mivel a MulticastSocket receive metodusa blokkolo jellegu, emiatt ennek feloldasahoz szukseg van a
     * szerver lezarasahoz. */
    public synchronized void stopServer(){
        if( advertisementThread == null ) return;
        serverRunning = false;
        try{ UDPServerSocket.close(); } catch(Exception e) {}
    }

    /* A szerver oldali kommunikacioert felel. (1) Eloszor letrehozzuk az UDP szervert a multicastPort-on,
    * majd belepunk az adott IP cimu csoportba, letrehozzuk az in-output csatornakat, illetve egy listat,
    * amiben a csatlakozott klienseket fogjuk szamon tartani. (2) Figyelunk, hogy jott e uzenet. Ha igen,
    * akkor megjelenitjuk a UI-ban hogy csatlakozott kliens. Elso sorban megvizsgaljuk, hogy a kapott
    * uzenet QUERY:serverName tipusu. Ha igen, akkor valaszolunk, es amennyiben a csatlakozott kliensek
    * kozott nem szerepel a jelenlegi kliens, elmentjuk a kliens ip cimet es port szamat. Erre azert
    * van szukseg mert a kapcsolatot csak akkor nyilvanitjuk kialakitottnak ha a csatlakozott kliensek
    * kozott szerepel az adott kliens, es a valasz uzenetunkre egy QUERY:Done tipusu uzenetet kapunk.
    * Ekkor a kapcsolat letrejott, immar tudjuk a masik fel IP cimet, igy megallithatjuk a NetworkDiscoveryt*/
    private void createUDPServerThread(){
        advertisementThread = new Thread(() -> {
            try {
                // (1) inicializalas
                UDPServerSocket = new MulticastSocket(multicastPort);
                InetAddress group = InetAddress.getByName(multicastAddress);
                UDPServerSocket.joinGroup(group);
                byte[] rbuf = new byte[1024];
                byte[] sbuf = serverName.getBytes();
                DatagramPacket receive_packet = new DatagramPacket(rbuf, rbuf.length);
                DatagramPacket send_packet    = new DatagramPacket(sbuf, sbuf.length, group, multicastPort + 1);
                clientsConnected = new ArrayList<>();

                // (2) kommunikacio
                while (serverRunning){
                    UDPServerSocket.receive(receive_packet);
                    listener.clientConnected();
                    String query = new String(receive_packet.getData(), 0, receive_packet.getLength());
                    String clientAddress = receive_packet.getAddress().getHostAddress() + ":" + receive_packet.getPort();
                    System.out.println("HomeCloudNetworking Server> Query received: \"" + query + "\" from client " + clientAddress );
                    String[] parts = query.split(":");
                    if (parts.length == 2 && parts[0].equals("QUERY") && parts[1].equals(serverName) ) {
                        UDPServerSocket.send(send_packet);
                        if( !clientsConnected.contains(clientAddress) ) clientsConnected.add(clientAddress);
                    } else if (parts.length == 2 && parts[0].equals("QUERY") && parts[1].equals("Done") && clientsConnected.contains(clientAddress) ) {
                        listener.connectionEstablished( receive_packet.getAddress().getHostAddress() );
                        stopServer();
                    }
                }
                UDPServerSocket.leaveGroup(group);
            } catch (SocketException e) { System.err.println("HomeCloudNetworking> UDPServerSocket closed.");
            } catch (IOException e){
                System.err.println("ERROR: HomeCloudNetworking> createUDPServerThread");
                e.printStackTrace();
            }
            advertisementThread = null;
            listener.serverStopped();
        });
        advertisementThread.start();
    }


    // ------------------------------------------------------------------------
    // UDP Kliens (kuldes multicastPort+0, fogadas multicastPort+1 cimen)
    // ------------------------------------------------------------------------

    // Megmutatja , hogy a kliens fut-e
    public boolean isDiscoveryRunning(){
        return discoveryRunning;
    }

    /* UDP kliens megallitasa: ha nem letezik , akkor return -> hogy ne lehessen megallitani mikor nem is fut
     * Mivel a MulticastSocket receive metodusa blokkolo jellegu, emiatt ennek feloldasahoz szukseg van a
     * kliens lezarasahoz. */
    public synchronized void stopDiscovery(){
        if (discoveryThread == null) return;
        discoveryRunning = false;
        try{ UDPClientSocket.close(); } catch(Exception e) {}
    }

    /* A kliens oldali kommunikacioert felel. Ha mar letezik , akkor return -> hogy ne lehessen 2x elinditani
     * es letrehozzuk a felelos Threadet. (1) Kliens oldalon is letrehozzunk egy UDP szervert a multicastPort+1-en.
     * Azert van erre szukseg, mert igy szerver oldalon a multicastPort+0-an, mig kliens oldalon a
     * multicastPort+1 -et tudjuk figyelni, hogy jott - e uzenet vagy nem. Aztan belepunk az adott IP cimu
     * csoportba, letrehozzuk az in-output csatornakat. (2) UI-ban megjelenitju hogy a discovery elindult,
     * es szetkuldunk egy QUERY:serverName tipusu uzenetet a csoportban. A szerver ezt meg kell kapja,
     * igy a kovetkezo lepesben varjuk a szerver valaszat. Amennyiben megjott a valasz, kuldunk egy
     * "QUERY:Done" uzenetet, tudatva ezzel a szerverrel hogy a NetworkDiscovery teljesitette celjat,
     * es elmentjuk az IP-cimet.
     * akkor megjelenitjuk a UI-ban hogy csatlakozott kliens. Elso sorban megvizsgaljuk, hogy a kapott
     * uzenet QUERY:serverName tipusu. Ha igen, akkor valaszolunk, es amennyiben a csatlakozott kliensek
     * kozott nem szerepel a jelenlegi kliens, elmentjuk a kliens ip cimet es port szamat. Erre azert
     * van szukseg mert a kapcsolatot csak akkor nyilvanitjuk kialakitottnak ha a csatlakozott kliensek
     * kozott szerepel az adott kliens, es a valasz uzenetunkre egy QUERY:Done tipusu uzenetet kapunk.
     * Ekkor a kapcsolat letrejott, immar tudjuk a masik fel IP cimet amit le is mentunk, igy
     * megallithatjuk a NetworkDiscoveryt. Leallast UI-ban feltuntetjuk. */
    public synchronized void startDiscovery(){
        if (discoveryThread != null) return;
        discoveryRunning = true;
        discoveryThread = new Thread(() -> {
            try{
                // (1) inicializalas
                UDPClientSocket = new MulticastSocket(multicastPort + 1);
                InetAddress group = InetAddress.getByName(multicastAddress);
                UDPClientSocket.joinGroup(group);
                String query = "QUERY:" + serverName;
                byte[] rbuf = new byte[1024];
                byte[] sbuf = query.getBytes();
                DatagramPacket receive_packet = new DatagramPacket(rbuf, rbuf.length);
                DatagramPacket send_packet    = new DatagramPacket(sbuf, sbuf.length, group, multicastPort);

                // (2) kommunikacio
                listener.discoveryStarted();
                while (discoveryRunning ) {
                    UDPClientSocket.send(send_packet);
                    while (discoveryRunning){
                        UDPClientSocket.receive(receive_packet);
                        String msg = new String(receive_packet.getData(), 0, receive_packet.getLength());
                        System.out.println("HomeCloudNetworking Client> Answer received: \"" + msg + "\"");
                        System.out.println("HomeCloudNetworking Client> " + serverName + " discovered at " + receive_packet.getAddress().getHostAddress() + ":" + receive_packet.getPort() );

                        query = "QUERY:Done"; sbuf = query.getBytes();
                        UDPClientSocket.send( new DatagramPacket(sbuf, sbuf.length, group, multicastPort) );
                        listener.discoveredServer(receive_packet.getAddress().getHostAddress() );
                    }
                }
                UDPClientSocket.leaveGroup(group);
            } catch (SocketException e) { System.err.println("HomeCloudNetworking> UDPClientSocket closed.");
            } catch (IOException e){
                System.err.println("ERROR: HomeCloudNetworking> startDiscovery");
                e.printStackTrace();
            }
            listener.discoveryStopped();
            discoveryThread = null;
        });
        discoveryThread.start();
    }

}
