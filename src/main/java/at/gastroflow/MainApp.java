package at.gastroflow;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;

import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/at/gastroflow/frontend/views/login.fxml"
                )
        );

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(loader.load(),
                Math.min(1250, screen.getWidth() * 0.92),
                Math.min(850, screen.getHeight() * 0.90));

        stage.setTitle("GastroFlow – Anmeldung");
        stage.setScene(scene);
        stage.setX(screen.getMinX() + (screen.getWidth() - scene.getWidth()) / 2);
        stage.setY(screen.getMinY() + (screen.getHeight() - scene.getHeight()) / 2);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
