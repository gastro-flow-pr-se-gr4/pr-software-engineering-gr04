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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.gastroflow.backend.demo.model.Reservation;
import at.gastroflow.backend.demo.model.ReservationStatus;
import at.gastroflow.backend.demo.model.RestaurantSettings;
import at.gastroflow.backend.demo.service.ReservationService;
import at.gastroflow.frontend.management.ReservationDialog;
import at.gastroflow.frontend.management.Ui;

import at.gastroflow.backend.demo.model.Product;
import at.gastroflow.backend.demo.service.DemoData;
import at.gastroflow.backend.demo.service.MenuService;

// Bestellungen laufen über die REST-API des echten Backends (#75)
import at.gastroflow.backend.dto.BillDTO;
import at.gastroflow.backend.dto.OrderDTO;
import at.gastroflow.backend.dto.OrderItemDTO;
import at.gastroflow.backend.model.OrderStatus;
import at.gastroflow.frontend.api.ApiException;
import at.gastroflow.frontend.api.InventoryBridge;
import at.gastroflow.frontend.api.OrderClient;
import at.gastroflow.frontend.model.OrderDraft;

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

    // Produkte kommen aus der gemeinsamen Speisekarte (von der Leitung verwaltet)
    private int selectedTable = 4;
    private String category = null;

    // Angefangene Bestellung pro Tisch – bleibt beim Tischwechsel und Abmelden erhalten
    private static final Map<Integer, OrderDraft> DRAFTS = new HashMap<>();
    private OrderDraft.Line selectedLine = null;

    // Laufende Bestellungen aller Tische, einmal pro Aktualisierung von der REST-API geholt
    private List<OrderDTO> activeOrders = List.of();

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

        selectLine(null);
        showTables();
        showTabs();
        showProducts();
        showReservations();
        refresh();
        showOrderPage();
    }

    // Seiten innerhalb des Service: Bestellen und Reservierungen

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

    // Reservierungen von heute (gleiche Daten wie in der Leitung)

    private void showReservations() {

        reservationsBox.getChildren().clear();
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> today = ReservationService.getReservationsOn(LocalDate.now());

        int guests = today.stream()
                .filter(r -> r.getStatus() != ReservationStatus.NO_SHOW)
                .mapToInt(Reservation::getPersons).sum();
        long expected = today.stream()
                .filter(r -> r.getStatus() == ReservationStatus.BESTAETIGT).count();
        reservationSummary.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                + "  ·  " + today.size() + " Reservierungen  ·  " + guests + " Gäste  ·  "
                + expected + " noch erwartet");

        if (today.isEmpty()) {
            Label empty = new Label("Heute gibt es keine Reservierungen.");
            empty.getStyleClass().add("muted");
            reservationsBox.getChildren().add(empty);
        }

        for (Reservation reservation : today) {
            reservationsBox.getChildren().add(reservationRow(reservation, now));
        }
    }

    private VBox reservationRow(Reservation reservation, LocalDateTime now) {

        Label time = new Label(reservation.getStart().format(TIME_FORMAT)
                + "–" + reservation.getEnd().format(TIME_FORMAT));
        time.getStyleClass().add("res-time");

        Label name = new Label(reservation.getName());
        name.getStyleClass().add("res-name");
        name.setWrapText(true);

        Label status = Ui.badge(Ui.reservationLabel(reservation, now),
                Ui.reservationColor(reservation, now));

        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        HBox top = new HBox(10, time, name, space, status);
        top.setAlignment(Pos.CENTER_LEFT);

        Label details = new Label(reservation.getPersons() + " Pers. · Tisch "
                + reservation.getTable()
                + (reservation.hasPhone() ? " · ☎ " + reservation.getPhone() : ""));
        details.getStyleClass().add("muted");

        VBox row = new VBox(6, top, details);
        row.getStyleClass().add("item-row");

        if (reservation.getNote() != null && !reservation.getNote().isBlank()) {
            Label note = new Label("Notiz: " + reservation.getNote());
            note.getStyleClass().add("muted");
            note.setWrapText(true);
            row.getChildren().add(note);
        }

        // Aktionen
        HBox actions = new HBox(8);

        if (reservation.getStatus() == ReservationStatus.BESTAETIGT) {
            actions.getChildren().add(actionButton("Angekommen", () -> {
                try {
                    ReservationService.setStatus(reservation, ReservationStatus.ANWESEND);
                    selectTable(reservation.getTable());
                } catch (IllegalArgumentException e) {
                    warning(e.getMessage());
                }
                showReservations();
            }));
        }

        actions.getChildren().addAll(
                actionButton("Für Tisch " + reservation.getTable() + " bestellen", () -> {
                    selectTable(reservation.getTable());
                    showOrderPage();
                }),
                actionButton("Bearbeiten", () -> {
                    if (ReservationDialog.show(reservation, LocalDate.now())) {
                        showReservations();
                        showTables();
                    }
                }));

        row.getChildren().add(actions);
        return row;
    }

    private Button actionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("small-action");
        button.setOnAction(e -> action.run());
        return button;
    }

    // Wählt einen Tisch für die neue Bestellung aus
    private void selectTable(int table) {
        storeNote();
        selectedTable = table;
        selectLine(null);
        showTables();
        refresh();
    }

    // Neue Reservierung für heute anlegen (mit Prüfung, ob der Tisch frei ist)
    @FXML
    private void newReservation() {
        if (ReservationDialog.show(null, LocalDate.now())) {
            showReservations();
            showTables();
        }
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

        List<Reservation> reservations = ReservationService.getReservationsOn(LocalDate.now());
        LocalDateTime now = LocalDateTime.now();

        loadActiveOrders();

        // Tischanzahl aus den gemeinsamen Einstellungen (von der Leitung festgelegt)
        for (int i = 1; i <= RestaurantSettings.get().getTableCount(); i++) {

            final int table = i;
            int seats = RestaurantSettings.get().getCapacity(i);

            String status = tableStatus(table, reservations);
            Reservation next = nextReservation(table, reservations, now);

            String text = String.format("%02d", i) + "\n" + seats + " Plätze";
            if (status.equals("reserviert") && next != null) {
                text = text + "\nab " + next.getStart().format(TIME_FORMAT);
            }

            // Hinweis, dass die Küche etwas fertig hat
            boolean ready = activeOrders.stream()
                    .anyMatch(order -> order.table() == table && order.status() == OrderStatus.FERTIG);
            if (ready) {
                text = text + "\n● fertig";
            }

            Button button = new Button(text);
            button.getStyleClass().addAll("table-tile", sizeClass(seats), "table-" + status);

            if (i == selectedTable) {
                button.getStyleClass().add("selected-table");
            }

            button.setTooltip(new Tooltip(tooltipText(table, seats, status, next)));
            button.setOnAction(e -> selectTable(table));

            int column = (i - 1) % TABLES_PER_ROW;
            int row = (i - 1) / TABLES_PER_ROW;
            GridPane.setHalignment(button, HPos.CENTER);
            tablesPane.add(button, column, row);
        }
    }

    // Holt alle laufenden Bestellungen; ist das Backend nicht erreichbar, bleibt die Liste leer
    // (die Fehlermeldung erscheint dann bei der Rechnung des Tisches)
    private void loadActiveOrders() {
        try {
            activeOrders = OrderClient.activeOrders();
        } catch (ApiException e) {
            activeOrders = List.of();
        }
    }

    private String tableStatus(int table, List<Reservation> reservations) {

        if (activeOrders.stream().anyMatch(order -> order.table() == table)) {
            return "besetzt";
        }

        for (Reservation reservation : reservations) {
            if (reservation.getTable() == table
                    && reservation.getStatus() == ReservationStatus.ANWESEND) {
                return "besetzt";
            }
        }

        // erst 2 Stunden vor der Reservierung als reserviert anzeigen
        LocalDateTime now = LocalDateTime.now();
        Reservation next = nextReservation(table, reservations, now);
        if (next != null && next.getStart().minusHours(2).isBefore(now)) {
            return "reserviert";
        }

        return "frei";
    }

    // nächste bestätigte Reservierung für den Tisch, die heute noch nicht vorbei ist
    private Reservation nextReservation(int table, List<Reservation> reservations, LocalDateTime now) {

        for (Reservation reservation : reservations) {
            if (reservation.getTable() == table
                    && reservation.getStatus() == ReservationStatus.BESTAETIGT
                    && reservation.getEnd().isAfter(now)) {
                return reservation;
            }
        }
        return null;
    }

    private String sizeClass(int seats) {
        if (seats <= 4) {
            return "table-small";
        } else if (seats <= 6) {
            return "table-medium";
        } else {
            return "table-large";
        }
    }

    private String tooltipText(int table, int seats, String status, Reservation next) {

        String text = "Tisch " + table + " · " + seats + " Plätze · " + status;

        if (next != null) {
            text = text + "\nNächste Reservierung: " + next.getStart().format(TIME_FORMAT)
                    + " " + next.getName() + " (" + next.getPersons() + " Pers.)";
        }
        return text;
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

                storeNote();
                selectLine(draft().add(product));
                refresh();
            });

            productsPane.getChildren().add(tile);
        }

        if (productsPane.getChildren().isEmpty()) {
            Label empty = new Label("Keine Produkte in dieser Kategorie verfügbar.");
            empty.getStyleClass().add("muted");
            productsPane.getChildren().add(empty);
        }
    }

    // Sonderwunsch

    private void saveSpecialRequest() {

        if (selectedLine == null) {
            warning("Bitte zuerst ein Produkt auswählen.");
            return;
        }

        storeNote();
        refresh();
    }

    // Übernimmt den eingetippten Text, damit kein Sonderwunsch verloren geht
    private void storeNote() {
        if (selectedLine != null) {
            selectedLine.setNote(noteArea.getText());
        }
    }

    // Wählt die Position aus, zu der der Sonderwunsch gehört
    private void selectLine(OrderDraft.Line line) {
        selectedLine = line;
        noteArea.setText(line == null ? "" : line.getNote());
        noteArea.setDisable(line == null);
        noteTarget.setText(line == null ? "" : "für " + line.getProduct().getName());
    }

    // Angefangene Bestellung für den ausgewählten Tisch
    private OrderDraft draft() {
        return DRAFTS.computeIfAbsent(selectedTable, OrderDraft::new);
    }

    // Eine Position mit Plus, Minus und Entfernen
    private VBox orderLine(OrderDraft.Line line) {

        Label qty = new Label(String.valueOf(line.getQuantity()));
        qty.getStyleClass().add("qty");

        Label name = new Label(line.getProduct().getName());
        name.setWrapText(true);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        Button minus = lineButton("−", "Menge verringern", () -> draft().decrease(line));
        minus.setDisable(line.getQuantity() <= 1);

        Button plus = lineButton("+", "Menge erhöhen", () -> draft().increase(line));
        plus.setDisable(line.getQuantity() >= OrderDraft.MAX_QUANTITY);

        Button remove = lineButton("✕", "Position entfernen", () -> {
            draft().remove(line);
            if (line == selectedLine) {
                selectLine(null);
            }
        });
        remove.getStyleClass().add("remove-action");

        Label price = new Label(formatPrice(line.getTotal()));
        price.getStyleClass().add("line-price");

        HBox row = new HBox(12, qty, name, minus, plus, remove, price);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6, row);
        box.getStyleClass().addAll("item-row", "order-line");
        if (line == selectedLine) {
            box.getStyleClass().add("selected-line");
        }

        if (!line.getNote().isEmpty()) {
            Label request = new Label("↳ " + line.getNote());
            request.setWrapText(true);
            request.getStyleClass().add("line-note");
            box.getChildren().add(request);
        }

        // Anklicken wählt die Position für den Sonderwunsch aus
        box.setOnMouseClicked(e -> {
            if (!draft().getLines().contains(line)) {
                return; // wurde gerade entfernt
            }
            storeNote();
            selectLine(line);
            refresh();
        });
        return box;
    }

    private Button lineButton(String text, String description, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("small-action");
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        button.setOnAction(e -> {
            storeNote();
            action.run();
            refresh();
        });
        return button;
    }

    // Fehler beim Absenden anzeigen (null = kein Fehler)
    private void showSendError(String message) {
        sendError.setText(message == null ? "" : message);
        sendError.setVisible(message != null);
        sendError.setManaged(message != null);
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

        OrderDraft draft = draft();

        if (draft.isEmpty()) {
            Label empty = new Label("Noch keine Artikel – rechts ein Produkt auswählen.");
            empty.getStyleClass().add("muted");
            currentItems.getChildren().add(empty);
        }

        for (OrderDraft.Line line : draft.getLines()) {
            currentItems.getChildren().add(orderLine(line));
        }

        totalLabel.setText(formatPrice(draft.getTotal()));

        // Leere Bestellungen können nicht gesendet werden
        discardButton.setDisable(draft.isEmpty());
        sendButton.setDisable(draft.isEmpty());
        showSendError(null);

        showBill();
    }

    // Alle laufenden Bestellungen des Tisches; zu zahlen sind nur die unbezahlten
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
        boolean unpaid = active.stream().anyMatch(OrderDTO::isPaymentDue);

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
        payButton.setDisable(!unpaid);
    }

    private VBox billOrder(OrderDTO order) {

        Label title = new Label("Bestellung #" + order.number()
                + " · " + order.createdAt().format(TIME_FORMAT) + " Uhr");
        title.getStyleClass().add("res-name");

        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        // abgeschlossen heißt hier: serviert, aber noch nicht bezahlt
        String status = order.status() == OrderStatus.ABGESCHLOSSEN
                ? "Serviert" : order.status().toString();

        HBox heading = new HBox(10, title, space);
        heading.setAlignment(Pos.CENTER_LEFT);
        if (order.isPaid()) {
            heading.getChildren().add(Ui.badge("Bezahlt", "gray"));
        }
        heading.getChildren().add(Ui.badge(status, Ui.orderColor(order.status())));

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

        if (order.status() == OrderStatus.FERTIG) {
            box.getChildren().add(actionButton("Serviert", () -> {
                try {
                    OrderClient.changeStatus(order.id(), OrderStatus.ABGESCHLOSSEN);
                } catch (IllegalStateException e) {
                    warning(e.getMessage());
                }
                showTables();
                refresh();
            }));
        }
        return box;
    }


    @FXML
    private void discard() {

        if (!Ui.confirm("Bestellung für Tisch " + selectedTable + " mit " + draft().getItemCount()
                + " Artikeln verwerfen?", "Verwerfen")) {
            return;
        }

        selectLine(null);
        DRAFTS.remove(selectedTable);
        refresh();
    }

    @FXML
    private void send() {

        storeNote();
        OrderDraft draft = draft();

        // Prüfungen, die nur das Frontend kennt (Tische und Speisekarte liegen noch in der Demo)
        String problem = checkDraft(draft);
        if (problem != null) {
            showSendError(problem); // die Bestellung bleibt zum Korrigieren erhalten
            return;
        }

        // Über die REST-API anlegen; die id des Entwurfs verhindert doppeltes Speichern
        OrderDTO order;
        try {
            order = OrderClient.create(draft.toRequest("Service"));
        } catch (ApiException e) {
            showSendError(e.getMessage());
            return;
        }
        draft.markSent();

        // Zutaten automatisch vom Lager abbuchen (Lager liegt noch in der Demo)
        InventoryBridge.book(order);

        // Entwurf leeren, bevor die Bestätigung erscheint – so ist kein zweites Absenden möglich
        DRAFTS.remove(selectedTable);
        selectLine(null);
        showTables();
        refresh();

        String confirmation = "Bestellung #" + order.number() + " für Tisch " + order.table()
                + " wurde an die Küche gesendet.\n\n"
                + draft.getItemCount() + " Artikel · " + formatPrice(order.total());

        InventoryBridge.problemFor(order).ifPresentOrElse(
                stockProblem -> warning(confirmation + "\n\nHinweis Lager: " + stockProblem),
                () -> info(confirmation));
    }

    // Gibt es den Tisch noch und sind alle Produkte verfügbar? (null = alles in Ordnung)
    private String checkDraft(OrderDraft draft) {
        if (draft.isSent()) {
            return "Diese Bestellung wurde bereits an die Küche gesendet.";
        }
        if (draft.isEmpty()) {
            return "Die Bestellung ist leer. Bitte zuerst ein Produkt auswählen.";
        }
        if (draft.getTable() < 1 || draft.getTable() > RestaurantSettings.get().getTableCount()) {
            return "Tisch " + draft.getTable() + " gibt es nicht mehr. Bitte einen anderen Tisch wählen.";
        }
        String unavailable = draft.getLines().stream()
                .map(OrderDraft.Line::getProduct)
                .filter(product -> !MenuService.getAvailableProducts().contains(product))
                .map(Product::getName)
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
        return unavailable.isEmpty() ? null
                : "Nicht mehr verfügbar: " + unavailable + ". Bitte aus der Bestellung entfernen.";
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

    private String formatPrice(BigDecimal price) {
        return formatPrice(price.doubleValue());
    }

    @FXML
    private void pay() {

        int table = selectedTable;
        BillDTO bill;
        try {
            bill = OrderClient.bill(table);
        } catch (ApiException e) {
            warning(e.getMessage());
            return;
        }
        long notServed = bill.orders().stream()
                .filter(order -> order.isPaymentDue() && order.isOpen())
                .count();

        String question = "Tisch " + table + " abkassieren?\n\nZu zahlen: " + formatPrice(bill.amountDue());
        if (notServed > 0) {
            question += "\n\nAchtung: " + (notServed == 1 ? "1 Bestellung ist" : notServed + " Bestellungen sind")
                    + " noch nicht serviert. Der Tisch bleibt besetzt, bis alles serviert ist.";
        }
        if (!Ui.confirm(question, "Bezahlt")) {
            return;
        }

        try {
            BigDecimal paid = OrderClient.pay(table);
            endPresentReservations(table);
            showTables();
            showReservations();
            refresh();
            info("Tisch " + table + ": " + formatPrice(paid) + " bezahlt."
                    + (OrderClient.isTableOccupied(table) ? "" : " Der Tisch ist frei."));
        } catch (ApiException e) {
            refresh();
            warning(e.getMessage());
        }
    }

    // Nach dem Bezahlen sind die anwesenden Gäste dieses Tisches fertig
    private void endPresentReservations(int table) {
        for (Reservation reservation : ReservationService.getReservationsOn(LocalDate.now())) {
            if (reservation.getTable() == table && reservation.getStatus() == ReservationStatus.ANWESEND) {
                ReservationService.setStatus(reservation, ReservationStatus.ABGESCHLOSSEN);
            }
        }
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
        storeNote(); // eingetippten Sonderwunsch nicht verlieren
        Navigation.toLogin((Node) event.getSource());
    }

}
