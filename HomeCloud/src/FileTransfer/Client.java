package FileTransfer;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Client {

    private final String SERVER_IP;
    private final int SERVER_PORT  = 4460;
    private String pathName;
    private HashMap<String, Long> fileNames;
    private FileTransferUISetter setter;
    private volatile boolean running = false;
    private Thread clientThread = null;
    private ObservableList elements = FXCollections.observableArrayList();

    public Client( FileTransferUISetter setter, String fileName , String SERVER_IP) {
        this.setter     = setter;
        this.pathName   = fileName;
        this.SERVER_IP  = SERVER_IP;
    }

    // ~ ezzel tudjuk lekerdezni, hogy a kliens fut-e
    public boolean isClientRunning() {
        return running;
    }

    /* Kliens elinditasa: ha mar letezik , akkor return -> hogy ne lehessen 2x elinditani.
    *  Letrehozzuk a felelos Threadet , UI-ban feltuntetjuk hogy elindult */
    public synchronized void startClient(){
        if( clientThread != null ) return;
        running = true;
        createTCPClientThread();
        setter.clientStarted();
    }

    // Kliens megallitasa: ha nem letezik , akkor return -> hogy ne lehessen megallitani mikor nem is fut
    public synchronized void stopClient(){
        if( clientThread == null ) return;
        running = false;
    }

    /* A kliens oldali kommunikacioert felel. (1) Eloszor csatlakozas a szerverhez, majd ezt UI-ban is
    * feltuntetjuk. Letrehozzuk a szukseges in- es output csatornakat, majd a fileNames-nek odaadjuk
    * a metodus altal oszegyujtott file neveket es eleresi datumokat. A talalt fajlokat megjelenitjuk
    * az UI-ban. (2) Vegigmegyunk a fileNames-en, es ezzel rakerdezunk a szervernel az adott file-ra.
    * Varunk a szerver valaszara: ha a szerver valaszolt, es a "Send the next file" uzenet jott, akkor
    * rakerdezunk a kovetkezo fajlra. Amennyiben egy REQUEST:fajlnev tipusu uzenet jott, akkor a
    * szerver keri a fajlt. Beolvassuk teljesen a fajlt , majd ezutan elkuldjuk a fajl meretet es utanna
    * magat a fajlt. (3) megszakitas eseten (running valtozo) vagy ha vegigmentunk az osszes fajlon a kliens
    * egy "Disconecting" uzenettel jelzi a szerver fele lecsatlakozasat, amit majd az UI-ban is feltuntetunk.*/
    private void createTCPClientThread(){
        clientThread = new Thread(() -> {
            try {
                // (1) inicializalas
                Socket socket = new Socket(SERVER_IP, SERVER_PORT);
                setter.connectedToServer();
                DataInputStream dis  = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                fileNames = discoverDirectory(pathName);
                setter.setListViewToClient( elements );

                // (2) kommunikacio a szerverrel
                for (String i : fileNames.keySet() ) {
                    if (running == false) break;
                    // uzenet kuldese a szervernek
                    String msg = i + ":" + fileNames.get(i).toString();
                    System.out.println("\nClient> Processing file : " + msg);
                    dos.writeUTF(msg);
                    dos.flush();
                    // a valasz-uzenet fogadasa
                    while (running) {
                        if (dis.available() > 0) {
                            String query = dis.readUTF();
                            if (query.equals("Send the next file")) break;

                            String[] parts = query.split(":");
                            if ( parts[0].equals("REQUEST")) {
                                // a kert fajl beolvasasa
                                File file = new File(pathName + parts[1]);
                                byte[] bytearray = new byte[(int) file.length()];
                                FileInputStream fis = new FileInputStream(file);
                                DataInputStream dataIS = new DataInputStream(new BufferedInputStream(fis));
                                dataIS.readFully(bytearray, 0, bytearray.length);
                                dataIS.close();
                                fis.close();
                                // a kert fajl kuldese
                                System.out.println("Client> Sending file " + i + "(size: " + Long.toString(bytearray.length) + "B)");
                                dos.writeInt(bytearray.length);
                                dos.write(bytearray, 0, bytearray.length);
                                dos.flush();
                            }
                        }
                    }
                }

                // (3) lecsatlakozas
                System.out.println("Client> Disconecting..");
                dos.writeUTF("Disconecting");
                dos.flush();

                dis.close();
                dos.close();
                socket.close();
            } catch (SocketException e)      { e.printStackTrace();
            } catch (UnknownHostException e) { e.printStackTrace();
            } catch (IOException e)          { e.printStackTrace(); }
            clientThread = null;
            setter.clientStopped();
        });
        clientThread.start();
    }

    /* Az adott eleresi uttal rendelkezo mappa bejarasa (param. path)
    * Bejarjuk az adott konyvtar struktura fa szerkezetenek osszes levelet, majd
    * szurunk a rendes fajlokra , ezaltal megkapjuk eredmenynek a letezo fajlokat.
    * Egy HashMap-ban eltaroljuk csupan a fileok neveit illetve utolso modositasi datumukat.
    * Osszegyujtjuk a fileokat a UI-ban valo megjelenitesert (elements)
    * Ha nem tortent hiba, visszaterunk a fentebb emlitett Hashmap-el, ellenkezo esetben null-al. */
    private HashMap discoverDirectory(String path){
        try (Stream<Path> walk = Files.walk(Paths.get(path))) {
            List<String> result = walk.filter(Files::isRegularFile).map(x -> x.toString()).collect(Collectors.toList());
            HashMap<String, Long> tempData = new HashMap<String, Long>();
            for (String temp : result) {
                String tempString = temp.substring(path.length(), temp.length());
                String temporal = tempString.replace("\\", "/");
                File file = new File(temp);
                tempData.put(temporal, file.lastModified());
                this.elements.add(temp);
            }
            return tempData;
        } catch (IOException e) { e.printStackTrace(); }
        return null;
    }

}
