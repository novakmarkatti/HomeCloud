package homecloudmobile.NetworkDiscovery ;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.ArrayList;

public class HomeCloudNetworking extends Activity {

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
    public HomeCloudNetworking( String serverName, String multicastAddress, int multicastPort, HomeCloudListener listener) {
        if (multicastAddress == null) throw new NullPointerException("address is null");
        if (listener == null) throw new NullPointerException("listener is null");
        this.serverName       = serverName;
        this.multicastAddress = multicastAddress;
        this.multicastPort    = multicastPort;
        this.listener         = listener;
        discoveryRunning = false;
        serverRunning = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WifiManager wifi  = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if(wifi  != null) {
            WifiManager.MulticastLock lock = wifi.createMulticastLock("Log_Tag");
            lock.acquire();
        }
    }


    // ------------------------------------------------------------------------
    // UDP Szerver (kuldes multicastPort+1, fogadas multicastPort+0 cimen)
    // ------------------------------------------------------------------------

    public boolean isServerRunning() {
        return serverRunning;
    }

    public synchronized void startServer(){
        if (advertisementThread != null) return;
        serverRunning = true;
        createUDPServerThread();
        listener.serverStarted();
    }

    public synchronized void stopServer(){
        if( advertisementThread == null ) return;
        serverRunning = false;
        try{ UDPServerSocket.close(); } catch(Exception e) {}
    }

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
                    Log.d("output","HomeCloudNetworking Server> Query received: \"" + query + "\" from client " + clientAddress );
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
            } catch (SocketException e) {
                Log.e("output","HomeCloudNetworking> UDPServerSocket closed.");
            } catch (IOException e){
                Log.e("output","ERROR: HomeCloudNetworking> createUDPServerThread\n" + e.toString());
            }
            advertisementThread = null;
            listener.serverStopped();
        });
        advertisementThread.start();
    }


    // ------------------------------------------------------------------------
    // UDP Kliens (kuldes multicastPort+0, fogadas multicastPort+1 cimen)
    // ------------------------------------------------------------------------

    public boolean isDiscoveryRunning(){
        return discoveryRunning;
    }

    public synchronized void stopDiscovery(){
        if (discoveryThread == null) return;
        discoveryRunning = false;
        try{ UDPClientSocket.close(); } catch(Exception e) {}
    }

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
                        Log.d("output","HomeCloudNetworking Client> Answer received: \"" + msg + "\"");
                        Log.d("output","HomeCloudNetworking Client> " + serverName + " discovered at " + receive_packet.getAddress().getHostAddress() + ":" + receive_packet.getPort() );

                        query = "QUERY:Done"; sbuf = query.getBytes();
                        UDPClientSocket.send( new DatagramPacket(sbuf, sbuf.length, group, multicastPort) );
                        listener.discoveredServer(receive_packet.getAddress().getHostAddress() );
                    }
                }
                UDPClientSocket.leaveGroup(group);
            } catch (SocketException e) {
                Log.e("output","HomeCloudNetworking> UDPClientSocket closed.");
            } catch (IOException e){
                Log.e("output","ERROR: HomeCloudNetworking> startDiscovery\n" + e.toString());
            }
            listener.discoveryStopped();
            discoveryThread = null;
        });
        discoveryThread.start();
    }

}
