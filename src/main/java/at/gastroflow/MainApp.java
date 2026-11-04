package at.gastroflow;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/at/gastroflow/views/service.fxml"
                )
        );

        Scene scene = new Scene(loader.load(), 1000, 700);

        stage.setTitle("GastroFlow – Service");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
