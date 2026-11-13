package at.gastroflow.frontend.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private ComboBox<String> roleBox;

    @FXML
    private Label errorLabel;

    @FXML
    public void initialize() {
        roleBox.getItems().addAll(
                "Service",
                "Küche",
                "Leitung"
        );

        // Fehler ausblenden, sobald ein Bereich gewählt wurde
        roleBox.valueProperty().addListener((obs, oldValue, newValue) -> hideError());
    }

    @FXML
    private void handleLogin() {

        String selectedRole = roleBox.getValue();

        if (selectedRole == null) {
            showError("Bitte einen Arbeitsbereich auswählen.");
            return;
        }

        if (selectedRole.equals("Service")) {
            Navigation.show(roleBox, Navigation.SERVICE, "Service");

        } else if (selectedRole.equals("Küche")) {
            Navigation.show(roleBox, Navigation.KITCHEN, "Küche");

        } else if (selectedRole.equals("Leitung")) {
            Navigation.show(roleBox, Navigation.MANAGEMENT, "Leitung");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);

        if (!roleBox.getStyleClass().contains("input-error")) {
            roleBox.getStyleClass().add("input-error");
        }
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        roleBox.getStyleClass().remove("input-error");
    }
}
