package at.gastroflow.frontend.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.io.IOException;

// Wechselt im Hauptfenster zwischen den Ansichten (Login, Service, Küche, Leitung)
public class Navigation {

    public static final String LOGIN = "login";
    public static final String SERVICE = "service";
    public static final String KITCHEN = "kitchen";
    public static final String MANAGEMENT = "management";

    private static final String VIEW_FOLDER = "/at/gastroflow/frontend/views/";

    public static void show(Node currentNode, String view, String title) {

        Stage stage = (Stage) currentNode.getScene().getWindow();

        try {
            FXMLLoader loader = new FXMLLoader(
                    Navigation.class.getResource(VIEW_FOLDER + view + ".fxml")
            );
            Parent root = loader.load();

            // Fenstergröße beim Wechseln beibehalten
            Scene scene = new Scene(
                    root,
                    stage.getScene().getWidth(),
                    stage.getScene().getHeight()
            );

            stage.setScene(scene);
            stage.setTitle("GastroFlow – " + title);

        } catch (IOException | IllegalStateException e) {

            Alert alert = new Alert(
                    Alert.AlertType.ERROR,
                    "Die Ansicht \"" + title + "\" konnte nicht geladen werden.\n\n" + e.getMessage(),
                    ButtonType.OK
            );
            alert.setHeaderText("Fehler beim Öffnen");
            alert.showAndWait();
        }
    }

    public static void toLogin(Node currentNode) {
        show(currentNode, LOGIN, "Anmeldung");
    }
}
