package FileTransfer;

import MainMenu.MainMenuUI;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class FileTransferUI {

    private Parent root;
    private double xOffset, yOffset;
    public static String parameter;

    /* A FileTransfer ablak betolteseert felel. Parameterkent egy ip-cimet kap,
    * ami a network discovery soran felfedezett masik gepet jelzi. A kliens ezt
    * a statikus parameterbol tudja elerni. Lenyegeben letiltjuk a default
    * alkalmazas fejlecet, betoltunk egy ikont es magat az fxml fajlt ami tartalmazza
    * az ablak kinezetet, majd lehetove tesszuk az ablak mozgatasat az eger ablakban
    * valo lenyomva tartasakor. Vegezetul pedig amig az ablak aktiv, addig lehetetlenne
    * tesszuk a fomenu elereset. */
    public void display( String IP) throws Exception{
        this.parameter = IP;
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.getIcons().add(new Image( MainMenuUI.class.getResourceAsStream("/images/transfer.png")));

        FXMLLoader loader = new FXMLLoader( getClass().getResource( "/FileTransfer/FileTransfer.fxml" ) );
        root = loader.load();
        root.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                xOffset = mouseEvent.getSceneX();
                yOffset = mouseEvent.getSceneY();
            }
        });
        root.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                stage.setX( mouseEvent.getScreenX() - xOffset);
                stage.setY( mouseEvent.getScreenY() - yOffset);
            }
        });

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.showAndWait();
    }

}
