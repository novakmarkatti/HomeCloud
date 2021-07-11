package WatchDirectory;

import javafx.scene.control.ListView;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WatchDir {

    private int ID = 1;
    private final String directoryPath;
    private final String address;
    private WatchService watcher;
    private Map<WatchKey,Path> keys;
    private WatchDirectoryUIsetter setter;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private MessageQueue mq;
    private MessageQueue eventsStorage;
    private Thread clientThread         = null;
    private Thread processEventsThread  = null;
    private Thread queueThread          = null;
    private Thread eventsStorageThread  = null;
    private ArrayList<String> transportedFiles  = new ArrayList<>();
    private ArrayList<String> directoryNames    = new ArrayList<>();
    private ArrayList<String> fullList          = new ArrayList<>();

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    public WatchDir(WatchDirectoryUIsetter setter, Path dir, String IPaddress) throws IOException {
        this.setter = setter;
        this.directoryPath = dir.toString().replace("\\", "/");
        this.address = IPaddress;
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,Path>();
        this.mq = new MessageQueue(10000);
        this.eventsStorage = new MessageQueue(10000);
        registerAll(dir);
    }

    // Lekerdezheto, hogy a mappa figyeles es az esemeny feldolgozas fut- e
    public boolean isRunning() {
        return running;
    }

    /* A parameterkent megadott eleresi utat ( az adott konyvtarat amit figyelni fogunk ) es
    * a benne levo al konyvtakarat jarjuk be, es regisztraljuk oket a WatchServicel( 4 esemenyt
    * figyelunk: fajl/mappa torles, letrehozas, modositas es esemeny tulcsordulas), emiatt
    * fogjuk tudni, hogy tortent e esemeny az adott eleresi uton. A metodus rekurzivan hivja
    * magat, igy a kijelolt eleresi utat teljesen bejarja. */
    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                keys.put( dir.register(watcher, ENTRY_DELETE, ENTRY_CREATE, ENTRY_MODIFY, OVERFLOW) , dir );
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /* Az esemenyek figyeleseert felelos szal. Ha mar letezik a szal, akkor returnolunk.
    * Beallitjuk, hogy fut, tovabba letrehozunk 1-1 szalat: az egyik a megtortent esemenyeket
    * tarolokent fogadja, majd feldolgozza oket, azutan a kesz kuldheto esemenyeket a masik
    * szal tarolojaba helyezi, ami aztan az esemenyek masik eszkozhoz valo eljuttatasert felel.
    * Az esemenyek feldolgozasa soran eloszor lekerunk egy watchkey-t a poll metodussal, mivel ez
    * azonnal null-al ter vissza ha nem erheto el a kulcs, igy nem blokkol. Ha a kulcs amit
    * kaptunk nem volt regisztralva inicializalas soran akkor eldobjuk a kulcsot es folytatjuk
    * ciklust, maskepp pedig feldolgozzuk a fuggo esemenyeket a kulcsnak. Nem foglalkozunk a
    * kovetkezo esetekkel: amennyiben OVERFLOW vagy ENTRY_DELETE tortent, vagy pedig egy esemeny
    * mar nem eloszor fordult elo. A fajl nevet/utvonalat az event contextbol tudjuk kinyerni,
    * de ehez az WatchEvent<Path> tipusu kell legyen , emiatt hogy ne kapjunk szimpla castolassal
    * "unchecked or unsafe operations." hibauzenetet letrehozuk a statikus cast metodust ami
    * elfojta az "unchecked" errort. Igy hogy megkaptuk az aktualis event utvonalat meg kell neznunk
    * hogy ENTRY_CREATE eseten mappa volt e ami letrejott, mert ha igen akkor azt is be kell
    * regisztralnunk. Vegezetul pedig egy ID, event tipus es fajl utvonal tipusu uzenetet rakunk be
    * az eventsStorage taroloba ahol majd a szal feldolgozza oket.*/
    public void processEvents() {
        if( processEventsThread != null ) return;
        this.running = true;
        watchQueueServerThread();
        handleEventsStorageThread();
        System.out.println("WatchDir> Directory listening STARTED");
        processEventsThread = new Thread(() -> {
            while( running ) {
                WatchKey key = watcher.poll();
                if( key != null ) {

                    Path dir = keys.get(key);
                    if (dir == null) {
                        System.err.println("WatchDir> WatchKey not recognized!!");
                        continue;
                    }

                    for (WatchEvent<?> event: key.pollEvents()) {
                        if (event.kind() == OVERFLOW)             { continue;
                        } else if (event.kind() == ENTRY_DELETE ) { continue;
                        } else if( event.count() > 1 )            { continue; }

                        WatchEvent<Path> ev = cast(event);
                        Path child = dir.resolve( ev.context() );
                        if (event.kind() == ENTRY_CREATE) {
                            try {
                                if (Files.isDirectory(child, NOFOLLOW_LINKS)) { registerAll(child); }
                            } catch (IOException e) { e.printStackTrace(); }
                        }

                        String msg = this.ID + "~" + event.kind() + "~" + child.toString().replace("\\", "/") ;
                        eventsStorage.put(msg);
                        this.ID++;
                    }

                    // reset key and remove from set if directory no longer accessible
                    boolean valid = key.reset();
                    if (!valid) {
                        keys.remove(key);
                        if (keys.isEmpty()) { break; }
                    }
                }
            }
            processEventsThread = null;
            watcher = null;
            keys = null;
            setter.stopWatchHandlerUI();
            System.err.println("WatchDir> processEvents finished.");
        });
        processEventsThread.start();
    }

    /* Az alabbi metodus az esemenyek tarolasaert es feldolgozasaert felel. Ha a tarolo ures,
    * akkor varakozunk, maskepp ha egy esemeny tortent akkor kiveszuk a tarolobol. Eloszor
    * megnezzuk, hogy az utolos modifikalas datuma 0-e, mert ha igen, az azt jelenti hogy a fajlt
    * vagy mappat toroltek, igy nem kell foglalkoznunk vele. Az eleresi utbol kinyerjuk a fajl nevet,
    * majd ellenorizzuk hogy fajl vagy mappa -e. Ha mappa, akkor hozzaadjuk a FailedFiles metodusban
    * hasznalt mappa eleresi utvonalat tartalmazo listahoz. Elkeszitjuk az esemeny uzenetet, ami egy
    * ID-bol, esemeny tipusabol, fajlnevbol, fajltipusbol es utolso modifikalasi datumbol all. Ha ez
    * az elso esemeny, akkor elkuldjuk a masik eszkozhoz, maskepp beteszuk a mar feldolgozott es
    * kuldheto esemenyeket tarolo sorba. Vegezetul megjelenitjuk a UI-ban az esemenyt.  */
    private void handleEventsStorageThread() {
        if( eventsStorageThread != null ) return;
        eventsStorageThread = new Thread(() -> {
            while(running) {
                if( eventsStorage.isEmpty() ){
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) { e.printStackTrace(); }
                } else {
                    String value = eventsStorage.get();
                    String[] parts = value.split("~");  // ID | EVENT | PATH
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) { e.printStackTrace(); }
                    File f = new File(parts[2]);
                    if (f.lastModified() == 0) {
                        continue;
                    }
                    String fileName = parts[2].substring(directoryPath.length(), parts[2].length());
                    String fileType = "file";
                    if (f.isDirectory()) {
                        fileType = "directory";
                        // CheckFailedFiles >>
                        if( !directoryNames.contains(parts[2]) ) {
                            directoryNames.add( parts[2] );
                        } // << CheckFailedFiles
                    }
                    String msg = parts[0] + ":" + parts[1] + ":" + fileName + ":" + fileType + ":" + f.lastModified();
                    if (parts[0].equals("1")) {
                        createTCPClientThread(msg);
                    } else {
                        mq.put(msg);
                    }
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String toUI = "EVENT ID:" + parts[0] + " | EVENT:" + parts[1] + " | NAME:" + directoryPath + fileName + " | TYPE:" + fileType + " | LAST MODIFIED:" + format.format(new Date(f.lastModified()));
                    setter.setListViewToWatchHandler(toUI);
                }
            }
            eventsStorageThread = null;
        });
        eventsStorageThread.start();
    }

    /* A metodus lenyegeben a termelo-fogyaszto peldaban, a fogyasztonak a termeket kiado felkent
    * lehet elkepzelni. Egy szervert futtatunk, amivel varjuk a masik eszkoztol jovo uzenetet,
    * hogy kuldhetjuk a kovetkezo esemenyt, mivel csak akkor kuldhetunk egy esemenyt, ha a masik
    * fel mar feldolgozott egyet, es keri a kovetkezot(kivetelt kepez el alol a legelso esemeny)
    * A kapott uzenet filename:Send the next file jellegu kell legyen. Ha az elso resz ures, akkor
    * mappa letrehozas tortenhetett, vagy csak egyszeruen nincs szukseg az adott esemenyre
    * (pl. a fajl letezik). Ha nem ures az elso resz, akkor eltaroljuk mint atkuldott fajlt, es ha
    * a masodik resz a Send the next file-t tartalmazza, akkor kiveszunk a mar kuldheto esemenyeket
    * tartalmazo sorbol es elkuldjuk a masik eszkoznek. */
    private void watchQueueServerThread() {
        if( queueThread != null ) return;
        System.out.println("WatchDir> Queue server started.");
        queueThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket( 4449);
                while( running ) {
                    Socket clientSocket = serverSocket.accept();
                    DataInputStream  dis = new DataInputStream(clientSocket.getInputStream());
                    while( running) {
                        if ( dis.available() > 0 ) {
                            String query = dis.readUTF();
                            // CheckFailedFiles >>
                            String[] parts = query.split(":");
                            if( !parts[0].equals("") ){
                                transportedFiles.add( parts[0] );
                            } // << CheckFailedFiles
                            if( parts[1].equals("Send the next file") ){
                                while(running){
                                    if(mq.isEmpty()){
                                        try {
                                            Thread.sleep(1);
                                        } catch (InterruptedException e) { e.printStackTrace(); }
                                    } else{
                                        createTCPClientThread( mq.get() );
                                        break;
                                    }
                                }
                            }
                            dis.close();
                            clientSocket.close();
                            break;
                        }
                    }
                }
            } catch (SocketException e) { System.err.println("WatchDir> Queue server closed.");
            } catch (IOException e) { e.printStackTrace(); }
            queueThread = null;
            setter.stopWatchHandlerUI();
        });
        queueThread.start();
    }

    /* Az at nem kuldott fajlokat vizsgaljuk meg. Azert van erre szukseg, mert a WatchService
    * fajlokat figyel, es van hogy nem veszi eszre az uj mappa beillesztesekor a benne levo osszes
    * fajlt. Ezert lenyegeben hogyha valamilyen esemeny tortenik amely egy mappat erint, eltaroljuk
    * az adott mappa eleresi utvonalat. Amikor a a felhasznalo vegzett a fajlatvitelevel, a gombnyomasra
    * az osszes erintett mappat bejarjuk, es kigyujtjuk a bennuk levo fajlokat egy teljes listaba.
    * A Directoy listening soran mindig eltaroltuk egy listaban hogyha egy fajl atvitelre kerult, igy a
    * kovetkezo lepesben bejarjuk az atkuldott fajlok listajat, es ha az adott fajl mar atkuldesre kerult
    * akkor kivesszuk a mappak bejarasabol letrehozott teljes listabol. Vegezetul pedig ami a teljes listaban
    * maradt, azok az at nem kuldott fajlok. Bejarjuk a listat, es a fajlokat betesszuk eventkent az
    * eventeket tarolo listaba, majd megjelenitjuk a UI-ban hogy a muvelet vegetert. */
    public void FailedFiles(){
        System.out.println("=======FailedFiles=======");
        for( String directoryName : directoryNames ){
            try (Stream<Path> walk = Files.walk(Paths.get( directoryName ))) {
                List<String> result = walk.filter(Files::isRegularFile).map(x -> x.toString()).collect(Collectors.toList());
                for (String temporal : result) {
                    String temp = temporal.replace("\\", "/");
                    if( !fullList.contains(temp) ) {
                        fullList.add(temp);
                    }
                }
            } catch (IOException e) { e.printStackTrace(); }
        }
        directoryNames.clear();

        for( String transportedFile : transportedFiles ){
            for (int i = 0; i < fullList.size() ; i++) {
                if( fullList.get(i).equals(  directoryPath + transportedFile ) ) {
                    fullList.remove(i);
                    break;
                }
            }
        }
        transportedFiles.clear();

        for( String listelem : fullList ){
            String msg = this.ID + "~" + "ENTRY_CREATE" + "~" + listelem ;
            eventsStorage.put(msg);
            this.ID++;
        }
        fullList.clear();

        setter.FailedFilesChecked();
    }

    /* Esemenyfeldolgozas megallitasa: ha nem letezik a szal, akkor return -> hogy ne lehessen
     * megallitani mikor nem is fut. Mivel a ServerSocket accept metodusa blokkolo jellegu, emiatt
     * ennek feloldasahoz szukseg van a szerver lezarasahoz. */
    public void stopWatchDir(){
        if(queueThread == null) return;
        this.running = false;
        try{ serverSocket.close(); } catch (IOException e) { e.printStackTrace(); }
    }

    /* Ertesitjuk a masik eszkozt,ha valamilyen esemeny tortent. Hogyha mar letezik a szal
    * akkor returnolunk. Felcsatlakozunk a masik eszkoz szerverere, es elkuldjuk neki a
    * tortent esemenyt (msg param.) */
    private void createTCPClientThread(String msg){
        if( clientThread != null ) return;
        clientThread = new Thread(() -> {
            try{
                Socket server = new Socket( this.address, 4448);
                DataOutputStream dos = new DataOutputStream(server.getOutputStream());
                String[] parts = msg.split(":");
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                System.out.println("WatchDir<Process-" + parts[0] + "> OPERATION: ID:" + parts[0] + " | EVENT:" + parts[1] + " | NAME:" + parts[2] + " | TYPE:" + parts[3] + " | LAST MODIFIED:" + format.format( new Date( Long.parseLong(parts[4]) )  ) );
                dos.writeUTF(msg);
                dos.flush();
                dos.close();
                server.close();
            } catch (UnknownHostException e) { e.printStackTrace();
            } catch (IOException e) { e.printStackTrace(); }
            clientThread = null;
        });
        clientThread.start();
    }

}
