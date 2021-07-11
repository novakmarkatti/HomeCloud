package WatchDirectory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;

public class WatchListener {

    private String targetPath;
    private final String address;
    private Thread EventClientThread = null;
    private Thread serverThread = null;
    private Thread clientThread = null;
    private volatile boolean serverRunning = false;
    private WatchDirectoryUIsetter setter;
    private ServerSocket serverSocket;

    WatchListener(WatchDirectoryUIsetter setter, String targetPath, String IPaddress){
        this.setter = setter;
        this.targetPath = targetPath;
        this.address = IPaddress;
    }

    /* visszater azzal az ertekkel hogy fut e a WatchListener. */
    public boolean isServerRunning() {
        return serverRunning;
    }

    // A megadott utvonal getter metodusa
    public String getTargetPath() {
        return targetPath;
    }

    // A megadott utvonal setter metodusa
    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    /* WatchListener elinditasa: ha mar letezik , akkor return -> hogy ne lehessen 2x elinditani.
     * Letrehozzuk a felelos Threadet */
    public void startWatchListener() {
        if( serverThread != null ) return;
        serverRunning = true;
        createTCPServerThread();
    }

    /* WatchListener megallitasa: ha nem letezik a akkor return -> hogy ne lehessen
     * megallitani mikor nem is fut. Mivel a ServerSocket accept metodusa blokkolo
     * jellegu, emiatt ennek feloldasahoz szukseg van a szerver lezarasahoz. */
    public void stopWatchListener(){
        if( serverThread == null) return;
        serverRunning = false;
        try{ serverSocket.close(); } catch (IOException e) { e.printStackTrace(); }
    }

    /* A szal egy szervert uzemeltet amivel a masik eszkoztol a mar feldolgozott eventeket kapja
    * meg uzenetkent (parts[0]=ID parts[1]=kind, parts[2]=filename, parts[3]=filetype, parts[4]=modifydate).
    * Elszor megnezzuk hogy az uzenet teljes-e, Create vagy Modify tipusu event jott e. Ha igen akkor
    * FileTransferhez hasonloan ket esetet kulonboztetunk meg: megvan a fajl vagy nincs. Ha megvan, akkor
    * a fajl frissitese tortenik, maskepp kerjuk a kovetkezo eventet. Ha nincs meg a fajl/event akkor eloszor
    * megnezzuk, hogy mappa-e. Ha igen, akkor letrehozzuk es kerjuk a kovetkezo fajlt. A masik eset amikor eloszor
    * azt ellenorizzuk, hogy a fajl almappakban van e, mert ha igen akkor kuldes elott azokat kell letrehozni
    * ha meg nem leteznek, csak ezutan tortenhet meg a fajl kuldese. Vegezetul bezarjuk a klienst es feltuntetjuk
    * a szerver zarast a UI-ban. */
    private void createTCPServerThread(){
        serverThread = new Thread(() -> {
            System.out.println( "WatchListener> Accepting events STARTED");
            try {
                serverSocket = new ServerSocket(4448);
                while(serverRunning){
                    Socket clientSocket = serverSocket.accept();
                    DataInputStream  dis = new DataInputStream(clientSocket.getInputStream());

                    while (serverRunning){
                        if ( dis.available() > 0 ) {
                            String query = dis.readUTF();
                            String[] parts = query.split(":");
                            System.out.println( "WatchListener<Process-" + parts[0] + "> Query received: \"" + query + "\"" );
                            if (parts.length == 5) {
                                if( parts[1].equals("ENTRY_CREATE") || parts[1].equals("ENTRY_MODIFY") ){
                                    Long longDate = Long.parseLong(parts[4]);
                                    Date date = new Date( longDate );
                                    File f = new File( this.targetPath + parts[2] );
                                    if( f.exists() ){
                                        if( parts[3].equals("file") && date.after( new Date(f.lastModified()) ) ) {
                                            System.out.println( "WatchListener<Process-" + parts[0] + "> File update" );
                                            createTCPClientThread(parts[0], longDate, parts[2]);
                                        } else {
                                            createEventClientThread("");
                                            System.out.println( "WatchListener<Process-" + parts[0] + "> Asking the next file." );
                                        }
                                    } else {
                                        if( parts[3].equals("directory") ) {
                                            System.out.println( "WatchListener<Process-" + parts[0] + "> Creating new directory" );
                                            Files.createDirectories( Paths.get( this.targetPath + parts[2] ) );
                                            createEventClientThread("");
                                            String toUI = "EVENT ID:" + parts[0] + " | NAME:" +  targetPath + parts[2] ;
                                            setter.setListViewToWatchListener(toUI);
                                        } else {
                                            String[] tempArray = parts[2].split("/");
                                            if (tempArray.length >= 3) {
                                                String temp = "";
                                                for (int q = 1; q < tempArray.length - 1; q++) {
                                                    temp += "/" + tempArray[q];
                                                }
                                                try {
                                                    Files.createDirectories( Paths.get( this.targetPath + temp ) );
                                                } catch (IOException e) {
                                                    System.err.println("Server> Failed to create directory!" + e.getMessage());
                                                }
                                            }
                                            System.out.println( "WatchListener<Process-" + parts[0] + "> File transfer" );
                                            createTCPClientThread(parts[0], longDate, parts[2]);
                                        }
                                    }
                                }
                            }
                            dis.close();
                            clientSocket.close();
                            break;
                        }
                    }
                }
            } catch (SocketException e) { System.err.println("WatchListener> ServerSocket closed, can't wait for clients.");
            } catch (UnknownHostException e) { e.printStackTrace();
            } catch (IOException e) { e.printStackTrace(); }
            serverThread = null;
            setter.stopWatchListenerUI();
        });
        serverThread.start();
    }

    /* A szal feladata hogy csatlakozzon a masik eszkozon valo szerverhez, es
    * elkuld egy uzenetet ami jelzi hogy vegzett a megadott fajl atkuldesevel, megerkezett,
    * ezzel keri a szervertol a kovetkezo eventet. Mappa letrehozas eseten vagy ha nincs mit
    * kezdjunk az adott eventel ures lesz az uzenet azon resze. */
    private void createEventClientThread(String filename){
        if( EventClientThread != null ) return;
        EventClientThread = new Thread(() -> {
            try{
                Socket client = new Socket( address, 4449 );
                DataOutputStream dos = new DataOutputStream(client.getOutputStream());
                dos.writeUTF(filename+":Send the next file");
                dos.flush();
                dos.close();
                client.close();
            } catch (IOException e) { e.printStackTrace(); }
            EventClientThread = null;
        });
        EventClientThread.start();
    }

    /* A szal a keres-fogadas megvalositasaeert felel. Mukodese hasonlo a FileTransfer szerverehez.
    * A parameterbol kapott adatokal elkuld egy "REQUEST:ID:filename" tipusu uzenetet  amivel keri
    * az adott fajlt a masik eszkoz szerveretol. Miutan elkulduk a kerest varunk a szerver valaszara,
    * azaz az adott fajlra. Beolvassuk a fajlt a csatornabol majd kiirjuk. Vegezetul leellenorizzuk
    * hogy tenyleg megerkezett e, ha igen beallitjuk a modositasi datumot,amennyiben nem megismeteljuk
    * az egesz muveletet addig amig meg nem erkezik a fajl. Vegezetul bezarjuk a klienset es a csatornakat
    * majd kerjuk a kovetkezo eventet. */
    private void createTCPClientThread(String ID, Long lastmodified , String filename){
        if( clientThread != null ) return;
        clientThread = new Thread(() -> {
            try{
                Socket client = new Socket( address, 4450);
                DataInputStream  dis = new DataInputStream(client.getInputStream());
                DataOutputStream dos = new DataOutputStream(client.getOutputStream());

                boolean havefile = false;
                while( !havefile ){
                    if( serverRunning == false ) break;
                    // A keres/igeny elkuldese az adott fajlrol
                    System.out.println("WatchListener Client<Process-" + ID + "> REQUESTing: " + filename);
                    String request = "REQUEST:" + ID + ":" + filename;
                    dos.writeUTF(request);
                    dos.flush();
                    // Fajl fogadasa
                    int size = dis.readInt();
                    if( size == -1) break;
                    else {
                        OutputStream output = new FileOutputStream(this.targetPath + filename);   // output file
                        System.out.println("WatchListener Client<Process-" + ID + "> READing: " + filename);
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while (size > 0 && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
                            output.write(buffer, 0, bytesRead);
                            size -= bytesRead;
                        }
                        output.close();
                        // Leellenorizzuk hogy a fajl megerkezett-e
                        File f = new File(this.targetPath + filename);
                        if (f.exists() && !f.isDirectory()) {
                            System.out.println("WatchListener Client<Process-" + ID + "> File " + this.targetPath + filename + " transfered.");
                            f.setLastModified(lastmodified);
                            havefile = true;
                            String toUI = "EVENT ID:" + ID + " | NAME:" + targetPath + filename;
                            setter.setListViewToWatchListener(toUI);
                        }
                    }
                }
                dos.close();
                dis.close();
                client.close();
            } catch (IOException e) { e.printStackTrace(); }
            clientThread = null;
            createEventClientThread(filename);
        });
        clientThread.start();
    }
}
