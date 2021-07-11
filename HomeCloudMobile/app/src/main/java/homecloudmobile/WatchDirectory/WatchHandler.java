package homecloudmobile.WatchDirectory;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
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

    public WatchHandler(WatchDirectoryUIsetter setter, String path, String IPaddress){
        this.setter = setter;
        this.filePath = path;
        this.address = IPaddress;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void WatchHandlerCheckFailedFiles(){
        if( directoryWatcher != null) {
            directoryWatcher.FailedFiles();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void startWatchService() throws IOException {
        if( serverThread != null ) return;
        Path dir = Paths.get( this.filePath );
        directoryWatcher = new WatchDir(setter, dir, address);
        directoryWatcher.processEvents();
        serverRunning = true;
        createTCPServerThread();
    }

    public void stopWatchService(){
        if (directoryWatcher == null) return;
        directoryWatcher.stopWatchDir();
        serverRunning = false;
        try{ serverSocket.close(); } catch (IOException e) { Log.e("output", "stopWatchService\n" + e.toString()); }
    }

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
                            Log.d( "output","WatchHandler<Process-" + parts[1]+ "> Query received: \"" + query + "\"" );
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
                                    Log.d("output", "WatchHandler<Process-" + parts[1] + "> Sending file: " + parts[2]);
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
            } catch (SocketException e)      { Log.e( "output","WatchHandler> ServerSocket closed.");
            } catch (UnknownHostException e) { Log.e( "output","WatchHandler> createTCPServerThread\n" + e.toString());
            } catch (IOException e)          { Log.e( "output","WatchHandler> createTCPServerThread\n" + e.toString()); }
            serverThread = null;
            setter.stopWatchHandlerUI();
        });
        serverThread.start();
    }

}
