package at.gastroflow.frontend.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.stream.Collectors;

import at.gastroflow.backend.dto.KitchenTableDTO;
import at.gastroflow.backend.dto.OrderDTO;
import at.gastroflow.backend.dto.OrderItemDTO;
import at.gastroflow.backend.model.Course;
import at.gastroflow.backend.model.OrderStatus;
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

    private void showOrders() {

        newColumn.getChildren().clear();
        preparingColumn.getChildren().clear();
        finishedColumn.getChildren().clear();

        String error = null;
        List<KitchenTableDTO> tables;
        try {
            tables = OrderClient.kitchenTables();
        } catch (IllegalStateException e) {
            tables = List.of();
            error = "Bestellungen nicht verfügbar: " + e.getMessage();
        }

        for (KitchenTableDTO table : tables) {
            switch (table.stage()) {
                case NEU -> newColumn.getChildren().add(newCard(table));
                case IN_ZUBEREITUNG -> preparingColumn.getChildren().add(preparingCard(table));
                case FERTIG -> finishedColumn.getChildren().add(finishedCard(table));
                default -> { }
            }
        }

        finish(newColumn, newCount, error != null ? error : "Keine neuen Bestellungen.");
        finish(preparingColumn, preparingCount, "Nichts in Zubereitung.");
        finish(finishedColumn, finishedCount, "Nichts zur Abholung bereit.");
    }

    private void finish(VBox column, Label counter, String emptyText) {
        counter.setText(String.valueOf(column.getChildren().size()));
        if (column.getChildren().isEmpty()) {
            Label empty = new Label(emptyText);
            empty.getStyleClass().add("empty-column");
            empty.setWrapText(true);
            column.getChildren().add(empty);
        }
    }

    private VBox newCard(KitchenTableDTO table) {
        Course course = table.nextCourse();
        VBox card = card(table, course.toString());
        addOrders(card, ofCourse(withStatus(table, OrderStatus.NEU), course));
        addLaterCourses(card, table, course);
        card.getChildren().add(actionButton("Zubereitung starten", () -> OrderClient.startNextCourse(table.table())));
        return card;
    }

    private VBox preparingCard(KitchenTableDTO table) {
        List<OrderDTO> preparing = withStatus(table, OrderStatus.IN_ZUBEREITUNG);
        VBox card = card(table, courseNames(preparing));
        addOrders(card, preparing);
        addLaterCourses(card, table, preparing.get(preparing.size() - 1).course());
        card.getChildren().add(actionButton("Fertig", () -> OrderClient.markCourseReady(table.table())));
        return card;
    }

    private VBox finishedCard(KitchenTableDTO table) {
        List<OrderDTO> ready = withStatus(table, OrderStatus.FERTIG);

        VBox card;
        if (ready.isEmpty()) {
            List<OrderDTO> served = withStatus(table, OrderStatus.ABGESCHLOSSEN);
            card = card(table, served.isEmpty() ? "" : served.get(served.size() - 1).course() + " serviert");
        } else {
            card = card(table, courseNames(ready) + " fertig");
            addOrders(card, ready);
            Label waiting = new Label("Wartet auf den Service");
            waiting.getStyleClass().add("ready-hint");
            card.getChildren().add(waiting);
        }

        if (table.nextCourse() != null) {
            Course course = table.nextCourse();
            List<OrderDTO> nextOrders = ofCourse(withStatus(table, OrderStatus.NEU), course);
            Label preview = new Label("Als Nächstes: " + course + " (" + itemsText(nextOrders) + ")");
            preview.getStyleClass().add("next-course");
            preview.setWrapText(true);
            preview.setMinHeight(Region.USE_PREF_SIZE);
            card.getChildren().add(preview);
            card.getChildren().add(actionButton("Weiter mit " + course, () -> OrderClient.startNextCourse(table.table())));
        } else if (table.canClose()) {
            Button close = actionButton("Abschließen", () -> OrderClient.closeKitchenTable(table.table()));
            close.getStyleClass().add("close-button");
            card.getChildren().add(close);
        }
        return card;
    }

    private VBox card(KitchenTableDTO table, String courseText) {
        VBox card = new VBox(12);
        card.getStyleClass().add("order-card");

        Label title = new Label("Tisch " + table.table());
        title.getStyleClass().add("order-title");
        title.setWrapText(true);
        card.getChildren().addAll(title, courseProgress(table));

        if (!courseText.isBlank()) {
            Label course = new Label(courseText.toUpperCase());
            course.getStyleClass().add("course-label");
            course.setWrapText(true);
            card.getChildren().add(course);
        }
        return card;
    }

    private FlowPane courseProgress(KitchenTableDTO table) {
        FlowPane row = new FlowPane(10, 4);
        for (KitchenTableDTO.CourseProgressDTO progress : table.courses()) {
            Label label = new Label(progress.done() ? "✓ " + progress.course() : progress.course().toString());
            label.getStyleClass().add(progress.done() ? "course-done" : progress.cooking() ? "course-current" : "course-open");
            row.getChildren().add(label);
        }
        return row;
    }

    private void addOrders(VBox card, List<OrderDTO> orders) {
        VBox items = new VBox(5);
        for (OrderDTO order : orders) {
            for (OrderItemDTO item : order.items()) {
                Label line = new Label(item.quantity() + "× " + item.productName());
                line.getStyleClass().add("order-items");
                line.setWrapText(true);
                line.setMinHeight(Region.USE_PREF_SIZE);
                items.getChildren().add(line);
            }
        }
        card.getChildren().add(items);

        String notes = orders.stream()
                .map(OrderDTO::note)
                .filter(note -> !note.isBlank())
                .collect(Collectors.joining("\n"));
        if (!notes.isBlank()) {
            Label noteLabel = new Label("Sonderwunsch\n" + notes);
            noteLabel.getStyleClass().add("order-note");
            noteLabel.setWrapText(true);
            noteLabel.setMinHeight(Region.USE_PREF_SIZE);
            card.getChildren().add(noteLabel);
        }
    }

    private void addLaterCourses(VBox card, KitchenTableDTO table, Course current) {
        String later = withStatus(table, OrderStatus.NEU).stream()
                .map(OrderDTO::course)
                .filter(course -> course != current)
                .distinct()
                .map(Course::toString)
                .collect(Collectors.joining(", "));
        if (!later.isEmpty()) {
            Label hint = new Label("Danach: " + later);
            hint.getStyleClass().add("next-course");
            hint.setWrapText(true);
            card.getChildren().add(hint);
        }
    }

    private List<OrderDTO> withStatus(KitchenTableDTO table, OrderStatus status) {
        return table.orders().stream().filter(order -> order.status() == status).toList();
    }

    private List<OrderDTO> ofCourse(List<OrderDTO> orders, Course course) {
        return orders.stream().filter(order -> order.course() == course).toList();
    }

    private String courseNames(List<OrderDTO> orders) {
        return orders.stream().map(OrderDTO::course).distinct().map(Course::toString)
                .collect(Collectors.joining(" + "));
    }

    private String itemsText(List<OrderDTO> orders) {
        return orders.stream().map(OrderDTO::getItemsText).collect(Collectors.joining(", "));
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
