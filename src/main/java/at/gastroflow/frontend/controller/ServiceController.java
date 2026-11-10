package at.gastroflow.frontend.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

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

    // Feste Beispieldaten für die UI

    private record DemoProduct(
            String name,
            String price,
            String category
    ) {}

    private final List<DemoProduct> products = List.of(
            new DemoProduct("Kürbiscremesuppe", "4,50 €", "Tagesgericht"),
            new DemoProduct("Hühnerschnitzel mit Petersilienkartoffeln",
                    "13,90 €", "Tagesgericht"),
            new DemoProduct("Apfelstrudel", "5,50 €", "Tagesgericht"),

            new DemoProduct("Gastro Burger", "14,90 €", "Speisen"),
            new DemoProduct("Trüffelpasta", "16,50 €", "Speisen"),
            new DemoProduct("Caesar Salad", "11,80 €", "Speisen"),
            new DemoProduct("Süßkartoffel-Pommes", "5,90 €", "Speisen"),

            new DemoProduct("Cola 0,33 l", "3,80 €", "Getränke"),
            new DemoProduct("Espresso", "2,90 €", "Getränke"),
            new DemoProduct("Wasser", "2,90 €", "Getränke"),
            new DemoProduct("Apfelsaft", "3,20 €", "Getränke"),

            new DemoProduct("Schokokuchen", "6,50 €", "Dessert")
    );

    private int selectedTable = 4;
    private String category = "Tagesgericht";
    private String selectedProduct = null;
    private String demoSpecialRequest = "";

    @FXML
    private void initialize() {

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

                showTabs();
                showProducts();
            });

            categoryTabs.getChildren().add(button);
        }
    }

    // Produkte anzeigen

    private void showProducts() {

        productsPane.getChildren().clear();

        for (DemoProduct product : products) {

            if (!product.category().equals(category)) {
                continue;
            }

            VBox tile = new VBox(9);
            tile.getStyleClass().add("product-tile");

            Label name = new Label(product.name());
            name.getStyleClass().add("product-name");
            name.setWrapText(true);

            Label price = new Label(product.price());
            price.getStyleClass().add("muted");

            tile.getChildren().addAll(name, price);

            tile.setOnMouseClicked(e -> {

                selectedProduct = product.name();
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

            Label name = new Label(selectedProduct);
            name.setWrapText(true);

            HBox.setHgrow(name, Priority.ALWAYS);
            name.setMaxWidth(Double.MAX_VALUE);

            Button minus = new Button("−");
            minus.getStyleClass().add("small-action");

            minus.setOnAction(e -> {});

            Button plus = new Button("+");
            plus.getStyleClass().add("small-action");

            plus.setOnAction(e -> {});

            Label price = new Label("0,00 €");
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

        totalLabel.setText("0,00 €");

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

        info("Bestellung an die Küche gesendet.");
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
}
