package at.gastroflow.frontend.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

import java.time.LocalDateTime;
import java.util.List;

import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderItem;
import at.gastroflow.backend.model.OrderStatus;
import at.gastroflow.backend.model.Product;
import at.gastroflow.backend.service.DemoData;
import at.gastroflow.backend.service.InventoryService;
import at.gastroflow.backend.service.MenuService;
import at.gastroflow.backend.service.OrderService;

public class ServiceController {


    @FXML private FlowPane tablesPane, productsPane;
    @FXML private VBox currentItems, paidItems;
    @FXML private Label currentTitle, totalLabel, paidTitle;
    @FXML private Label paidTotal, counterLabel;
    @FXML private TextArea noteArea;
    @FXML private Button discardButton, sendButton, payButton;
    @FXML private FlowPane categoryTabs;
    @FXML private ScrollPane pageScroll;
    @FXML private FlowPane contentLayout;
    @FXML private VBox orderPanel, rightPanel;

    // Produkte kommen aus der gemeinsamen Speisekarte (von der Leitung verwaltet)
    private int selectedTable = 4;
    private String category = null;
    private Product selectedProduct = null;
    private String demoSpecialRequest = "";

    @FXML
    private void initialize() {

        DemoData.load();
        List<String> categories = categoriesWithProducts();
        category = categories.isEmpty() ? null : categories.get(0);

        pageScroll.viewportBoundsProperty().addListener(
                (obs, oldBounds, bounds) ->
                        updateLayout(bounds.getWidth())
        );

        Platform.runLater(() ->
                updateLayout(pageScroll.getViewportBounds().getWidth())
        );

        noteArea.textProperty().addListener(
                (obs, oldText, newText) -> {

                    if (newText.length() > 120) {
                        noteArea.setText(newText.substring(0, 120));
                        return;
                    }

                    counterLabel.setText(
                            noteArea.getText().length() + "/120"
                    );
                }
        );

        Button saveNoteButton =
                new Button("Sonderwunsch speichern");

        saveNoteButton.getStyleClass().add("primary-button");
        saveNoteButton.setMaxWidth(Double.MAX_VALUE);
        saveNoteButton.setOnAction(e -> saveSpecialRequest());

        VBox noteBox = (VBox) noteArea.getParent();
        noteBox.getChildren().add(saveNoteButton);

        noteArea.setPromptText("Zuerst ein Gericht auswählen ...");

        showTables();
        showTabs();
        showProducts();
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
                Math.max(200, panelWidth - 52)
        );

        tablesPane.setPrefWrapLength(
                Math.max(200, panelWidth - 52)
        );
    }

    // Tische anzeigen und auswählen

    private void showTables() {

        tablesPane.getChildren().clear();

        for (int i = 1; i <= 6; i++) {

            final int table = i;

            Button button = new Button(
                    String.format("%02d", i)
            );

            button.getStyleClass().add("table-tile");

            if (i == selectedTable) {
                button.getStyleClass().add("selected-table");
            }

            button.setOnAction(e -> {

                selectedTable = table;
                selectedProduct = null;
                demoSpecialRequest = "";
                noteArea.clear();

                showTables();
                refresh();
            });

            tablesPane.getChildren().add(button);
        }
    }

    // Kategorien anzeigen

    private void showTabs() {

        categoryTabs.getChildren().clear();

        for (String name : categoriesWithProducts()) {

            Button button = new Button(name);
            button.getStyleClass().add("category-tab");

            if (name.equals(category)) {
                button.getStyleClass().add("active-tab");
            }

            button.setOnAction(e -> {

                category = name;

                showTabs();
                showProducts();
            });

            categoryTabs.getChildren().add(button);
        }
    }

    // Produkte anzeigen

    private void showProducts() {

        productsPane.getChildren().clear();

        // Nur aktive Produkte – deaktivierte können nicht neu bestellt werden
        for (Product product : MenuService.getAvailableProducts()) {

            if (!product.getCategory().equals(category)) {
                continue;
            }

            VBox tile = new VBox(9);
            tile.getStyleClass().add("product-tile");

            Label name = new Label(product.getName());
            name.getStyleClass().add("product-name");
            name.setWrapText(true);

            Label price = new Label(formatPrice(product.getPrice()));
            price.getStyleClass().add("muted");

            tile.getChildren().addAll(name, price);

            tile.setOnMouseClicked(e -> {

                selectedProduct = product;
                demoSpecialRequest = "";
                noteArea.clear();

                refresh();
            });

            productsPane.getChildren().add(tile);
        }
    }

    // Sonderwunsch

    private void saveSpecialRequest() {

        if (selectedProduct == null) {
            info("Bitte zuerst ein Produkt auswählen.");
            return;
        }

        demoSpecialRequest = noteArea.getText().trim();
        refresh();
    }


    private void refresh() {

        currentTitle.setText(String.format(
                "Aktuelle Bestellung · Tisch %02d",
                selectedTable
        ));

        paidTitle.setText(String.format(
                "Tisch abrechnen · Tisch %02d",
                selectedTable
        ));

        currentItems.getChildren().clear();

        if (selectedProduct == null) {

            Label empty = new Label(
                    "Noch keine Artikel – rechts ein Produkt auswählen."
            );

            empty.getStyleClass().add("muted");
            currentItems.getChildren().add(empty);

        } else {

            VBox itemBox = new VBox(4);

            HBox line = new HBox(12);
            line.setAlignment(Pos.CENTER_LEFT);
            line.getStyleClass().add("item-row");

            Label qty = new Label("1");
            qty.getStyleClass().add("qty");

            Label name = new Label(selectedProduct.getName());
            name.setWrapText(true);

            HBox.setHgrow(name, Priority.ALWAYS);
            name.setMaxWidth(Double.MAX_VALUE);

            Button minus = new Button("−");
            minus.getStyleClass().add("small-action");

            minus.setOnAction(e -> {});

            Button plus = new Button("+");
            plus.getStyleClass().add("small-action");

            plus.setOnAction(e -> {});

            Label price = new Label(formatPrice(selectedProduct.getPrice()));
            price.getStyleClass().add("line-price");

            line.getChildren().addAll(
                    qty, name, minus, plus, price
            );

            itemBox.getChildren().add(line);

            if (!demoSpecialRequest.isEmpty()) {

                Label request = new Label(
                        "↳ " + demoSpecialRequest
                );

                request.setWrapText(true);

                request.setStyle(
                        "-fx-text-fill: #64748b;" +
                                "-fx-font-size: 12px;" +
                                "-fx-padding: 0 0 8 35;"
                );

                itemBox.getChildren().add(request);
            }

            currentItems.getChildren().add(itemBox);
        }

        totalLabel.setText(formatPrice(selectedProduct == null ? 0 : selectedProduct.getPrice()));

        discardButton.setDisable(selectedProduct == null);
        sendButton.setDisable(selectedProduct == null);

        paidItems.getChildren().clear();

        Label paidEmpty = new Label(
                "Noch keine gesendeten Bestellungen."
        );

        paidEmpty.getStyleClass().add("muted");
        paidItems.getChildren().add(paidEmpty);

        paidTotal.setText("0,00 €");
        payButton.setDisable(false);
    }


    @FXML
    private void discard() {

        selectedProduct = null;
        demoSpecialRequest = "";
        noteArea.clear();

        refresh();

        info("Bestellung verworfen.");
    }

    @FXML
    private void send() {

        if (selectedProduct == null) {
            info("Bitte zuerst ein Produkt auswählen.");
            return;
        }

        // Echte Bestellung anlegen – die Zutaten werden dabei automatisch vom Lager abgebucht
        Order order = new Order(selectedTable, "Service", LocalDateTime.now(), 1,
                List.of(new OrderItem(selectedProduct, 1)), OrderStatus.NEU);
        order.setNote(demoSpecialRequest);
        OrderService.send(order);

        String stockHint = InventoryService.problemFor(order)
                .map(problem -> "\n\nHinweis Lager: " + problem)
                .orElse("");

        selectedProduct = null;
        demoSpecialRequest = "";
        noteArea.clear();
        refresh();

        info("Bestellung #" + order.getNumber() + " an die Küche gesendet." + stockHint);
    }

    // Kategorien der Speisekarte, in denen es aktive Produkte gibt
    private List<String> categoriesWithProducts() {
        return MenuService.getCategories().stream()
                .filter(c -> MenuService.getAvailableProducts().stream()
                        .anyMatch(p -> p.getCategory().equals(c)))
                .toList();
    }

    private String formatPrice(double price) {
        return String.format("%.2f €", price).replace('.', ',');
    }

    @FXML
    private void pay() {

        info("Tisch als bezahlt markiert.");
    }

    @FXML
    private void logout() {

        info("Logout.");
    }

    private void info(String message) {

        Alert alert = new Alert(
                Alert.AlertType.INFORMATION,
                message,
                ButtonType.OK
        );

        alert.setHeaderText(null);
        alert.showAndWait();
    }


    @FXML
    private void handleLogout(ActionEvent event) throws IOException {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/at/gastroflow/frontend/views/login.fxml"
                )
        );

        Stage stage = (Stage) ((Node) event.getSource())
                .getScene().getWindow();

        Scene scene = new Scene(
                loader.load(),
                stage.getScene().getWidth(),
                stage.getScene().getHeight()
        );

        stage.setScene(scene);
        stage.setTitle("GastroFlow – Anmeldung");
    }

}
