package at.gastroflow.frontend.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;

public class KitchenController {

    @FXML
    private FlowPane ordersPane;

    @FXML
    private Label newCount;

    @FXML
    private Label preparingCount;

    @FXML
    private Label finishedCount;

    // Wird beim Öffnen der Küchenansicht ausgeführt
    @FXML
    private void initialize() {
        showDemoOrders();
    }

    // Zeigt feste Beispielbestellungen an
    private void showDemoOrders() {

        ordersPane.getChildren().clear();

        // Zähler für die Beispielbestellungen
        newCount.setText("1");
        preparingCount.setText("1");
        finishedCount.setText("2");

        // Bestellung 1 - Neu
        createOrderCard(
                "1024",
                "5",
                "2× Gastro Burger\n1× Süßkartoffel-Pommes",
                "Burger ohne Zwiebeln",
                "Neu"
        );

        // Bestellung 2 - In Zubereitung
        createOrderCard(
                "1025",
                "2",
                "1× Trüffelpasta\n1× Caesar Salad",
                "",
                "In Zubereitung"
        );

        // Bestellung 3 - Fertig
        createOrderCard(
                "1023",
                "4",
                "2× Hühnerschnitzel\n1× Kürbiscremesuppe",
                "Suppe zuerst",
                "Fertig"
        );
    }

    // Erstellt eine Bestellkarte für die Oberfläche
    private void createOrderCard(
            String number,
            String table,
            String products,
            String note,
            String statusText
    ) {

        VBox card = new VBox(15);
        card.getStyleClass().add("order-card");
        card.setPrefWidth(330);

        // Bestellnummer und Tisch
        Label title = new Label(
                "Bestellung #" + number + " · Tisch " + table
        );
        title.getStyleClass().add("order-title");

        // Statusanzeige
        Label status = new Label(statusText);
        status.getStyleClass().add("status-label");

        // Farbe je nach Status
        if (statusText.equals("Neu")) {
            status.getStyleClass().add("status-new");

        } else if (statusText.equals("In Zubereitung")) {
            status.getStyleClass().add("status-preparing");

        } else {
            status.getStyleClass().add("status-finished");
        }

        HBox heading = new HBox(12, title, status);
        heading.setStyle("-fx-alignment: center-left;");

        // Produkte anzeigen
        Label items = new Label(products);
        items.getStyleClass().add("order-items");
        items.setWrapText(true);

        card.getChildren().addAll(heading, items);

        // Sonderwünsche anzeigen
        if (!note.isBlank()) {

            Label noteLabel = new Label(
                    "Sonderwunsch: " + note
            );

            noteLabel.getStyleClass().add("order-note");
            noteLabel.setWrapText(true);

            card.getChildren().add(noteLabel);
        }

        // Button-Beschriftung je nach Status
        String buttonText;

        if (statusText.equals("Neu")) {
            buttonText = "In Zubereitung";

        } else if (statusText.equals("In Zubereitung")) {
            buttonText = "Fertig";

        } else {
            buttonText = "Abholbereit";
        }

        // Button erstellen
        Button button = new Button(buttonText);

        button.getStyleClass().add("primary-button");
        button.setMaxWidth(Double.MAX_VALUE);

        // Nur Frontend-Demo, keine Geschäftslogik
        button.setOnAction(event -> {
            button.setText("Aktion ausgewählt (Demo)");
            button.setDisable(true);
        });

        card.getChildren().add(button);

        // Bestellkarte zur Oberfläche hinzufügen
        ordersPane.getChildren().add(card);
    }

    // Zurück zur Anmeldung
    @FXML
    private void handleLogout() throws IOException {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/at/gastroflow/frontend/views/login.fxml"
                )
        );

        Stage stage = (Stage)
                ordersPane.getScene().getWindow();

        Scene scene = new Scene(
                loader.load(),
                stage.getScene().getWidth(),
                stage.getScene().getHeight()
        );

        stage.setScene(scene);
        stage.setTitle("GastroFlow – Anmeldung");
    }
}
