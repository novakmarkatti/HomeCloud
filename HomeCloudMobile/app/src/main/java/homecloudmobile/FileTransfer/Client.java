package homecloudmobile.FileTransfer;

import android.util.Log;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;

public class Client {

    private final String SERVER_IP;
    private final int SERVER_PORT  = 4460;
    private String pathName;
    private HashMap<String, Long> fileNames;
    private FileTransferUISetter setter;
    private volatile boolean running = false;
    private Thread clientThread = null;
    private ArrayList<String> elements = new ArrayList<>();

    public Client( FileTransferUISetter setter, String fileName , String SERVER_IP) {
        this.setter     = setter;
        this.pathName   = fileName;
        this.SERVER_IP  = SERVER_IP;
    }

    public synchronized void startClient(){
        if( clientThread != null ) return;
        running = true;
        createTCPClientThread();
        setter.clientStarted();
    }

    public synchronized void stopClient(){
        if( clientThread == null ) return;
        running = false;
    }

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
                    Log.d("output","\nClient> Processing file : " + msg);
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
                                Log.d("output","Client> Sending file " + i + "(size: " + Long.toString(bytearray.length) + "B)");
                                dos.writeInt(bytearray.length);
                                dos.write(bytearray, 0, bytearray.length);
                                dos.flush();
                            }
                        }
                    }
                }

                // (3) lecsatlakozas
                Log.d("output","Client> Disconecting..");
                dos.writeUTF("Disconecting");
                dos.flush();

                dis.close();
                dos.close();
                socket.close();
            } catch (SocketException e)      { Log.e("output", "Client> createTCPClientThread\n" + e.toString() );
            } catch (UnknownHostException e) { Log.e("output", "Client> createTCPClientThread\n" + e.toString() );
            } catch (IOException e)          { Log.e("output", "Client> createTCPClientThread\n" + e.toString() ); }
            clientThread = null;
            setter.clientStopped();
        });
        clientThread.start();
    }

    private HashMap discoverDirectory(String path){
        File parentDir = new File(path);
        ArrayList<File> result = getListFiles( parentDir );
        HashMap<String, Long> tempData = new HashMap<String, Long>();
        for (File f : result) {
            String temp = f.toString();
            String tempString = temp.substring( path.length(), temp.length());
            tempData.put(tempString, f.lastModified() );
            this.elements.add(temp);
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
