
package at.gastroflow.controller;

import at.gastroflow.model.*;
import at.gastroflow.service.OrderService;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

public class ServiceController {

    @FXML private FlowPane tablesPane, productsPane;
    @FXML private VBox currentItems, paidItems;
    @FXML private Label currentTitle, totalLabel, paidTitle;
    @FXML private Label paidTotal, counterLabel;
    @FXML private TextArea noteArea;
    @FXML private Button discardButton, sendButton, payButton;
    @FXML private HBox categoryTabs;
    @FXML private ScrollPane pageScroll;
    @FXML private FlowPane contentLayout;
    @FXML private VBox orderPanel, rightPanel;

    private final Map<Integer, Order> drafts = new HashMap<>();
    private final Map<Integer, List<Order>> sentByTable = new HashMap<>();

    // Aktuell ausgewähltes Gericht
    private OrderItem selectedItem;

    private final List<Product> products = List.of(

            // Tagesgericht
            new Product("Kürbiscremesuppe", 4.50, "Tagesgericht"),
            new Product("Hühnerschnitzel mit Petersilienkartoffeln",
                    13.90, "Tagesgericht"),
            new Product("Apfelstrudel", 5.50, "Tagesgericht"),

            // Speisen
            new Product("Gastro Burger", 14.90, "Speisen"),
            new Product("Trüffelpasta", 16.50, "Speisen"),
            new Product("Caesar Salad", 11.80, "Speisen"),
            new Product("Süßkartoffel-Pommes", 5.90, "Speisen"),

            // Getränke
            new Product("Cola 0,33 l", 3.80, "Getränke"),
            new Product("Espresso", 2.90, "Getränke"),
            new Product("Wasser", 2.90, "Getränke"),
            new Product("Apfelsaft", 3.20, "Getränke"),

            // Dessert
            new Product("Schokokuchen", 6.50, "Dessert")
    );

    private int selectedTable = 4;
    private String category = "Tagesgericht";

    @FXML
    private void initialize() {

        pageScroll.viewportBoundsProperty().addListener(
                (obs, oldBounds, bounds) ->
                        updateLayout(bounds.getWidth())
        );

        Platform.runLater(() ->
                updateLayout(
                        pageScroll.getViewportBounds().getWidth())
        );

        // Eingabe auf 120 Zeichen begrenzen
        noteArea.textProperty().addListener(
                (obs, oldText, newText) -> {

                    if (newText.length() > 120) {
                        noteArea.setText(
                                newText.substring(0, 120));
                        return;
                    }

                    counterLabel.setText(
                            noteArea.getText().length() + "/120");
                }
        );

        // Speicherbutton unter dem Sonderwunschfeld
        Button saveNoteButton =
                new Button("Sonderwunsch speichern");

        saveNoteButton.getStyleClass().add("primary-button");

        saveNoteButton.setMaxWidth(Double.MAX_VALUE);

        saveNoteButton.setOnAction(e -> saveSpecialRequest());

        // Button zur vorhandenen FXML hinzufügen
        VBox noteBox = (VBox) noteArea.getParent();
        noteBox.getChildren().add(saveNoteButton);

        noteArea.setPromptText(
                "Zuerst ein Gericht anklicken ...");

        renderTables();
        renderTabs();
        renderProducts();
        refresh();
    }

    // Fenstergröße anpassen
    private void updateLayout(double viewportWidth) {

        if (viewportWidth <= 0) return;

        double available = Math.max(320, viewportWidth - 44);
        boolean sideBySide = available >= 1080;

        double panelWidth = sideBySide
                ? (available - 24) / 2
                : available;

        orderPanel.setPrefWidth(panelWidth);
        rightPanel.setPrefWidth(panelWidth);

        contentLayout.setPrefWrapLength(available);

        productsPane.setPrefWrapLength(
                Math.max(200, panelWidth - 52));

        tablesPane.setPrefWrapLength(
                Math.max(200, panelWidth - 52));
    }

    private String money(double amount) {
        return String.format(
                Locale.GERMANY, "%.2f €", amount);
    }

    private Order draft() {
        return drafts.computeIfAbsent(
                selectedTable, Order::new);
    }

    // Tische anzeigen
    private void renderTables() {

        tablesPane.getChildren().clear();

        for (int i = 1; i <= 6; i++) {

            final int table = i;

            Button button = new Button(
                    String.format("%02d", i));

            button.getStyleClass().add("table-tile");

            if (i == selectedTable) {
                button.getStyleClass().add("selected-table");
            }

            button.setOnAction(e -> {

                selectedTable = table;

                // Auswahl beim Tischwechsel zurücksetzen
                selectedItem = null;
                noteArea.clear();

                renderTables();
                refresh();
            });

            tablesPane.getChildren().add(button);
        }
    }

    // Kategorien anzeigen
    private void renderTabs() {

        categoryTabs.getChildren().clear();

        for (String name : List.of(
                "Tagesgericht",
                "Speisen",
                "Getränke",
                "Dessert")) {

            Button button = new Button(name);
            button.getStyleClass().add("category-tab");

            if (name.equals(category)) {
                button.getStyleClass().add("active-tab");
            }

            button.setOnAction(e -> {

                category = name;

                renderTabs();
                renderProducts();
            });

            categoryTabs.getChildren().add(button);
        }
    }

    // Produkte anzeigen
    private void renderProducts() {

        productsPane.getChildren().clear();

        for (Product product : products) {

            if (!product.category().equals(category)) {
                continue;
            }

            VBox tile = new VBox(9);
            tile.getStyleClass().add("product-tile");

            Label name = new Label(product.name());
            name.getStyleClass().add("product-name");
            name.setWrapText(true);

            Label price = new Label(
                    money(product.price()));
            price.getStyleClass().add("muted");

            tile.getChildren().addAll(name, price);

            tile.setOnMouseClicked(e -> {

                draft().addProduct(product);
                refresh();
            });

            productsPane.getChildren().add(tile);
        }
    }

    // Gericht für Sonderwunsch auswählen
    private void selectItem(OrderItem item) {

        selectedItem = item;

        // Vorhandenen Sonderwunsch anzeigen
        noteArea.setText(item.getSpecialRequest());

        noteArea.setPromptText(
                "Sonderwunsch für " + item.getProduct().name());

        refresh();
    }

    // Sonderwunsch speichern
    private void saveSpecialRequest() {

        if (selectedItem == null) {

            info("Bitte zuerst ein Gericht "
                    + "in der Bestellung auswählen.");
            return;
        }

        String request = noteArea.getText().trim();

        selectedItem.setSpecialRequest(request);

        refresh();

        info(request.isEmpty()
                ? "Sonderwunsch wurde entfernt."
                : "Sonderwunsch wurde gespeichert.");
    }

    // Bestellung aktualisieren
    private void refresh() {

        currentTitle.setText(String.format(
                "Aktuelle Bestellung · Tisch %02d",
                selectedTable));

        paidTitle.setText(String.format(
                "Tisch abrechnen · Tisch %02d",
                selectedTable));

        currentItems.getChildren().clear();

        // Aktuelle Bestellpositionen
        for (OrderItem item : draft().getItems()) {

            VBox itemBox = new VBox(4);

            HBox line = new HBox(12);
            line.setAlignment(Pos.CENTER_LEFT);
            line.getStyleClass().add("item-row");

            Label qty = new Label(
                    String.valueOf(item.getQuantity()));
            qty.getStyleClass().add("qty");

            Label name = new Label(
                    item.getProduct().name());

            name.setWrapText(true);

            HBox.setHgrow(name, Priority.ALWAYS);
            name.setMaxWidth(Double.MAX_VALUE);

            // Gericht auswählen
            name.setOnMouseClicked(e -> selectItem(item));

            qty.setOnMouseClicked(e -> selectItem(item));

            // Menge verringern
            Button minus = new Button("−");
            minus.getStyleClass().add("small-action");

            minus.setOnAction(e -> {

                if (item.getQuantity() == 1) {

                    draft().getItems().remove(item);

                    if (selectedItem == item) {
                        selectedItem = null;
                        noteArea.clear();
                    }

                } else {

                    item.setQuantity(
                            item.getQuantity() - 1);
                }

                refresh();
            });

            // Menge erhöhen
            Button plus = new Button("+");
            plus.getStyleClass().add("small-action");

            plus.setOnAction(e -> {

                item.setQuantity(
                        item.getQuantity() + 1);

                refresh();
            });

            Label sum = new Label(
                    money(item.getTotal()));
            sum.getStyleClass().add("line-price");

            sum.setOnMouseClicked(e -> selectItem(item));

            line.getChildren().addAll(
                    qty, name, minus, plus, sum);

            // Ausgewähltes Gericht hervorheben
            if (selectedItem == item) {

                itemBox.setStyle(
                        "-fx-background-color: #e8f0ff;"
                                + "-fx-background-radius: 8;"
                                + "-fx-padding: 5;");
            }

            itemBox.getChildren().add(line);

            // Gespeicherten Sonderwunsch anzeigen
            if (!item.getSpecialRequest().isEmpty()) {

                Label request = new Label(
                        "↳ " + item.getSpecialRequest());

                request.setWrapText(true);

                request.setStyle(
                        "-fx-text-fill: #64748b;"
                                + "-fx-font-size: 12px;"
                                + "-fx-padding: 0 0 8 35;");

                request.setOnMouseClicked(
                        e -> selectItem(item));

                itemBox.getChildren().add(request);
            }

            currentItems.getChildren().add(itemBox);
        }

        // Leere Bestellung
        if (draft().getItems().isEmpty()) {

            Label empty = new Label(
                    "Noch keine Artikel – rechts ein Produkt auswählen.");

            empty.getStyleClass().add("muted");

            currentItems.getChildren().add(empty);
        }

        totalLabel.setText(
                money(draft().getTotal()));

        boolean hasDraft =
                !draft().getItems().isEmpty();

        discardButton.setDisable(!hasDraft);
        sendButton.setDisable(!hasDraft);

        // Gesendete Bestellungen
        paidItems.getChildren().clear();

        double sentTotal = 0;

        for (Order sent : sentByTable.getOrDefault(
                selectedTable, List.of())) {

            sentTotal += sent.getTotal();

            for (OrderItem item : sent.getItems()) {

                VBox itemBox = new VBox(4);

                HBox line = new HBox(12);
                line.setAlignment(Pos.CENTER_LEFT);
                line.getStyleClass().add("item-row");

                Label qty = new Label(
                        String.valueOf(item.getQuantity()));

                qty.getStyleClass().add("qty");

                Label name = new Label(
                        item.getProduct().name());

                HBox.setHgrow(name, Priority.ALWAYS);
                name.setMaxWidth(Double.MAX_VALUE);

                Label sum = new Label(
                        money(item.getTotal()));

                line.getChildren().addAll(qty, name, sum);
                itemBox.getChildren().add(line);

                // Sonderwunsch auch bei Abrechnung anzeigen
                if (!item.getSpecialRequest().isEmpty()) {

                    Label request = new Label(
                            "↳ " + item.getSpecialRequest());

                    request.setWrapText(true);

                    request.setStyle(
                            "-fx-text-fill: #64748b;"
                                    + "-fx-font-size: 12px;"
                                    + "-fx-padding: 0 0 8 35;");

                    itemBox.getChildren().add(request);
                }

                paidItems.getChildren().add(itemBox);
            }
        }

        if (paidItems.getChildren().isEmpty()) {

            Label empty = new Label(
                    "Noch keine gesendeten Bestellungen.");

            empty.getStyleClass().add("muted");

            paidItems.getChildren().add(empty);
        }

        paidTotal.setText(money(sentTotal));
        payButton.setDisable(sentTotal == 0);
    }

    // Bestellung verwerfen
    @FXML
    private void discard() {

        drafts.put(
                selectedTable,
                new Order(selectedTable));

        selectedItem = null;
        noteArea.clear();

        refresh();
    }

    // Bestellung senden
    @FXML
    private void send() {

        if (draft().getItems().isEmpty()) {
            return;
        }

        Order order = draft();

        // Hinweis bei noch nicht gespeichertem Sonderwunsch
        if (selectedItem != null
                && !noteArea.getText().trim().equals(
                selectedItem.getSpecialRequest())) {

            info("Bitte zuerst den Sonderwunsch speichern.");
            return;
        }

        OrderService.send(order);

        sentByTable.computeIfAbsent(
                selectedTable,
                k -> new ArrayList<>()
        ).add(order);

        drafts.put(
                selectedTable,
                new Order(selectedTable));

        selectedItem = null;
        noteArea.clear();

        refresh();

        info("Bestellung für Tisch "
                + String.format("%02d", selectedTable)
                + " wurde gespeichert. "
                + "Noch keine Küchenanbindung.");
    }

    // Tisch abrechnen
    @FXML
    private void pay() {

        if (sentByTable.getOrDefault(
                selectedTable, List.of()).isEmpty()) {
            return;
        }

        sentByTable.remove(selectedTable);

        drafts.put(
                selectedTable,
                new Order(selectedTable));

        selectedItem = null;
        noteArea.clear();

        refresh();

        info("Tisch "
                + String.format("%02d", selectedTable)
                + " als bezahlt markiert und freigegeben (Demo).");
    }

    @FXML
    private void logout() {
        info("Logout ist noch nicht mit einer Anmeldung verbunden.");
    }

    private void info(String message) {

        Alert alert = new Alert(
                Alert.AlertType.INFORMATION,
                message,
                ButtonType.OK);

        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
