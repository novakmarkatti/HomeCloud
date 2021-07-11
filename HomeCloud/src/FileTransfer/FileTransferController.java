package FileTransfer;

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

public class FileTransferController {

    @FXML private Button startStopServer;
    @FXML private Button startStopClient;
    @FXML private Button backToMenu;
    @FXML private TextField pathTextField;
    @FXML private Text statusMsg;
    @FXML private Text appMode;
    @FXML private Text appModeText1;
    @FXML private Text appModeText2;
    @FXML private ListView<String> listView = new ListView<>();
    private Server server = null;
    private Client client = null;

    /* A UI-t allitgatjuk vele a szerver es kliens oldalrol abban az esetben :
    *  - Ha a szerver elindult/megallt, kliens csatlakozott/lelepett ,
    * illetve az atkuldott fajlok listaban valo megjelenitesenek okabol.
    *  - Ha a kliens elindult/megallt, csatlakozott a szerverhez,
    * illetve a kuldheto fajlok listaban valo megjelenitesenek okabol. */
    private FileTransferUISetter setter = new FileTransferUISetter() {
        // szerver oldal
        @Override public void serverStarted() {
            UILogger("Server started");
        }
        @Override public void serverStopped() {
            UILogger("Server stopped");
            Platform.runLater(() -> {
                startStopServer.setText("Start server");
                if( "Server Mode".equals( appMode.getText() ) ) {
                    startStopServer.setDisable(false);
                }
            });
        }
        @Override public void clientConnected() {
            UILogger("Client connected");
        }
        @Override public void clientDisconnected() {
            UILogger("Client disconnected");
        }
        @Override public void setListViewToServer( String elem) {
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

        // kliens oldal
        @Override public void setListViewToClient(ObservableList elements) {
            if( "Client Mode".equals( appMode.getText() ) ) {
                Platform.runLater(() -> {
                    listView.setItems( elements );
                });
            }
        }
        @Override public void clientStarted() {
            UILogger("Client started");
        }
        @Override public void clientStopped() {
            UILogger("Client stopped");
            Platform.runLater(() -> {
                startStopClient.setText("Start client");
                if( "Client Mode".equals( appMode.getText() ) ) {
                    startStopClient.setDisable(false);
                }
            });
        }
        @Override public void connectedToServer() {
            UILogger("Connected to server");
        }
    };

    /* A szerver elinditasaert es megallitasaert felel. Lenyegeben azt figyeljuk milyen szoveg
    * szerepel a gombon. Ha "start" akkor elobbszor megnezzuk hogy a felhasznalo valasztott-e
    * mar cel utvonalat, mert csak ebben az esetben indithato el a szerver. Utanna ha meg nem
    * hoztunk letre szervert , akkor letrehozzuk, es elinditjuk. A szerver eltarolja a legutoljara
    * megadott eleresi utvonalat, ezert ha egy ujabbat vel felfedezni, akkor az ujabbat allitja be,
    * es oda fogja a fajlokat eltarolni. "Stop" eseten megallitjuk a szervert, es a gombot hogy
    * ne lehessen sokszor nyomogatni ezert deaktivaljuk. Miutan a szerver veglegesen lealt, akkor
    * lesz ujra elerheto es elindithato a gomb.*/
    @FXML private void handleStartStopServer(){
        try {
            if( "Start server".equals( startStopServer.getText() ) ){
                if( !pathTextField.getText().equals("") ) {
                    String temp = pathTextField.getText();
                    String temporal = temp.replace("\\" , "/");
                    if (server == null) {
                        server = new Server(setter, temporal );
                    }
                    if (!temporal.equals(server.getTargetDirectory())) {
                        server.setTargetDirectory(temporal);
                    }
                    server.startServer();
                    startStopServer.setText("Stop server");
                } else {
                    UILogger("Choosing of the directory failed.");
                }
            } else if( "Stop server".equals( startStopServer.getText() ) ){
                server.stopServer();
                startStopServer.setDisable(true);
            }
        } catch(Exception e){
            System.err.println("ERROR: FileTransfer> handleStartStopServer");
            e.printStackTrace();
        }
    }

    /* A kliens elinditasaert es megallitasaert felel. Szerverhez hasonloan azt figyeljuk milyen szoveg
     * szerepel a gombon. Ha "start" akkor elobbszor megnezzuk hogy a felhasznalo valasztott-e
     * mar eredet utvonalat, ahonnan a fajlokat szeretne szallitani, mert csak ebben az esetben indithato
     * el a kliens. Minden alkalommal uj klienst hozunk letre, amit elinditunk. "Stop" eseten megallitjuk
     * a klienst, es a gombot hogy ne lehessen sokszor nyomogatni ezert deaktivaljuk.
     * Miutan a kliens veglegesen lealt, akkor lesz ujra elerheto es elindithato a gomb.*/
    @FXML private void handleStartStopClient(){
        try {
            if( "Start client".equals( startStopClient.getText() ) ){
                if( !pathTextField.getText().equals("") ) {
                    String temp = pathTextField.getText();
                    String temporal = temp.replace("\\" , "/");
                    client = new Client( setter,  temporal , FileTransferUI.parameter );
                    client.startClient();
                    startStopClient.setText("Stop client");
                } else {
                    UILogger("Choosing of the directory failed.");
                }
            } else if( "Stop client".equals( startStopClient.getText() ) ){
                client.stopClient();
                startStopClient.setDisable(true);
            }
        } catch(Exception e){
            System.err.println("ERROR: FileTransfer> handleStartStopClient");
            e.printStackTrace();
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
    * megnezzuk, hogy a szerver es kliens letezik es fut-e, amennyiben igen, megallitjuk oket. */
    @FXML private void handleBackToMenu(){
        try {
            if(server != null) {
                if (server.isServerRunning()) {
                    server.stopServer();
                }
            }
            if(client != null) {
                if (client.isClientRunning()) {
                    client.stopClient();
                }
            }
        } catch(Exception e) { e.printStackTrace(); }
        Stage stage = (Stage) backToMenu.getScene().getWindow();
        stage.close();
    }

    /* Az alkalmazas szerver/kliens modban valo hasznalatat teszi lehetove.
    * Erre azert van szukseg, mivel 2 eszkoz hasznalata eseten egyik szerver, masik
    * kliens szerepet fog betolteni. Lenyegeben figyeljuk hogy milyen modban vagyunk, es ez
    * alapjan allitgatjuk az ablakban talalhato szovegeket, aktivaljuk/deaktivaljuk
    * a szervert es klienst indito a start-stop gombokat */
    @FXML private void changeAppMode(){
        if( "Server Mode".equals( appMode.getText() ) ){
            appMode.setText("Client Mode");
            appModeText1.setText("Device 1 : Choose the ORIGIN path: (from which you will transfer the files)");
            appModeText2.setText("Device 1 : Existing files in the selected directory : ");
            startStopServer.setDisable(true);
            startStopClient.setDisable(false);
            listView.setItems( null );
            UILogger( "Changed to \"Client Mode\" ");
        } else if( "Client Mode".equals( appMode.getText() ) ){
            appMode.setText("Server Mode");
            appModeText1.setText("Device 2 : Choose the TARGET path: (where the files will be transfered)");
            appModeText2.setText("Device 2 : Transfered files : ");
            startStopServer.setDisable(false);
            startStopClient.setDisable(true);
            listView.setItems( null );
            UILogger( "Changed to \"Server Mode\" ");
        }
    }

}
