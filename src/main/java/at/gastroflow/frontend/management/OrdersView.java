package at.gastroflow.frontend.management;

import at.gastroflow.backend.model.Ingredient;
import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderItem;
import at.gastroflow.backend.model.OrderStatus;
import at.gastroflow.backend.model.Product;
import at.gastroflow.backend.model.RestaurantSettings;
import at.gastroflow.backend.service.InventoryService;
import at.gastroflow.backend.service.MenuService;
import at.gastroflow.backend.service.OrderService;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Bestellungsübersicht: alle Bestellungen nach Status, verzögerte und knappe Zutaten hervorgehoben. */
public class OrdersView {

    private static final String ALL = "Alle";

    private final VBox content = new VBox(24);
    private String filter = ALL;

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {
        LocalDateTime now = LocalDateTime.now();
        int delay = RestaurantSettings.get().getDelayMinutes();

        // Offene Bestellungen + heute abgeschlossene/stornierte
        List<Order> orders = new ArrayList<>(OrderService.getOpenOrders());
        for (Order order : OrderService.getOrdersOn(LocalDate.now())) {
            if (!order.isOpen()) {
                orders.add(order);
            }
        }
        orders.sort(Comparator.comparing(Order::getCreatedAt).reversed());

        long delayed = orders.stream().filter(o -> isDelayed(o, now, delay)).count();

        List<String> options = new ArrayList<>();
        options.add(ALL);
        for (OrderStatus status : OrderStatus.values()) {
            options.add(status.toString());
        }

        VBox rows = new VBox();
        List<Order> visible = orders.stream()
                .filter(o -> filter.equals(ALL) || o.getStatus().toString().equals(filter))
                .toList();
        if (visible.isEmpty()) {
            rows.getChildren().add(Ui.muted("Keine Bestellungen in diesem Bereich."));
        }
        for (Order order : visible) {
            rows.getChildren().add(orderRow(order, now, delay));
        }

        content.getChildren().setAll(
                Ui.header("Bestellungen", "Offene Bestellungen und alle Bestellungen von heute",
                        Ui.secondary("Aktualisieren", this::render),
                        Ui.secondary("+ Testbestellung", this::openTestOrderDialog)),
                Ui.cards(
                        Ui.statCard("NEU", count(orders, OrderStatus.NEU), "Warten auf die Küche"),
                        Ui.statCard("IN ZUBEREITUNG", count(orders, OrderStatus.IN_ZUBEREITUNG), "Werden gerade zubereitet"),
                        Ui.statCard("FERTIG", count(orders, OrderStatus.FERTIG), "Bereit zum Servieren"),
                        Ui.statCard("ABGESCHLOSSEN", count(orders, OrderStatus.ABGESCHLOSSEN), "Heute erledigt"),
                        Ui.statCard("VERZÖGERT", String.valueOf(delayed), "Länger als " + delay + " Minuten offen")),
                Ui.panel(null,
                        Ui.segment(options, filter, option -> {
                            filter = option;
                            render();
                        }),
                        Ui.muted("Rot markiert: offene Bestellungen, die länger als " + delay
                                + " Minuten warten (Grenze unter Einstellungen änderbar). ⚠ = Bestellung nutzt knappe Zutaten."),
                        Ui.headingRow(Ui.cell("NR.", 60), Ui.cell("TISCH", 42), Ui.cell("KELLNER", 110),
                                Ui.cell("ZEIT", 46), Ui.growCell("PRODUKTE"), Ui.cell("GESAMT", 80),
                                Ui.cell("STATUS", 112), Ui.cell("WARTEZEIT", 78), Ui.cell("", 90)),
                        rows)
        );
    }

    private HBox orderRow(Order order, LocalDateTime now, int delay) {
        Label number = Ui.cell("#" + order.getNumber(), 60);
        number.getStyleClass().add("strong");
        Label total = Ui.cell(Ui.money(order.getTotal()), 80);
        total.getStyleClass().add("strong");

        Label duration;
        if (!order.isOpen()) {
            duration = Ui.cell("–", 78);
        } else if (isDelayed(order, now, delay)) {
            duration = Ui.cell("⚠ " + order.getMinutesSinceCreated(now) + " min", 78);
            duration.getStyleClass().add("delay-text");
        } else {
            duration = Ui.cell(order.getMinutesSinceCreated(now) + " min", 78);
        }

        // Produkte + interne Lager-Hinweise
        Label items = new Label(order.getItemsText());
        items.setWrapText(true);
        VBox productBox = new VBox(3, items);
        if (order.isOpen()) {
            List<Ingredient> scarce = InventoryService.scarceIngredientsFor(order);
            if (!scarce.isEmpty()) {
                Label hint = new Label("⚠ Knappe Zutaten: " + String.join(", ",
                        scarce.stream().map(Ingredient::getName).toList()));
                hint.getStyleClass().add("warn-text");
                hint.setWrapText(true);
                productBox.getChildren().add(hint);
            }
        }
        Optional<String> problem = InventoryService.problemFor(order);
        if (problem.isPresent()) {
            Label hint = new Label(problem.get());
            hint.getStyleClass().add("error-text");
            hint.setWrapText(true);
            productBox.getChildren().add(hint);
        }
        productBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(productBox, Priority.ALWAYS);

        HBox action = new HBox();
        action.setMinWidth(90);
        if (order.isOpen()) {
            action.getChildren().add(Ui.small("Stornieren", () -> cancel(order)));
        }

        HBox row = Ui.row(number,
                Ui.cell(String.valueOf(order.getTable()), 42),
                Ui.cell(order.getWaiter(), 110),
                Ui.cell(order.getCreatedAt().format(Ui.TIME), 46),
                productBox,
                total,
                Ui.cellBox(Ui.badge(order.getStatus().toString(), Ui.orderColor(order.getStatus())), 112),
                duration,
                action);
        if (isDelayed(order, now, delay)) {
            row.getStyleClass().add("row-delayed");
        }
        return row;
    }

    private void cancel(Order order) {
        boolean beforePreparation = order.getStatus() == OrderStatus.NEU;
        String text = "Bestellung #" + order.getNumber() + " stornieren?\n"
                + (beforePreparation
                ? "Sie ist noch nicht in Zubereitung – gebuchte Zutaten werden ins Lager zurückgebucht."
                : "Sie ist bereits „" + order.getStatus() + "“ – die Zutaten gelten als verbraucht (keine Rückbuchung).");
        if (!Ui.confirm(text, "Stornieren")) {
            return;
        }
        boolean restored = OrderService.cancel(order);
        Ui.info(restored ? "Storniert. Die Zutaten wurden ins Lager zurückgebucht."
                : "Storniert. Es wurde nichts zurückgebucht.");
        render();
    }

    /** Testbestellung der Leitung, um die automatische Lagerbuchung auszuprobieren. */
    private void openTestOrderDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Testbestellung");
        dialog.setHeaderText("Testbestellung anlegen (wird wie eine echte Bestellung gebucht)");
        ButtonType save = new ButtonType("Bestellung anlegen", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<Product> product = new ComboBox<>(FXCollections.observableArrayList(MenuService.getAvailableProducts()));
        product.setValue(MenuService.findByName("Classic Burger") != null && MenuService.findByName("Classic Burger").isAvailable()
                ? MenuService.findByName("Classic Burger") : product.getItems().isEmpty() ? null : product.getItems().get(0));
        product.setPrefWidth(280);
        Spinner<Integer> quantity = new Spinner<>(1, 50, 3);
        ComboBox<Integer> table = new ComboBox<>();
        for (int i = 1; i <= RestaurantSettings.get().getTableCount(); i++) {
            table.getItems().add(i);
        }
        table.setValue(1);

        Label preview = new Label();
        preview.setWrapText(true);
        Runnable update = () -> {
            if (product.getValue() == null) {
                preview.setText("");
                return;
            }
            Map<Integer, Long> need = InventoryService.requiredFor(
                    List.of(new OrderItem(product.getValue(), quantity.getValue())));
            Integer portions = InventoryService.portionsFor(product.getValue());
            StringBuilder text = new StringBuilder(need.isEmpty() ? "Keine Rezeptur – es wird nichts vom Lager abgebucht."
                    : "Wird abgebucht: ");
            List<String> parts = new ArrayList<>();
            need.forEach((id, amount) -> InventoryService.findIngredient(id).ifPresent(i ->
                    parts.add(i.getUnit().baseUnit().format(amount) + " " + i.getName())));
            text.append(String.join(", ", parts));
            if (portions != null) {
                text.append("\nMöglich mit aktuellem Lager: ").append(portions).append(" Portionen.");
                if (quantity.getValue() > portions) {
                    text.append(" Zu wenig Bestand – die Bestellung wird angelegt, aber nicht abgebucht.");
                }
            }
            preview.setText(text.toString());
        };
        product.valueProperty().addListener((obs, o, n) -> update.run());
        quantity.valueProperty().addListener((obs, o, n) -> update.run());
        update.run();

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(12);
        form.addRow(0, new Label("Produkt"), product);
        form.addRow(1, new Label("Menge"), quantity);
        form.addRow(2, new Label("Tisch"), table);

        VBox box = new VBox(14, Ui.demoHint("Nur zum Testen der Lagerbuchung in der Leitung."), form, preview);
        box.setPrefWidth(480);
        dialog.getDialogPane().setContent(box);
        Ui.styleDialog(dialog);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != save || product.getValue() == null) {
            return;
        }
        Order order = new Order(table.getValue(), "Leitung (Test)", LocalDateTime.now(), quantity.getValue(),
                List.of(new OrderItem(product.getValue(), quantity.getValue())), OrderStatus.NEU);
        OrderService.send(order);
        Ui.info(InventoryService.problemFor(order).orElse("Bestellung #" + order.getNumber()
                + " angelegt. Die Zutaten wurden automatisch vom Lager abgebucht (siehe Bestand & Lager → Lagerhistorie)."));
        render();
    }

    private boolean isDelayed(Order order, LocalDateTime now, int delay) {
        return order.isOpen() && order.getMinutesSinceCreated(now) > delay;
    }

    private String count(List<Order> orders, OrderStatus status) {
        return String.valueOf(orders.stream().filter(o -> o.getStatus() == status).count());
    }
}
