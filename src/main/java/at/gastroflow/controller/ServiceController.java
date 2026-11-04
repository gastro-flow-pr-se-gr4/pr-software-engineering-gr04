package at.gastroflow.controller;

import at.gastroflow.model.Order;
import at.gastroflow.model.OrderItem;
import at.gastroflow.model.Product;
import at.gastroflow.service.OrderService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import java.util.List;

public class ServiceController {
    @FXML private FlowPane tableButtons;
    @FXML private VBox orderArea;
    @FXML private Label tableLabel;
    @FXML private Label totalLabel;
    @FXML private ListView<Product> menuList;
    @FXML private ListView<OrderItem> orderList;
    @FXML private TextArea noteArea;
    private Order currentOrder;

    private final List<Product> products = List.of(
            new Product("Pizza Margherita", 10.90, "Speisen"),
            new Product("Pizza Salami", 12.90, "Speisen"),
            new Product("Pasta Pomodoro", 11.50, "Speisen"),
            new Product("Caesar Salad", 9.90, "Speisen"),
            new Product("Wasser", 2.90, "Getränke"),
            new Product("Cola", 3.50, "Getränke"),
            new Product("Apfelsaft", 3.20, "Getränke"),
            new Product("Espresso", 2.60, "Getränke")
    );

    @FXML private void initialize() {
        menuList.setItems(FXCollections.observableArrayList(products));
        orderArea.setDisable(true);
        for (int number = 1; number <= 12; number++) {
            final int table = number;
            Button button = new Button("Tisch " + number);
            button.getStyleClass().add("table-button");
            button.setOnAction(event -> selectTable(table));
            tableButtons.getChildren().add(button);
        }
    }

    private void selectTable(int table) {
        currentOrder = new Order(table);
        tableLabel.setText("Bestellung – Tisch " + table);
        noteArea.clear();
        orderArea.setDisable(false);
        refreshOrder();
    }

    @FXML private void addProduct() {
        Product selected = menuList.getSelectionModel().getSelectedItem();
        if (selected == null || currentOrder == null) {
            showMessage("Bitte zuerst einen Tisch und ein Produkt auswählen.");
            return;
        }
        currentOrder.addProduct(selected);
        refreshOrder();
    }

    @FXML private void increaseQuantity() {
        OrderItem selected = orderList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selected.setQuantity(selected.getQuantity() + 1);
            refreshOrder();
        }
    }

    @FXML private void decreaseQuantity() {
        OrderItem selected = orderList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (selected.getQuantity() <= 1) currentOrder.getItems().remove(selected);
            else selected.setQuantity(selected.getQuantity() - 1);
            refreshOrder();
        }
    }

    @FXML private void sendOrder() {
        if (currentOrder == null || currentOrder.getItems().isEmpty()) {
            showMessage("Bitte zuerst Produkte hinzufügen.");
            return;
        }
        currentOrder.setNote(noteArea.getText().trim());
        OrderService.send(currentOrder);
        showMessage("Bestellung für Tisch " + currentOrder.getTableNumber() + " wurde gesendet (Demo)." );
        currentOrder = null;
        tableLabel.setText("Bitte Tisch auswählen");
        noteArea.clear();
        orderArea.setDisable(true);
        refreshOrder();
    }

    private void refreshOrder() {
        orderList.setItems(FXCollections.observableArrayList(
                currentOrder == null ? List.of() : currentOrder.getItems()
        ));
        totalLabel.setText(String.format("Gesamt: %.2f €", currentOrder == null ? 0 : currentOrder.getTotal()));
    }

    private void showMessage(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}

