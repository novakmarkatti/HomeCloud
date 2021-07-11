package homecloudmobile.WatchDirectory;

import android.os.Build;
import android.util.Log;
import androidx.annotation.RequiresApi;
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                keys.put( dir.register(watcher, ENTRY_DELETE, ENTRY_CREATE, ENTRY_MODIFY, OVERFLOW) , dir );
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void processEvents() {
        if( processEventsThread != null ) return;
        this.running = true;
        watchQueueServerThread();
        handleEventsStorageThread();
        Log.d("output","WatchDir> Directory listening STARTED");
        processEventsThread = new Thread(() -> {
            while( running ) {
                WatchKey key = watcher.poll();
                if( key != null ) {

                    Path dir = keys.get(key);
                    if (dir == null) {
                        Log.e("output","WatchDir> WatchKey not recognized!!");
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
                            } catch (IOException e) { Log.e("output", "WatchDir> processEvents\n" + e.toString()); }
                        }

                        String msg = this.ID + "~" + event.kind() + "~" + child.toString().replace("\\", "/") ;
                        eventsStorage.put(msg);
                        this.ID++;
                    }

                    // reset key and remove from set if directory no longer accessible
                    boolean valid = key.reset();
                    if (!valid) {
                        keys.remove(key);
                        if (keys.isEmpty()) break;
                    }
                }
            }
            processEventsThread = null;
            watcher = null;
            keys = null;
            setter.stopWatchHandlerUI();
            Log.e("output","WatchDir> processEvents finished.");
        });
        processEventsThread.start();
    }

    private void handleEventsStorageThread() {
        if( eventsStorageThread != null ) return;
        eventsStorageThread = new Thread(() -> {
            while(running) {
                if( eventsStorage.isEmpty() ){
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) { Log.e("output" , e.toString() ); }
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
                    String toUI = "EVENT ID:" + parts[0] + " | EVENT:" + parts[1] + " | NAME:" + directoryPath + fileName + " | TYPE:" + fileType ;
                    setter.setListViewToWatchHandler(toUI);
                }
            }
            eventsStorageThread = null;
        });
        eventsStorageThread.start();
    }

    private void watchQueueServerThread() {
        if( queueThread != null ) return;
        Log.d("output","WatchDir> Queue server started.");
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
                                        } catch (InterruptedException e) { Log.e("output" , e.toString() ); }
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
            } catch (SocketException e) { Log.e("output","WatchDir> Queue server closed.");
            } catch (IOException e)     { Log.e("output", "WatchDir> watchQueueServerThread\n" + e.toString()); }
            queueThread = null;
            setter.stopWatchHandlerUI();
        });
        queueThread.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void FailedFiles(){
        Log.d("output","=======FailedFiles=======");
        for( String directoryName : directoryNames ){
            try (Stream<Path> walk = Files.walk(Paths.get( directoryName ))) {
                List<String> result = walk.filter(Files::isRegularFile).map(x -> x.toString()).collect(Collectors.toList());
                for (String temporal : result) {
                    String temp = temporal.replace("\\", "/");
                    if( !fullList.contains(temp) ) {
                        fullList.add(temp);
                    }
                }
            } catch (IOException e) { Log.e("output", "WatchDir> FailedFiles\n" + e.toString()); }
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

    public void stopWatchDir(){
        if(queueThread == null) return;
        this.running = false;
        try{ serverSocket.close(); } catch (Exception e) {}
    }

    private void createTCPClientThread(String msg){
        if( clientThread != null ) return;
        clientThread = new Thread(() -> {
            try{
                Socket server = new Socket( this.address, 4448);
                DataOutputStream dos = new DataOutputStream(server.getOutputStream());
                String[] parts = msg.split(":");
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Log.d("output","WatchDir<Process-" + parts[0] + "> OPERATION: ID:" + parts[0] + " | EVENT:" + parts[1] + " | NAME:" + parts[2] + " | TYPE:" + parts[3] + " | LAST MODIFIED:" + format.format( new Date( Long.parseLong(parts[4]) )  ) );
                dos.writeUTF(msg);
                dos.flush();
                dos.close();
                server.close();
            } catch (UnknownHostException e) { Log.e("output", "WatchDir> createTCPClientThread\n" + e.toString());
            } catch (IOException e)         { Log.e("output", "WatchDir> createTCPClientThread\n" + e.toString()); }
            clientThread = null;
        });
        clientThread.start();
    }

}
