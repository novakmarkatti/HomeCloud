package WatchDirectory;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class WatchHandler {

    private WatchDir directoryWatcher = null;
    private Thread serverThread = null;
    private String filePath;
    private volatile boolean serverRunning = false;
    private WatchDirectoryUIsetter setter;
    private ServerSocket serverSocket;
    private final String address;

    WatchHandler(WatchDirectoryUIsetter setter, String path, String IPaddress){
        this.setter = setter;
        this.filePath = path;
        this.address = IPaddress;
    }

    /* Megtudjuk hogy fut e a WatchHandler. Mivel 2 reszbol tevodik ossze a futasa,
    * (onmaga es a WatchDir serverRunning-ja )emiatt ket ertek egyuttesevel terunk vissza*/
    public boolean isRunning() {
        return (serverRunning && directoryWatcher.isRunning() );
    }

    // A megadott utvonal getter metodusa
    public String getFilePath() {
        return filePath;
    }

    // A megadott utvonal setter metodusa
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /* Innentrol hivjuk meg a WatchDir FailedFiles metodusat ami vegul
    * tenylegesen leellenorzi a sikertelen fajlokat */
    public void WatchHandlerCheckFailedFiles(){
        if( directoryWatcher != null) {
            directoryWatcher.FailedFiles();
        }
    }

    /* WatchHandler elinditasa: ha mar letezik , akkor return -> hogy ne lehessen 2x elinditani.
     * Minden inditasnal ujj WatchDirt hozunk letre, aminek atadjuk Path-kent a megadott eleresi
     * utvonalat es az IP cimet, majd el is indijuk, vegul letrehozzuk a felelos Threadet */
    public void startWatchService() throws IOException {
        if( serverThread != null ) return;
        Path dir = Paths.get( this.filePath );
        directoryWatcher = new WatchDir(setter, dir, address);
        directoryWatcher.processEvents();
        serverRunning = true;
        createTCPServerThread();
    }

    /* WatchHandler megallitasa: ha nem letezik a WatchDir, akkor return -> hogy ne lehessen
     * megallitani mikor nem is fut, maskepp a WatchDirt allitjuk meg eloszor.
     * Mivel a ServerSocket accept metodusa blokkolo jellegu, emiatt ennek feloldasahoz szukseg van a
     * szerver lezarasahoz. */
    public void stopWatchService(){
        if (directoryWatcher == null) return;
        directoryWatcher.stopWatchDir();

        serverRunning = false;
        try{ serverSocket.close(); } catch (IOException e) { e.printStackTrace(); }
    }

    /* A serverThread szal felel a Watch a directory-ban az adott fajl beolvasasaert es
    * atkuldeseert. Elve a FileTransfer kliensehez hasonlo. Megnyitunk egy ServerSocket-et,
    * es varjuk hogy a masik eszkoz kliense csatlakozzon, majd ezzel letrehozzuk az in es output
    * csatornakat.A kapott uzenet 3 reszbol kell osszetevodjon: REQUEST:ID:filename. Ez jelzi, hogy
    * keri az adott eventben letrehozott/modositott fajlt. A tovabbiakban mar csak szimplan a
    * kapott nevu fajlt teljesen beolvassuk, majd valaszkent elkudljuk, utanna lezarjuk a kapcsolatot
    * a kliensel. Megszakitas soran lezarodik a szerver, majd az UI-ban feltuntetjuk.*/
    private void createTCPServerThread(){
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(4450);
                while (serverRunning){
                    Socket clientSocket = serverSocket.accept();
                    DataInputStream  dis = new DataInputStream(clientSocket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());

                    while (serverRunning){
                        if ( dis.available() > 0 ) { // ha tudunk olvasni
                            String query = dis.readUTF();
                            String[] parts = query.split(":");
                            System.out.println( "WatchHandler<Process-" + parts[1]+ "> Query received: \"" + query + "\"" );
                            if (parts.length == 3 && parts[0].equals("REQUEST") ) {
                                // Beolvassuk a fajlt a bytearray-ba
                                File file = new File( this.filePath + parts[2] );
                                if( file.exists() ) {
                                    byte[] bytearray = new byte[(int) file.length()];
                                    FileInputStream fis = new FileInputStream(file);
                                    DataInputStream dataIS = new DataInputStream(new BufferedInputStream(fis));
                                    dataIS.readFully(bytearray, 0, bytearray.length);
                                    dataIS.close();
                                    fis.close();
                                    // Elkuldjuk a fajlt
                                    System.out.println("WatchHandler<Process-" + parts[1] + "> Sending file: " + parts[2]);
                                    dos.writeInt(bytearray.length);
                                    dos.write(bytearray, 0, bytearray.length);
                                    dos.flush();
                                } else {
                                    dos.writeInt( -1 );
                                    dos.flush();
                                }
                            }
                            dos.close();
                            dis.close();
                            clientSocket.close();
                            break;
                        }
                    }
                }
            } catch (SocketException e) { System.err.println("WatchHandler> ServerSocket closed.");
            } catch (UnknownHostException e) { e.printStackTrace();
            } catch (IOException e) { e.printStackTrace(); }
            serverThread = null;
            setter.stopWatchHandlerUI();
        });
        serverThread.start();
    }

}
