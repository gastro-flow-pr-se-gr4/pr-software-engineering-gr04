package at.gastroflow.frontend.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private ComboBox<String> roleBox;

    @FXML
    public void initialize() {
        roleBox.getItems().addAll(
                "Service",
                "Küche",
                "Leitung"
        );
    }

    @FXML
    private void handleLogin() throws IOException {

        if ("Service".equals(roleBox.getValue())) {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/at/gastroflow/frontend/views/service.fxml"
                    )
            );

            Stage stage = (Stage) roleBox.getScene().getWindow();

            Scene scene = new Scene(
                    loader.load(),
                    stage.getScene().getWidth(),
                    stage.getScene().getHeight()
            );

            stage.setScene(scene);
            stage.setTitle("GastroFlow – Service");
        }
    }
}
