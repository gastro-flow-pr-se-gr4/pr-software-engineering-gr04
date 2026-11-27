package at.gastroflow.frontend.management;

import at.gastroflow.backend.dto.OrderDTO;
import at.gastroflow.backend.model.OrderStatus;
import at.gastroflow.frontend.api.ApiException;
import at.gastroflow.frontend.api.OrderClient;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OrdersView {

    private static final String ALL = "Alle";

    // Vorübergehend, bis die Einstellungen angebunden sind.
    private static final int DELAY_MINUTES = 30;

    private final VBox content = new VBox(24);
    private String filter = ALL;

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {

        LocalDateTime now = LocalDateTime.now();

        List<OrderDTO> orders = new ArrayList<>();
        String loadError = null;

        try {
            orders.addAll(OrderClient.openOrders());

            for (OrderDTO order :
                    OrderClient.ordersOn(LocalDate.now())) {

                if (!order.isOpen()) {
                    orders.add(order);
                }
            }

        } catch (ApiException e) {
            loadError = "Bestellungen nicht verfügbar: "
                    + e.getMessage();
        }

        orders.sort(
                Comparator.comparing(OrderDTO::createdAt)
                        .reversed()
        );

        boolean loaded = loadError == null;

        long delayed = orders.stream()
                .filter(o -> isDelayed(o, now))
                .count();

        List<String> options = new ArrayList<>();
        options.add(ALL);

        for (OrderStatus status : OrderStatus.values()) {
            options.add(status.toString());
        }

        VBox rows = new VBox();

        List<OrderDTO> visible = orders.stream()
                .filter(o -> filter.equals(ALL)
                        || o.status().toString().equals(filter))
                .toList();

        if (loadError != null) {
            rows.getChildren().add(Ui.muted(loadError));

        } else if (visible.isEmpty()) {
            rows.getChildren().add(
                    Ui.muted("Keine Bestellungen in diesem Bereich.")
            );
        }

        for (OrderDTO order : visible) {
            rows.getChildren().add(orderRow(order, now));
        }

        content.getChildren().setAll(

                Ui.header(
                        "Bestellungen",
                        "Offene Bestellungen und alle Bestellungen von heute",
                        Ui.secondary("Aktualisieren", this::render)
                ),

                Ui.cards(
                        Ui.statCard(
                                "NEU",
                                loaded ? count(orders, OrderStatus.NEU) : "–",
                                "Warten auf die Küche"
                        ),
                        Ui.statCard(
                                "IN ZUBEREITUNG",
                                loaded ? count(orders, OrderStatus.IN_ZUBEREITUNG) : "–",
                                "Werden gerade zubereitet"
                        ),
                        Ui.statCard(
                                "FERTIG",
                                loaded ? count(orders, OrderStatus.FERTIG) : "–",
                                "Bereit zum Servieren"
                        ),
                        Ui.statCard(
                                "ABGESCHLOSSEN",
                                loaded ? count(orders, OrderStatus.ABGESCHLOSSEN) : "–",
                                "Heute erledigt"
                        ),
                        Ui.statCard(
                                "VERZÖGERT",
                                loaded ? String.valueOf(delayed) : "–",
                                "Länger als " + DELAY_MINUTES
                                        + " Minuten offen"
                        )
                ),

                Ui.panel(
                        null,

                        Ui.segment(
                                options,
                                filter,
                                option -> {
                                    filter = option;
                                    render();
                                }
                        ),

                        Ui.muted(
                                "Rot markiert: offene Bestellungen, "
                                        + "die länger als "
                                        + DELAY_MINUTES
                                        + " Minuten warten."
                        ),

                        Ui.headingRow(
                                Ui.cell("NR.", 60),
                                Ui.cell("TISCH", 42),
                                Ui.cell("KELLNER", 110),
                                Ui.cell("ZEIT", 46),
                                Ui.growCell("PRODUKTE"),
                                Ui.cell("GESAMT", 80),
                                Ui.cell("STATUS", 112),
                                Ui.cell("WARTEZEIT", 78),
                                Ui.cell("", 90)
                        ),

                        rows
                )
        );
    }

    private HBox orderRow(
            OrderDTO order,
            LocalDateTime now
    ) {

        Label number = Ui.cell(
                "#" + order.number(), 60
        );

        number.getStyleClass().add("strong");

        Label total = Ui.cell(
                Ui.money(order.total().doubleValue()), 80
        );

        total.getStyleClass().add("strong");

        Label duration;

        if (!order.isOpen()) {

            duration = Ui.cell("–", 78);

        } else if (isDelayed(order, now)) {

            duration = Ui.cell(
                    "⚠ " + order.getMinutesSinceCreated(now)
                            + " min",
                    78
            );

            duration.getStyleClass().add("delay-text");

        } else {

            duration = Ui.cell(
                    order.getMinutesSinceCreated(now)
                            + " min",
                    78
            );
        }

        Label items = new Label(
                order.course() + ": " + order.getItemsText()
        );

        items.setWrapText(true);

        VBox productBox = new VBox(3, items);

        productBox.setMaxWidth(Double.MAX_VALUE);

        HBox.setHgrow(productBox, Priority.ALWAYS);

        HBox action = new HBox();
        action.setMinWidth(90);

        if (order.isOpen() && !order.isPaid()) {

            action.getChildren().add(
                    Ui.small(
                            "Stornieren",
                            () -> cancel(order)
                    )
            );
        }

        HBox row = Ui.row(
                number,
                Ui.cell(String.valueOf(order.table()), 42),
                Ui.cell(order.waiter(), 110),
                Ui.cell(order.createdAt().format(Ui.TIME), 46),
                productBox,
                total,

                Ui.cellBox(
                        Ui.badge(
                                order.status().toString(),
                                Ui.orderColor(order.status())
                        ),
                        112
                ),

                duration,
                action
        );

        if (isDelayed(order, now)) {
            row.getStyleClass().add("row-delayed");
        }

        return row;
    }

    private void cancel(OrderDTO order) {

        String text = "Bestellung #"
                + order.number()
                + " stornieren?";

        if (!Ui.confirm(text, "Stornieren")) {
            return;
        }

        try {

            OrderClient.cancel(order.id());

            Ui.info("Bestellung wurde storniert.");

        } catch (ApiException e) {

            Ui.error(e.getMessage());
        }

        render();
    }

    private boolean isDelayed(
            OrderDTO order,
            LocalDateTime now
    ) {

        return order.isOpen()
                && order.getMinutesSinceCreated(now)
                > DELAY_MINUTES;
    }

    private String count(
            List<OrderDTO> orders,
            OrderStatus status
    ) {

        return String.valueOf(
                orders.stream()
                        .filter(o -> o.status() == status)
                        .count()
        );
    }
}
