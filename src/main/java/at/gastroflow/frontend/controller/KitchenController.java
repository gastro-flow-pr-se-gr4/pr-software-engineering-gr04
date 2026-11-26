package at.gastroflow.frontend.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;

import at.gastroflow.backend.dto.OrderDTO;
import at.gastroflow.backend.dto.OrderItemDTO;
import at.gastroflow.backend.model.OrderStatus;
import at.gastroflow.frontend.api.ApiException;
import at.gastroflow.frontend.api.OrderClient;

public class KitchenController {

    @FXML
    private VBox newColumn;

    @FXML
    private VBox preparingColumn;

    @FXML
    private VBox finishedColumn;

    @FXML
    private Label newCount;

    @FXML
    private Label preparingCount;

    @FXML
    private Label finishedCount;

    // Wird beim Öffnen der Küchenansicht ausgeführt
    @FXML
    private void initialize() {
        showOrders();
    }

    // Zeigt alle offenen Bestellungen (von der REST-API) nach Status sortiert in drei Spalten
    private void showOrders() {
        List<OrderDTO> open;
        String emptyHint = null;
        try {
            open = OrderClient.openOrders();
        } catch (ApiException e) {
            open = List.of();
            emptyHint = "Bestellungen nicht verfügbar: " + e.getMessage();
        }
        fillColumn(newColumn, newCount, open, OrderStatus.NEU, emptyHint != null ? emptyHint : "Keine neuen Bestellungen.");
        fillColumn(preparingColumn, preparingCount, open, OrderStatus.IN_ZUBEREITUNG, "Nichts in Zubereitung.");
        fillColumn(finishedColumn, finishedCount, open, OrderStatus.FERTIG, "Nichts zur Abholung bereit.");
    }

    // Füllt eine Spalte mit den Bestellungen eines Status (älteste zuerst)
    private void fillColumn(VBox column, Label counter, List<OrderDTO> open, OrderStatus status, String emptyText) {

        column.getChildren().clear();

        List<OrderDTO> orders = open.stream()
                .filter(order -> order.status() == status)
                .sorted(Comparator.comparing(OrderDTO::createdAt))
                .toList();

        counter.setText(String.valueOf(orders.size()));

        if (orders.isEmpty()) {
            Label empty = new Label(emptyText);
            empty.getStyleClass().add("empty-column");
            empty.setWrapText(true);
            column.getChildren().add(empty);
        }

        for (OrderDTO order : orders) {
            column.getChildren().add(createOrderCard(order));
        }
    }

    // Erstellt eine Bestellkarte für die Oberfläche
    private VBox createOrderCard(OrderDTO order) {

        String number = String.valueOf(order.number());
        String table = String.valueOf(order.table());
        String note = order.note();

        VBox card = new VBox(15);
        card.getStyleClass().add("order-card");

        // Bestellnummer und Tisch
        Label title = new Label(
                "Bestellung #" + number + " · Tisch " + table
        );
        title.getStyleClass().add("order-title");

        title.setWrapText(true);

        // Produkte anzeigen (eine Zeile pro Position, damit nichts abgeschnitten wird)
        VBox items = new VBox(5);
        for (OrderItemDTO item : order.items()) {
            Label line = new Label(item.quantity() + "× " + item.productName());
            line.getStyleClass().add("order-items");
            line.setWrapText(true);
            line.setMinHeight(Region.USE_PREF_SIZE);
            items.getChildren().add(line);
        }

        card.getChildren().addAll(title, items);

        // Sonderwünsche anzeigen (einer pro Zeile)
        if (!note.isBlank()) {

            Label noteLabel = new Label("Sonderwunsch\n" + note);

            noteLabel.getStyleClass().add("order-note");
            noteLabel.setWrapText(true);
            noteLabel.setMinHeight(Region.USE_PREF_SIZE);

            card.getChildren().add(noteLabel);
        }

        // Nächster Schritt je nach Status. Fertige Bestellungen bringt der Service zum Tisch.
        switch (order.status()) {
            case NEU -> card.getChildren().add(
                    actionButton("Zubereitung starten", () -> OrderClient.changeStatus(order.id(), OrderStatus.IN_ZUBEREITUNG)));
            case IN_ZUBEREITUNG -> card.getChildren().add(
                    actionButton("Fertig", () -> OrderClient.changeStatus(order.id(), OrderStatus.FERTIG)));
            default -> {
                Label waiting = new Label("Wartet auf den Service");
                waiting.getStyleClass().add("ready-hint");
                card.getChildren().add(waiting);
            }
        }

        return card;
    }

    private Button actionButton(String text, Runnable action) {

        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setWrapText(true);
        button.setMinHeight(Region.USE_PREF_SIZE);

        button.setOnAction(event -> {
            try {
                action.run();
            } catch (IllegalStateException e) {
                new Alert(Alert.AlertType.WARNING, e.getMessage(), ButtonType.OK).showAndWait();
            }
            showOrders();
        });
        return button;
    }

    // Zurück zur Anmeldung
    @FXML
    private void handleLogout() {
        Navigation.toLogin(newColumn);
    }
}
