package MainMenu;

import FileTransfer.FileTransferUI;
import NetworkDiscovery.HomeCloudListener;
import NetworkDiscovery.HomeCloudNetworking;
import WatchDirectory.WatchDirectoryUI;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class MainMenuController {

    @FXML private Label lblAppMode;
    @FXML private Button exitBtn;
    @FXML private Button startStopServer;
    @FXML private Button startStopDiscovery;
    @FXML private Text statusMessage = new Text();
    @FXML private Button startWatchService;
    @FXML private Button startFileTransfer;
    private HomeCloudNetworking homeCloud = null;
    private String addressDevice1   = null;
    private String addressDevice2   = null;

    /* A UI-t allitgatjuk vele a szerver es kliens oldalrol abban az esetben :
     *  - Ha a szerver elindult/megallt, kliens csatlakozott vagy letrejott a kapcsolat(eltarolta az IP cimet)
     *  - Ha a kliens elindult/megallt, felfedezte a szervert (eltarolta az IP cimet) */
    private HomeCloudListener listener = new HomeCloudListener() {
        // udp szerver
        @Override public void serverStarted() {
            UILogger("Server started");
        }
        @Override public void serverStopped() {
            UILogger("Server stopped");
            Platform.runLater(() -> {
                startStopServer.setText("Start server");
                if( "Server Mode".equals( lblAppMode.getText() ) ) {
                    startStopServer.setDisable(false);
                }
            });
        }
        @Override public void clientConnected() {
            UILogger("Client connected");
        }
        @Override public void connectionEstablished( String address) {
            UILogger("Connection established");
            addressDevice2 = address;
            startWatchService.setDisable(false);
            startFileTransfer.setDisable(false);
        }
        // Udp kliens
        @Override public void discoveryStarted() {
            UILogger("Discovery started");
        }
        @Override public void discoveryStopped() {
            UILogger("Discovery stopped");
            Platform.runLater(() -> {
                startStopDiscovery.setText("Start discovery");
                if( "Client Mode".equals( lblAppMode.getText() ) ) {
                    startStopDiscovery.setDisable(false);
                }

            });
        }
        @Override public void discoveredServer(String address) {
            UILogger("Server discovered");
            addressDevice1 = address;
            if( homeCloud.isDiscoveryRunning() ){
                homeCloud.stopDiscovery();
            }
            startWatchService.setDisable(false);
            startFileTransfer.setDisable(false);
        }
    };

    /* Az alkalmazas szerver/kliens modban valo hasznalatat teszi lehetove.
     * Erre azert van szukseg, mivel 2 eszkoz hasznalata eseten egyik szerver, masik
     * kliens szerepet fog betolteni. Lenyegeben figyeljuk hogy milyen modban vagyunk, es ez
     * alapjan aktivaljuk/deaktivaljuk a start-stop gombokat */
    @FXML private void changeAppMode(){
        if( "Server Mode".equals( lblAppMode.getText() ) ){
            lblAppMode.setText("Client Mode");
            startStopServer.setDisable(true);
            startStopDiscovery.setDisable(false);
        } else if( "Client Mode".equals( lblAppMode.getText() ) ){
            lblAppMode.setText("Server Mode");
            startStopServer.setDisable(false);
            startStopDiscovery.setDisable(true);
        }
    }

    /* A NetworkDiscovery UDP szerver elinditasaert es megallitasaert felel. Lenyegeben azt figyeljuk
     * milyen szoveg szerepel a gombon. Ha "start" akkor eloszor megnezzuk hogy ha meg nem hoztunk letre
     * szervert , akkor letrehozzuk, es elinditjuk. "Stop" eseten megallitjuk a szervert, es a gombot hogy
     * ne lehessen sokszor nyomogatni ezert deaktivaljuk. Miutan a szerver veglegesen lealt, akkor
     * lesz ujra elerheto es elindithato a gomb.*/
    @FXML private void handleStartStopServer(){
        try {
            if( "Start server".equals( startStopServer.getText() ) ){
                if (homeCloud == null){
                    homeCloud = new HomeCloudNetworking(  "HomeCloud", "224.0.0.1", 4446, listener);
                }
                homeCloud.startServer();
                startStopServer.setText("Stop server");
             } else if( "Stop server".equals( startStopServer.getText() ) ){
                homeCloud.stopServer();
                startStopServer.setDisable(true);
            }
        } catch(Exception e){
            System.err.println("ERROR: MainMenuController> handleStartStopServer");
            e.printStackTrace();
        }
    }

    /* A NetworkDiscovery UDP kliens elinditasaert es megallitasaert felel. Szerverhez hasonloan azt figyeljuk
     * milyen szoveg szerepel a gombon. Ha "start" akkor eloszor megnezzuk hogy ha meg nem hoztunk letre
     * kliensr , akkor letrehozzuk, es elinditjuk. "Stop" eseten megallitjuk
     * a klienst, es a gombot hogy ne lehessen sokszor nyomogatni ezert deaktivaljuk.
     * Miutan a kliens veglegesen lealt, akkor lesz ujra elerheto es elindithato a gomb.*/
    @FXML private void handleStartStopDiscovery(){
        try {
            if( "Start discovery".equals( startStopDiscovery.getText() ) ){
                if (homeCloud == null){
                    homeCloud = new HomeCloudNetworking( "HomeCloud","224.0.0.1", 4446, listener);
                }
                homeCloud.startDiscovery();
                startStopDiscovery.setText("Stop discovery");
            } else if( "Stop discovery".equals( startStopDiscovery.getText() ) ){
                homeCloud.stopDiscovery();
                startStopDiscovery.setDisable(true);
            }
        } catch(Exception e){
            System.err.println("ERROR: MainMenuController> handleStartStopDiscovery");
            e.printStackTrace();
        }
    }

    /* A UILogger valositja meg , hogy a Statusban mindig latszodjon mi tortenik a programban */
    private void UILogger(String msg){
        Platform.runLater(() -> {
            synchronized(statusMessage){
                statusMessage.setText(msg);
                System.out.println("UILogger> " + msg);
            }
        });
    }

    // Megjelenitjuk vele a Directory Watcher felugro ablakot
    @FXML private void handleWatchDirectory(){
        try {
            WatchDirectoryUI watchDirectory = new WatchDirectoryUI();
            watchDirectory.display( checkAddresses() );
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Megjelenitjuk vele a FileTransfer felugro ablakot
    @FXML private void handleFileTransfer(){
        try {
            FileTransferUI fileTransferUI = new FileTransferUI();
            fileTransferUI.display( checkAddresses() );
        } catch (Exception e) { e.printStackTrace(); }
    }

    /* Itt ellenorizzuk le hogy a NetworkDiscovery soran melyik IP cim allitodott be.
    * Mivel 2 eszkozunk van, igy 1 eszkozon csak a masik eszkoz IP cimet taroljuk el,
    * igy egyik cim null marad. A tenyleges ip cimmel terunk vissza*/
    private String checkAddresses(){
        if( addressDevice1 == null )        return addressDevice2;
        else if( addressDevice2 == null )   return addressDevice1;
        else                                return addressDevice1;
    }

    // Megjelenitjuk vele a Help felugro ablakot
    @FXML private void handleHelpBtn(){
        try {
            Help help = new Help();
            help.display();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /* Ezzel zarjuk be az alkalmazast. Bezaras elott megnezzuk, hogy a NetworkDiscovery
    * szerver es kliens letezik es fut-e, amennyiben igen, megallitjuk oket. */
    @FXML private void handleExitBtn(){
        try {
            if(homeCloud != null) {
                if (homeCloud.isServerRunning()) {
                    homeCloud.stopServer();
                }
                if (homeCloud.isDiscoveryRunning()) {
                    homeCloud.stopDiscovery();
                }
            }
        } catch(Exception e) {e.printStackTrace(); }
        Stage stage = (Stage) exitBtn.getScene().getWindow();
        stage.close();
    }
}
