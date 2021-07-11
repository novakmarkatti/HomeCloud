package MainMenu;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MainMenuUI extends Application {
    private Parent root;
    private double xOffset, yOffset;

    public static void main(String[] args) { launch(args); }

    /* A fomenu betolteseert felel. Letiltjuk a default alkalmazas fejlecet, betoltunk
     * egy ikont es magat az fxml fajlt ami tartalmazza az ablak kinezetet, majd lehetove
     * tesszuk az ablak mozgatasat az eger ablakban valo lenyomva tartasakor. */
    @Override
    public void start(Stage primaryStage) throws Exception{
        root = FXMLLoader.load(getClass().getResource("MainMenu.fxml"));
        Scene scene = new Scene(root);
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.getIcons().add(new Image( MainMenuUI.class.getResourceAsStream("/images/cloud.png")));
        scene.setFill(Color.TRANSPARENT);
        primaryStage.setScene(scene);
        primaryStage.show();

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
                primaryStage.setX( mouseEvent.getScreenX() - xOffset);
                primaryStage.setY( mouseEvent.getScreenY() - yOffset);
            }
        });
    }

}
