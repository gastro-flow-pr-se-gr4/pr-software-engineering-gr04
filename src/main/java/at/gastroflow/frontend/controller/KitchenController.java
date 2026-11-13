package at.gastroflow.frontend.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderStatus;
import at.gastroflow.backend.service.DemoData;
import at.gastroflow.backend.service.OrderService;

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
        DemoData.load();
        showOrders();
    }

    // Zeigt alle offenen Bestellungen aus dem gemeinsamen Bestellsystem an (älteste zuerst)
    private void showOrders() {

        ordersPane.getChildren().clear();

        List<Order> openOrders = OrderService.getOpenOrders().stream()
                .sorted(Comparator.comparing(Order::getCreatedAt))
                .toList();

        // Zähler
        newCount.setText(String.valueOf(count(openOrders, OrderStatus.NEU)));
        preparingCount.setText(String.valueOf(count(openOrders, OrderStatus.IN_ZUBEREITUNG)));
        finishedCount.setText(String.valueOf(count(openOrders, OrderStatus.FERTIG)));

        if (openOrders.isEmpty()) {
            Label empty = new Label("Keine offenen Bestellungen.");
            empty.getStyleClass().add("order-items");
            ordersPane.getChildren().add(empty);
        }

        for (Order order : openOrders) {
            createOrderCard(order);
        }
    }

    private long count(List<Order> orders, OrderStatus status) {
        return orders.stream().filter(order -> order.getStatus() == status).count();
    }

    // Erstellt eine Bestellkarte für die Oberfläche
    private void createOrderCard(Order order) {

        String number = String.valueOf(order.getNumber());
        String table = String.valueOf(order.getTable());
        String products = order.getItems().stream()
                .map(item -> item.getQuantity() + "× " + item.getProductName())
                .collect(Collectors.joining("\n"));
        String note = order.getNote();
        String statusText = order.getStatus().toString();


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

        // Status im gemeinsamen Bestellsystem weiterschalten
        button.setOnAction(event -> {
            OrderStatus next = switch (order.getStatus()) {
                case NEU -> OrderStatus.IN_ZUBEREITUNG;
                case IN_ZUBEREITUNG -> OrderStatus.FERTIG;
                default -> OrderStatus.ABGESCHLOSSEN; // abgeholt
            };
            OrderService.changeStatus(order, next);
            showOrders();
        });

        card.getChildren().add(button);

        // Bestellkarte zur Oberfläche hinzufügen
        ordersPane.getChildren().add(card);
    }

    // Zurück zur Anmeldung
    @FXML
    private void handleLogout() {
        Navigation.toLogin(ordersPane);
    }
}
