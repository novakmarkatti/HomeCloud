package homecloudmobile.FileTransfer;

import android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

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
    }

    public String getTargetDirectory() {
        return targetDirectory;
    }

    public void setTargetDirectory(String targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    public synchronized void startServer() {
        if( serverThread != null ) return;
        serverRunning = true;
        createTCPServerThread();
        setter.serverStarted();
    }

    public synchronized void stopServer(){
        if( serverThread == null ) return;
        serverRunning = false;
        try{ serverSocket.close(); } catch (IOException e) { Log.e("output" , "Server> stopServer\n" +  e.toString()); }
    }

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
                            Log.d("output","Server> Query received: \"" + query + "\"");
                            String[] parts = query.split(":");
                            Date tempDate = new Date(Long.parseLong(parts[1]));
                            fileNames = discoverDirectory(targetDirectory);

                            // (3) Ellenorizzuk hogy szuksegunk van-e az adott fajlra
                            String answer = "Send the next file";
                            if ( fileNames.containsKey(parts[0]) ) {
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
                                    // CREATING DIRECTORIES
                                    File directory = new File(targetDirectory + temp);
                                    directory.mkdirs();
                                }
                                answer = "REQUEST:" + parts[0];
                            }
                            Log.d("output","Server> " + answer);
                            dos.writeUTF(answer);
                            dos.flush();

                            // (4) A klienstol jovo valasz fogadasa
                            if( answer.contains("REQUEST:") ) {
                                Log.d("output","Server> READing: " + parts[0]);
                                OutputStream output = new FileOutputStream(targetDirectory + parts[0]);   // output file
                                int size = dis.readInt();
                                byte[] buffer = new byte[1024];
                                int bytesRead;
                                while (size > 0 && (bytesRead = dis.read(buffer, 0, Math.min(buffer.length, size))) != -1) {
                                    output.write(buffer, 0, bytesRead);
                                    size -= bytesRead;
                                }
                                output.close();

                                // (5) Ellenorizzuk, shogy a fajl valoban megerkezett
                                File f = new File(targetDirectory + parts[0]);
                                answer = "Send the next file";
                                if (f.exists() && !f.isDirectory()) {
                                    Log.d("output","Server> File " + parts[0] + " transfered.");
                                    setter.setListViewToServer( targetDirectory + parts[0] );
                                    f.setLastModified( Long.parseLong(parts[1]) );
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
            } catch (SocketException e) { Log.e("output","Server closed");
            } catch (IOException e)     { Log.e("output", "Client> createTCPServerThread\n" + e.toString() ); }
            serverThread = null;
            setter.serverStopped();
        });
        serverThread.start();
    }

    private HashMap discoverDirectory(String path){
        File parentDir = new File(path);
        ArrayList<File> result = getListFiles( parentDir );
        HashMap<String, Long> tempData = new HashMap<String, Long>();
        for (File f : result) {
            String temp = f.toString();
            String tempString = temp.substring( path.length(), temp.length());
            tempData.put(tempString, f.lastModified() );
        }
        return tempData;
    }

    private ArrayList<File> getListFiles(File parentDir) {
        ArrayList<File> inFiles = new ArrayList<File>();
        File[] files = parentDir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                inFiles.addAll(getListFiles(file));
            } else {
                inFiles.add(file);
            }
        }
        return inFiles;
    }


}
