package homecloudmobile.WatchDirectory;

import android.os.Build;
import android.util.Log;
import androidx.annotation.RequiresApi;
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
import java.text.SimpleDateFormat;
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

    public WatchListener(WatchDirectoryUIsetter setter, String targetPath, String IPaddress){
        this.setter = setter;
        this.targetPath = targetPath;
        this.address = IPaddress;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void startWatchListener() {
        if( serverThread != null ) return;
        serverRunning = true;
        createTCPServerThread();
    }

    public void stopWatchListener(){
        if( serverThread == null) return;
        serverRunning = false;
        try{ serverSocket.close(); } catch (IOException e) { }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createTCPServerThread(){
        serverThread = new Thread(() -> {
            Log.d("output" ,"WatchListener> Accepting events STARTED");
            try {
                serverSocket = new ServerSocket(4448);
                while(serverRunning){
                    Socket clientSocket = serverSocket.accept();
                    DataInputStream  dis = new DataInputStream(clientSocket.getInputStream());

                    while (serverRunning){
                        if ( dis.available() > 0 ) {
                            String query = dis.readUTF();
                            String[] parts = query.split(":");
                            Log.d("output" , "WatchListener<Process-" + parts[0] + "> Query received: \"" + query + "\"" );
                            if (parts.length == 5) {
                                if( parts[1].equals("ENTRY_CREATE") || parts[1].equals("ENTRY_MODIFY") ){
                                    Long longDate = Long.parseLong(parts[4]);
                                    Date date = new Date( longDate );
                                    File f = new File( this.targetPath + parts[2] );
                                    if( f.exists() ){
                                        if( parts[3].equals("file") && date.after( new Date(f.lastModified()) ) ) {
                                            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                            Log.d("output" , "WatchListener<Process-" + parts[0] + "> File update" );
                                            Log.e("output" , "ERKEZETT: DATE: " + date  + " LONG DATE: " + parts[4] );
                                            Log.e("output" , "MEGLEVO: DATE: " + new Date(f.lastModified())  + " LONG DATE: " + f.lastModified() );
                                            createTCPClientThread(parts[0], longDate, parts[2]);
                                        } else {
                                            createEventClientThread("");
                                            Log.d("output" , "WatchListener<Process-" + parts[0] + "> Asking the next file." );
                                        }
                                    } else {
                                        if( parts[3].equals("directory") ) {
                                            Log.d("output" , "WatchListener<Process-" + parts[0] + "> Creating new directory" );
                                            Files.createDirectories( Paths.get( this.targetPath + parts[2] ) );
                                            createEventClientThread("");
                                            String toUI = "EVENT ID:" + parts[0] + " | NAME:" + targetPath + parts[2] ;
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
                                                    Log.e("output" ,"Server> Failed to create directory!" + e.toString());
                                                }
                                            }
                                            Log.d("output" , "WatchListener<Process-" + parts[0] + "> File transfer" );
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
            } catch (SocketException e)      { Log.e("output" ,"WatchListener> ServerSocket closed, can't wait for clients.");
            } catch (UnknownHostException e) { Log.e("output" ,"WatchListener> createTCPServerThread" + e.toString());
            } catch (IOException e)          { Log.e("output" ,"WatchListener> createTCPServerThread" + e.toString()); }
            serverThread = null;
            setter.stopWatchListenerUI();
        });
        serverThread.start();
    }

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
            } catch (IOException e) {  Log.e("output" ,"WatchListener> createEventClientThread\n" + e.toString()); }
            EventClientThread = null;
        });
        EventClientThread.start();
    }

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
                    Log.d("output" ,"WatchListener Client<Process-" + ID + "> REQUESTing: " + filename);
                    String request = "REQUEST:" + ID + ":" + filename;
                    dos.writeUTF(request);
                    dos.flush();
                    // Fajl fogadasa
                    int size = dis.readInt();
                    if( size == -1) break;
                    else {
                        OutputStream output = new FileOutputStream(this.targetPath + filename);   // output file
                        Log.d("output", "WatchListener Client<Process-" + ID + "> READing: " + filename);
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
                            Log.d("output", "WatchListener Client<Process-" + ID + "> File " + this.targetPath + filename + " transfered.");
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
            } catch (IOException e) { Log.e("output" ,"WatchListener> createTCPClientThread\n" + e.toString()); }
            clientThread = null;
            createEventClientThread(filename);
        });
        clientThread.start();
    }
}
