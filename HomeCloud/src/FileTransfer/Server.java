package FileTransfer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Server {

    private final int SERVER_PORT = 4460;
    private volatile boolean serverRunning = false;
    private FileTransferUISetter setter;
    private String targetDirectory ;
    private HashMap<String, Date> fileNames;
    private ServerSocket serverSocket;
    private Thread serverThread = null;

    public Server( FileTransferUISetter setter ,  String fileName) {
        this.targetDirectory = fileName;
        this.setter = setter;
        System.err.println("Server: " + targetDirectory);
    }

    // A cel utvonal getter metodusa
    public String getTargetDirectory() {
        return targetDirectory;
    }

    // A cel utvonal setter metodusa
    public void setTargetDirectory(String targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    // Ezzel tudjuk lekerdezni, hogy a szerver fut-e
    public boolean isServerRunning() {
        return serverRunning;
    }

    /* Szerver elinditasa: ha mar letezik , akkor return -> hogy ne lehessen 2x elinditani.
     * Letrehozzuk a felelos Threadet , UI-ban feltuntetjuk hogy elindult */
    public synchronized void startServer() {
        if( serverThread != null ) return;
        serverRunning = true;
        createTCPServerThread();
        setter.serverStarted();
    }

    /* Szerver megallitasa: ha nem letezik , akkor return -> hogy ne lehessen megallitani mikor nem is fut
    * Mivel a ServerSocket accept metodusa blokkolo jellegu, emiatt ennek feloldasahoz szukseg van a
    * szerver lezarasahoz. */
    public synchronized void stopServer(){
        if( serverThread == null ) return;
        serverRunning = false;
        try{ serverSocket.close(); } catch (IOException e) { e.printStackTrace(); }
    }

    /* A szerver oldali kommunikacioert felel. (1) Eloszor az adott porton letrehozzuk a TCP szervert,
     * majd ha egy kliens csatlakozni szeretne, elfogadjuk, ezt UI-ban is feltuntetjuk, tovabba letrehozzuk
     * a szukseges in- es output csatornakat. (2) Amennyiben a klienstol "Disconecting" uzenetet kaptuk,
     * bezarjuk az adott kliensel a kapcsolatot, maskepp ertelmezzuk es elemezzuk az uzenetet. Mivel a kliens
     * rakerdez egy fajlra es utanna elkuldi a fajlt, igy annak ellenorzesekepp hogy megvan illetve a legfrisebb
     * az adott fajl ezert szukseg van az adott celmappa minden iteracioban valo bejarasara. (3) Ezutan ellenorizzuk
     * az igenyt a fajlra , amiben ket esetet kulonboztetunk meg: mar letezik, vagy meg nem a fajl. Amennyiben letezik,
     * akkor a frissitesrol beszelunk, mivel csak azt kell megneznunk, hogy a kliens altal kuldott fajl frissebb-e.
     * Amennyiben igen, elkuldjuk az igenyt a fajlra, maskepp kerjuk a kovetkezo fajlt. Ha a fajl nem letezik, akkor
     * elso sorban ellenorizuk, hogy a konyvtar amiben van, letezik-e. Ezt ugy tudjuk megnezni, hogy az adott fajlnev
     * tobb reszre oszthato mint 3. Pl. fajlnev: "\\a\\a.txt"  -> tempArray[0]='' | tempArray[1] ='a' | tempArray[2]='a.txt'
     * Amennyiben konyvtarat kell letrehozni fajl fogadas elott, fajlnev nelkul osszerakjuk az eleresi utvonalat, majd
     * letrehozzuk a konyvtarakat az adott utvonalon. Legvegul pedig elkuldjuk az igenyt a fajlra. (4) Abban az esetben
     * ha igenyt kuldtunk , akkor varunk a kliens valaszara(magara a fajlra): letrehozzuk az output fajlt, aztan
     * beolvassuk a fajl meretet, majd sorra egy bufferbe olvassuk a klienstol jovo uzenetet addig amig van mit olvasni,
     * a bufferbol pedig kiirjuk a fajlba. (5) Ezutan ellenorizzuk, hogy a fajl valoban megerkezett: ha igen, beallitjuk
     * a kliens altal kuldott utolso modifikalasi datumot es a UI-ban azonnal megjelenitjuk ha megerkezett a fajl,
     * ellenkezo esetben ujra elkuldjuk az igenyt a fajlra. (6) Abban az esetben ha a kliens "disconect" uzenetet
     * kuldott vagy pedig megallitottuk mar a szervert, lezarjuk a kliensel valo kapcsolatot. Ha lezartuk a szervert,
     * akkor a UI-ban is feltuntetjuk. */
    private void createTCPServerThread(){
        serverThread = new Thread(() -> {
            try {
                // (1) inicializalas
                serverSocket = new ServerSocket(SERVER_PORT);
                while(serverRunning) {
                    Socket clientSocket = serverSocket.accept();
                    setter.clientConnected();
                    DataInputStream dis  = new DataInputStream(clientSocket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());

                    // (2) kommunikacio a kliensel
                    while (serverRunning) {
                        if (dis.available() > 0) {
                            String query = dis.readUTF();
                            if( query.equals("Disconecting") ) break;
                            System.out.println("Server> Query received: \"" + query + "\"");
                            String[] parts = query.split(":");
                            Date tempDate = new Date(Long.parseLong(parts[1]));
                            fileNames = discoverDirectory();

                            // (3) Ellenorizzuk hogy szuksegunk van-e az adott fajlra
                            String answer = "Send the next file";
                            if (fileNames.containsKey(parts[0]) ) {
                                // letezik a fajl => frissites
                                if (tempDate.after(fileNames.get( parts[0] ))) {
                                    answer = "REQUEST:" + parts[0];
                                }
                            } else {
                                // a fajl konyvtara amiben van nem letezik => eloszor konyvtar letrehozasa
                                String[] tempArray = parts[0].split("/");
                                if (tempArray.length >= 3) {
                                    String temp = "";
                                    for (int q = 1; q < tempArray.length - 1; q++) {
                                        temp += "/" + tempArray[q];
                                    }
                                    try {
                                        Path path = Paths.get(targetDirectory + temp);
                                        Files.createDirectories(path);
                                    } catch (IOException e) {
                                        System.err.println("Server> Failed to create directory!" + e.getMessage());
                                    }
                                }
                                answer = "REQUEST:" + parts[0];
                            }
                            System.out.println("Server> " + answer);
                            dos.writeUTF(answer);
                            dos.flush();

                            // (4) A klienstol jovo valasz fogadasa
                            if( answer.contains("REQUEST:") ) {
                                System.out.println("Server> READing: " + parts[0]);
                                OutputStream output = new FileOutputStream(targetDirectory + parts[0]);   // output file
                                int size = dis.readInt();
                                byte[] buffer = new byte[1024];
                                int bytesRead;
                                while (size > 0 && (bytesRead = dis.read(buffer, 0, Math.min(buffer.length, size))) != -1) {
                                    output.write(buffer, 0, bytesRead);
                                    size -= bytesRead;
                                }
                                output.close();

                                // (5) Ellenorizzuk, hogy a fajl valoban megerkezett
                                File f = new File(targetDirectory + parts[0]);
                                answer = "Send the next file";
                                if (f.exists() && !f.isDirectory()) {
                                    System.out.println("Server> File " + parts[0] + " transfered.");
                                    f.setLastModified(Long.parseLong(parts[1]));
                                    setter.setListViewToServer( targetDirectory + parts[0] );
                                } else {
                                    answer = "REQUEST:" + parts[0];
                                }
                                dos.writeUTF(answer);
                                dos.flush();
                            }
                        }
                    }
                    // (6) Lezaras
                    dos.close();
                    dis.close();
                    clientSocket.close();
                    setter.clientDisconnected();
                }
            } catch (SocketException e) { System.err.println("Server closed");
            } catch (IOException e)     { e.printStackTrace(); }
            serverThread = null;
            setter.serverStopped();
        });
        serverThread.start();
    }

    /* A metodus azonos a Client osztalyban meglevovel: az adott eleresi ut bejarasaert felel.
    * Kulonbseg hogy itt datumkent taroljuk az utolso modifikalas datumat(alapbol long) ,
    * illetve a fajlnevben kicsereljuk a \-eket \\-re a konyebb, egyertelmubb es biztosabb
    * Stringekkel valo dolgozas miatt. Tovabba az UI-ban nem itt adjuk ertekul az elemeket. */
    private HashMap discoverDirectory() {
        try (Stream<Path> walk = Files.walk(Paths.get(targetDirectory))) {
            List<String> result = walk.filter(Files::isRegularFile).map(x -> x.toString()).collect(Collectors.toList());
            HashMap<String, Date> tempData = new HashMap<String, Date>();
            for (String temp : result) {
                String tempString1 = temp.substring(targetDirectory.length(), temp.length());
                String temporal = tempString1.replace("\\", "/");
                File file = new File(temp);
                tempData.put(temporal, new Date(file.lastModified()));
            }
            return tempData;
        } catch (IOException e) { e.printStackTrace(); }
        return null;
    }

}
