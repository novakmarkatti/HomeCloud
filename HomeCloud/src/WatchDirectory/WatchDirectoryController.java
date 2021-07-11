package WatchDirectory;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javax.swing.*;

public class WatchDirectoryController {

    @FXML private Button startStopServer;
    @FXML private Button startStopClient;
    @FXML private Button backToMenu;
    @FXML private Button checkFailedFiles;
    @FXML private TextField pathTextField;
    @FXML private Text statusMsg;
    @FXML private Text appMode;
    @FXML private Text appModeText1;
    @FXML private Text appModeText2;
    @FXML private ListView<String> listView = new ListView<>();
    private int WatchHandlerStatus = 0;
    private Object WatchHandlerStatusLock = new Object();
    private WatchHandler server  = null;
    private WatchListener client = null;

    /* A UI-t allitgatjuk vele a WatchHanlder es WatchListener oldalrol abban az esetben :
     *  - Ha a szerver elindult/megallt, befejezodott e a sikertelen fajlok
     * atkuldese es az eventek listaban valo megjelenitesenek okabol.
     *  - Ha a kliens elindult/megallt, illetve az atkuldott fajlok listaban
     *  valo megjelenitesenek okabol. */
    public WatchDirectoryUIsetter setter = new WatchDirectoryUIsetter() {

        @Override public void FailedFilesChecked() {
            UILogger("Failed files are checked");
        }

        @Override public void serverStarted() {
            UILogger("Server started");
        }

        @Override public void stopWatchHandlerUI() {
            synchronized (WatchHandlerStatusLock){
                WatchHandlerStatus++;
                if(WatchHandlerStatus == 3){
                    WatchHandlerStatus = 0;
                    UILogger("Server stopped");
                    Platform.runLater(() -> {
                        startStopServer.setDisable(false);
                        startStopServer.setText("Start server");
                    });
                }
            }
        }
        @Override public void setListViewToWatchHandler( String elem) {
            if( "Server Mode".equals( appMode.getText() ) ) {
                synchronized(listView) {
                    Platform.runLater(() -> {
                        if(listView.getItems() == null){
                            ObservableList<String> items = FXCollections.observableArrayList ( elem );
                            listView.setItems(items);
                        } else {
                            listView.getItems().add( elem );
                        }
                    });
                }
            }
        }

        @Override public void clientStarted() {
            UILogger("Client started");
        }

        @Override public void stopWatchListenerUI() {
            UILogger("Client stopped");
            Platform.runLater(() -> {
                startStopClient.setDisable(false);
                startStopClient.setText("Start client");
            });
        }

        @Override public void setListViewToWatchListener( String elem) {
            if( "Client Mode".equals( appMode.getText() ) ) {
                synchronized(listView) {
                    Platform.runLater(() -> {
                        if(listView.getItems() == null){
                            ObservableList<String> items = FXCollections.observableArrayList ( elem );
                            listView.setItems(items);
                        } else {
                            listView.getItems().add( elem );
                        }
                    });
                }
            }
        }
    };

    /* A szerver(WatchHandler) elinditasaert es megallitasaert felel. Lenyegeben azt figyeljuk milyen szoveg
     * szerepel a gombon. Ha "start" akkor elobbszor megnezzuk hogy a felhasznalo valasztott-e
     * mar cel utvonalat, mert csak ebben az esetben indithato el a szerver. Utanna ha meg nem
     * hoztunk letre szervert , akkor letrehozzuk, es elinditjuk. A szerver eltarolja a legutoljara
     * megadott eleresi utvonalat, ezert ha egy ujabbat vel felfedezni, akkor az ujabbat allitja be,
     * es azt fogja a figyelni. "Stop" eseten megallitjuk a szervert, es a gombot hogy
     * ne lehessen sokszor nyomogatni ezert deaktivaljuk. Miutan a szerver veglegesen lealt, akkor
     * lesz ujra elerheto es elindithato a gomb.*/
    @FXML private void handleStartStopServer(){
        try {
            if( "Start server".equals( startStopServer.getText() ) ){
                if( !pathTextField.getText().equals("") ) {
                    String temp = pathTextField.getText();
                    String temporal = temp.replace("\\" , "/");
                    if (server == null) {
                        server = new WatchHandler(setter, temporal, WatchDirectoryUI.SERVER_IP);
                    }
                    if (! temporal.equals(server.getFilePath()) ) {
                        server.setFilePath( temporal );
                    }
                    server.startWatchService();
                    startStopServer.setText("Stop server");
                    setter.serverStarted();
                } else {
                    UILogger("Choosing of the directory failed.");
                }
            } else if( "Stop server".equals( startStopServer.getText() ) ){
                server.stopWatchService();
                startStopServer.setDisable(true);
            }
        } catch(Exception e){
            System.err.println("ERROR: WatchDirectory> handleStartStopServer");
            e.printStackTrace();
        }
    }

    /* A kliens(WatchListener) elinditasaert es megallitasaert felel. WatchHandler Szerverhez
     * hasonloan azt figyeljuk milyen szoveg szerepel a gombon. Ha "start" akkor eloszor
     * megnezzuk hogy a felhasznalo valasztott-e mar eredet utvonalat, ahova a fajlokat
     * szeretne szallitani, mert csak ebben az esetben indithato el a kliens. "Stop" eseten megallitjuk
     * a klienst es a gombot hogy ne lehessen sokszor nyomogatni ezert deaktivaljuk.
     * Miutan a kliens veglegesen lealt, akkor lesz ujra elerheto es elindithato a gomb.*/
    @FXML private void handleStartStopClient(){
        try {
            if( "Start client".equals( startStopClient.getText() ) ){
                if( !pathTextField.getText().equals("") ) {
                    String temp = pathTextField.getText();
                    String temporal = temp.replace("\\" , "/");
                    if (client == null) {
                        client = new WatchListener(setter, temporal, WatchDirectoryUI.SERVER_IP);
                    }
                    if (! temporal.equals( client.getTargetPath() ) ) {
                        client.setTargetPath( temporal );
                    }
                    client.startWatchListener();
                    startStopClient.setText("Stop client");
                    setter.clientStarted();
                } else {
                    UILogger("Choosing of the directory failed.");
                }
            } else if( "Stop client".equals( startStopClient.getText() ) ){
                client.stopWatchListener();
                startStopClient.setDisable(true);
            }
        } catch(Exception e){
            System.err.println("ERROR: WatchDirectory> handleStartStopClient");
            e.printStackTrace();
        }
    }

    /* Kilepes elott leelenorizzuk hogy van-e olyan fajl amit nem sikerult atkuldeni.
    * Ha a szerver(WatchHandler) letezik, akkor meghivjuk ra a
    * WatchHandlerCheckFailedFiles metodust, ami a WatchHandler-ben meghivja a
    * WatchDir FailedFiles metodusat*/
    @FXML private void handleCheckFailedFiles(){
        if (server != null) {
            UILogger("Checking failed files");
            server.WatchHandlerCheckFailedFiles();
        }
    }

    /* A mappakivalaszto felulet megvalositasaert felel. JFileChooser-t hasznaljuk,
     * de eloszor beallitjuk hogy az OP. rendszerben megszokott feluletet kapjuk.
     * Beallitjuk a felugro ablak cimet es hogy csak mappakat jelenitsen meg tovabba
     * hogy mentse el a kivalasztott elemet a "Save"-re kattintva es ne fuggjon ablakoktol.
     * Megprobaljuk beallitani a TextField-nek (a szerver es a kliens innentrol szerzi meg
     * a szukseges utvonalat) a kivalasztott erteket, majd megjelenitjuk a UI-ban a muvelet
     * siker/sikertelenseget */
    @FXML private void chooseDirectory(){
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException e)          { e.printStackTrace();
        } catch (InstantiationException e)          { e.printStackTrace();
        } catch (IllegalAccessException e)          {e.printStackTrace();
        } catch (UnsupportedLookAndFeelException e) {e.printStackTrace();}

        JFileChooser f = new JFileChooser();
        f.setDialogTitle("Choose a directory");
        f.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        f.showSaveDialog(null);
        try{
            String temp = f.getSelectedFile().toString();
            pathTextField.setText( temp.replace("\\" , "/") );
            UILogger("Directory was chosen successfully");
        } catch(NullPointerException e){
            pathTextField.setText("");
            UILogger("Choosing of the directory failed.");
        }

    }

    /* A UILogger valositja meg , hogy a Statusban mindig latszodjon mi tortenik a programban */
    private void UILogger(String msg){
        Platform.runLater(() -> {
            synchronized(statusMsg){
                statusMsg.setText(msg);
                System.out.println("UILogger> " + msg);
            }
        });
    }

    /* Ezzel terunk vissza a fomenure es zarjuk be az ablakot. Bezaras elott
     * megnezzuk, hogy a szerver(WatchHandler) es kliens(Watchlistener) letezik
     * es fut-e, mert amennyiben igen, megallitjuk oket. */
    @FXML private void handleBackToMenu(){
        try {
            if(server != null) {
                if ( server.isRunning()) {
                    server.stopWatchService();
                }
            }
            if(client != null) {
                if ( client.isServerRunning() ) {
                    client.stopWatchListener();
                }
            }
        } catch(Exception e) {e.printStackTrace(); }
        Stage stage = (Stage) backToMenu.getScene().getWindow();
        stage.close();
    }

    /* Az alkalmazas szerver/kliens modban valo hasznalatat teszi lehetove.
     * Erre azert van szukseg, mivel 2 eszkoz hasznalata eseten egyik szerver, masik
     * kliens szerepet fog betolteni. Lenyegeben figyeljuk hogy milyen modban vagyunk, es ez
     * alapjan allitgatjuk az ablakban talalhato szovegeket, aktivaljuk/deaktivaljuk
     * a szervert es klienst indito start-stop illetve checkFailedFiles gombokat */
    @FXML private void changeAppMode(){
        if( "Client Mode".equals( appMode.getText() ) ){
            appMode.setText("Server Mode");
            appModeText1.setText("Device 1 : Choose the ORIGIN path to listen a directory : ");
            appModeText2.setText("Device 1 : Event changes in the selected directory : ");
            startStopServer.setDisable(false);
            startStopClient.setDisable(true);
            checkFailedFiles.setDisable(false);
            listView.setItems( null );
            UILogger( "Changed to \"Server Mode\" ");
        } else if( "Server Mode".equals( appMode.getText() ) ){
            appMode.setText("Client Mode");
            appModeText1.setText("Device 2 : Choose the TARGET path: (where the files will be transfered)");
            appModeText2.setText("Device 2 : Transfered files : ");
            startStopServer.setDisable(true);
            startStopClient.setDisable(false);
            checkFailedFiles.setDisable(true);
            listView.setItems( null );
            UILogger( "Changed to \"Client Mode\" ");
        }
    }
}
