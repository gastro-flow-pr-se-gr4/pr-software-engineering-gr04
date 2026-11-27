package at.gastroflow.frontend.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.event.ActionEvent;
import javafx.scene.Node;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

import at.gastroflow.backend.dto.BillDTO;
import at.gastroflow.backend.dto.OrderDTO;
import at.gastroflow.backend.dto.OrderItemDTO;
import at.gastroflow.backend.model.OrderStatus;
import at.gastroflow.frontend.api.ApiException;
import at.gastroflow.frontend.api.OrderClient;

public class ServiceController {

    @FXML private GridPane tablesPane;
    @FXML private FlowPane productsPane;
    @FXML private VBox currentItems, paidItems;
    @FXML private Label currentTitle, totalLabel, paidTitle;
    @FXML private Label paidTotal, counterLabel, noteTarget, sendError;
    @FXML private TextArea noteArea;
    @FXML private Button discardButton, sendButton, payButton;
    @FXML private FlowPane categoryTabs;
    @FXML private VBox reservationsBox;
    @FXML private ScrollPane reservationPage;
    @FXML private Label reservationSummary;
    @FXML private Button orderTab, reservationTab;
    @FXML private ScrollPane pageScroll;
    @FXML private FlowPane contentLayout;
    @FXML private VBox orderPanel, rightPanel;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int TABLES_PER_ROW = 3;
    // Vorläufige UI-Konfiguration aus dem bisherigen Raumplan.
    private static final List<Integer> TABLE_CAPACITIES = List.of(4, 4, 4, 4, 4, 4, 6, 6, 6, 6, 8, 8);

    private int selectedTable = 4;

    private List<OrderDTO> activeOrders = List.of();

    @FXML
    private void initialize() {
        pageScroll.viewportBoundsProperty().addListener(
                (obs, oldBounds, bounds) -> updateLayout(bounds.getWidth()));
        Platform.runLater(() -> updateLayout(pageScroll.getViewportBounds().getWidth()));
        noteArea.setDisable(true);
        noteArea.setPromptText("Bestellerstellung noch nicht verfügbar");
        counterLabel.setText("0/120");
        noteTarget.setText("");
        showTables();
        showTabs();
        showProducts();
        showReservations();
        refresh();
        showOrderPage();
    }

    @FXML
    private void showOrderPage() {
        switchPage(true);
    }

    @FXML
    private void showReservationPage() {
        showReservations();
        switchPage(false);
    }

    private void switchPage(boolean orders) {
        pageScroll.setVisible(orders);
        reservationPage.setVisible(!orders);

        orderTab.getStyleClass().remove("service-tab-active");
        reservationTab.getStyleClass().remove("service-tab-active");
        (orders ? orderTab : reservationTab).getStyleClass().add("service-tab-active");
    }

    @FXML
    private void refreshReservations() {
        showReservations();
        showTables();
    }

    private void showReservations() {
        reservationsBox.getChildren().clear();
        reservationSummary.setText("Reservierungsverwaltung noch nicht angebunden");
        Label empty = new Label("Reservierungen sind derzeit nicht verfügbar.");
        empty.getStyleClass().add("muted");
        reservationsBox.getChildren().add(empty);
    }

    private void selectTable(int table) {
        selectedTable = table;
        showTables();
        refresh();
    }

    @FXML
    private void newReservation() {
        warning("Die Reservierungsverwaltung ist noch nicht angebunden.");
    }

    // Fenstergröße anpassen

    private void updateLayout(double viewportWidth) {

        if (viewportWidth <= 0) return;

        double available = Math.max(320, viewportWidth - 44);
        boolean sideBySide = available >= 1080;

        double panelWidth = sideBySide
                ? Math.floor((available - 24) / 2)
                : available;

        orderPanel.setPrefWidth(panelWidth);
        rightPanel.setPrefWidth(panelWidth);

        contentLayout.setPrefWrapLength(available);

        productsPane.setPrefWrapLength(
                Math.max(200, panelWidth - 52)
        );
    }

    // Raumplan: Tische mit Größe und Status (frei / reserviert / besetzt)

    private void showTables() {
        tablesPane.getChildren().clear();
        loadActiveOrders();
        for (int i = 1; i <= TABLE_CAPACITIES.size(); i++) {
            final int table = i;
            int seats = TABLE_CAPACITIES.get(i - 1);
            String status = tableStatus(table);
            String text = String.format("%02d", i) + "\n" + seats + " Plätze";
            if (activeOrders.stream().anyMatch(order -> order.table() == table
                    && order.status() == OrderStatus.FERTIG)) {
                text += "\n● fertig";
            }
            Button button = new Button(text);
            button.getStyleClass().addAll("table-tile", sizeClass(seats), "table-" + status);
            if (i == selectedTable) button.getStyleClass().add("selected-table");
            button.setTooltip(new Tooltip("Tisch " + table + " · " + seats + " Plätze"));
            button.setOnAction(e -> selectTable(table));
            GridPane.setHalignment(button, HPos.CENTER);
            tablesPane.add(button, (i - 1) % TABLES_PER_ROW, (i - 1) / TABLES_PER_ROW);
        }
    }

    private void loadActiveOrders() {
        try {
            activeOrders = OrderClient.activeOrders();
        } catch (ApiException e) {
            activeOrders = List.of();
        }
    }

    private String tableStatus(int table) {
        return activeOrders.stream().anyMatch(order -> order.table() == table)
                ? "besetzt" : "frei";
    }

    // nächste bestätigte Reservierung für den Tisch, die heute noch nicht vorbei ist

    private String sizeClass(int seats) {
        if (seats <= 4) {
            return "table-small";
        } else if (seats <= 6) {
            return "table-medium";
        } else {
            return "table-large";
        }
    }

    // Kategorien anzeigen

    private void showTabs() {
        categoryTabs.getChildren().clear();
    }

    // Produkte anzeigen

    private void showProducts() {
        productsPane.getChildren().clear();
        Label empty = new Label("Speisekarte noch nicht angebunden.");
        empty.getStyleClass().add("muted");
        productsPane.getChildren().add(empty);
    }

    // Sonderwunsch

    private void showSendError(String message) {
        sendError.setText(message == null ? "" : message);
        sendError.setVisible(message != null);
        sendError.setManaged(message != null);
    }

    private void refresh() {
        currentTitle.setText(String.format("Aktuelle Bestellung · Tisch %02d", selectedTable));
        paidTitle.setText(String.format("Tisch abrechnen · Tisch %02d", selectedTable));
        currentItems.getChildren().clear();
        Label empty = new Label("Bestellerstellung noch nicht verfügbar.");
        empty.getStyleClass().add("muted");
        currentItems.getChildren().add(empty);
        totalLabel.setText(formatPrice(BigDecimal.ZERO));
        discardButton.setDisable(true);
        sendButton.setDisable(true);
        showSendError(null);
        showBill();
    }

    private void showBill() {

        paidItems.getChildren().clear();
        BillDTO bill;
        try {
            bill = OrderClient.bill(selectedTable);
        } catch (ApiException e) {
            Label error = new Label("Rechnung nicht verfügbar: " + e.getMessage());
            error.getStyleClass().add("error-text");
            error.setWrapText(true);
            paidItems.getChildren().add(error);
            paidTotal.setText(formatPrice(BigDecimal.ZERO));
            payButton.setDisable(true);
            return;
        }

        List<OrderDTO> active = bill.orders();

        if (active.isEmpty()) {
            Label empty = new Label("Nichts offen – für diesen Tisch gibt es keine laufenden Bestellungen.");
            empty.getStyleClass().add("muted");
            empty.setWrapText(true);
            paidItems.getChildren().add(empty);
        }

        for (OrderDTO order : active) {
            paidItems.getChildren().add(billOrder(order));
        }

        paidTotal.setText(formatPrice(bill.amountDue()));
        payButton.setDisable(true);
    }

    private VBox billOrder(OrderDTO order) {

        Label title = new Label(order.course() + " · #" + order.number()
                + " · " + order.createdAt().format(TIME_FORMAT) + " Uhr");
        title.getStyleClass().add("res-name");

        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        String status = order.status() == OrderStatus.ABGESCHLOSSEN ? "Serviert"
                : !order.isKitchenOrder() && order.status() == OrderStatus.NEU ? "Zu bringen"
                : order.status().toString();

        HBox heading = new HBox(10, title, space);
        heading.setAlignment(Pos.CENTER_LEFT);
        if (order.isPaid()) {
            heading.getChildren().add(new Label("Bezahlt"));
        }
        heading.getChildren().add(new Label(status));

        VBox box = new VBox(6, heading);
        box.getStyleClass().add("item-row");

        for (OrderItemDTO item : order.items()) {
            Label name = new Label(item.quantity() + "× " + item.productName());
            name.setWrapText(true);
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);

            Label price = new Label(formatPrice(item.total()));
            price.getStyleClass().add("line-price");
            box.getChildren().add(new HBox(12, name, price));
        }

        if (!order.note().isBlank()) {
            Label note = new Label("↳ " + order.note().replace("\n", "\n↳ "));
            note.getStyleClass().add("muted");
            note.setWrapText(true);
            box.getChildren().add(note);
        }

        return box;
    }

    @FXML
    private void discard() {
        warning("Bestellerstellung noch nicht verfügbar.");
    }

    @FXML
    private void send() {
        warning("Bestellerstellung noch nicht verfügbar.");
    }

    private String formatPrice(double price) {
        return String.format("%.2f €", price).replace('.', ',');
    }

    private String formatPrice(BigDecimal price) {
        return formatPrice(price.doubleValue());
    }

    @FXML
    private void pay() {
        warning("Bezahlung derzeit deaktiviert.");
    }

    private void warning(String message) {

        Alert alert = new Alert(
                Alert.AlertType.WARNING,
                message,
                ButtonType.OK
        );

        alert.setTitle("Hinweis");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        Navigation.toLogin((Node) event.getSource());
    }

}
