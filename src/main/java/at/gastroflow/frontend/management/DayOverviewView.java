package at.gastroflow.frontend.management;

import at.gastroflow.backend.model.Reservation;
import at.gastroflow.backend.model.ReservationStatus;
import at.gastroflow.backend.model.RestaurantSettings;
import at.gastroflow.backend.service.AnalyticsService;
import at.gastroflow.backend.service.ReservationService;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Tagesübersicht: was passiert heute im Restaurant (Live-Timeline). */
public class DayOverviewView {

    private static final double HOUR_WIDTH = 84;
    private static final double LANE_HEIGHT = 44;
    private static final double LABEL_WIDTH = 80;

    private final VBox content = new VBox(24);

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        RestaurantSettings settings = RestaurantSettings.get();
        List<Reservation> reservations = ReservationService.getReservationsOn(today);

        int presentGuests = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.ANWESEND)
                .mapToInt(Reservation::getPersons).sum();
        int occupied = AnalyticsService.occupiedTables(now).size();
        long upcoming = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.BESTAETIGT && r.getStart().isAfter(now))
                .count();

        content.getChildren().setAll(
                Ui.header("Tagesübersicht", "Heute, " + today.format(Ui.DATE) + " · Stand " + now.format(Ui.TIME) + " Uhr",
                        Ui.secondary("Aktualisieren", this::render),
                        Ui.primary("+ Reservierung", () -> {
                            if (ReservationDialog.show(null, today)) {
                                render();
                            }
                        })),
                Ui.cards(
                        Ui.statCard("ANWESENDE GÄSTE", String.valueOf(presentGuests), "Mit Reservierung angekommen"),
                        Ui.statCard("BELEGTE TISCHE", String.valueOf(occupied), "Gäste oder offene Bestellung"),
                        Ui.statCard("FREIE TISCHE", String.valueOf(settings.getTableCount() - occupied),
                                "Von " + settings.getTableCount() + " Tischen"),
                        Ui.statCard("KOMMENDE RESERVIERUNGEN", String.valueOf(upcoming), "Heute noch erwartet"),
                        Ui.statCard("AUSLASTUNG", Ui.percent(occupied / (double) settings.getTableCount()),
                                "Belegte Tische jetzt")),
                timelinePanel(reservations, now),
                listPanel(reservations, now)
        );
    }

    // ---------- Timeline ----------

    private Node timelinePanel(List<Reservation> reservations, LocalDateTime now) {
        RestaurantSettings settings = RestaurantSettings.get();
        LocalDateTime dayStart = now.toLocalDate().atTime(settings.getOpeningTime());
        LocalDateTime dayEnd = now.toLocalDate().atTime(settings.getClosingTime());
        int hours = settings.getClosingTime().getHour() - settings.getOpeningTime().getHour();
        double width = hours * HOUR_WIDTH;

        VBox rows = new VBox(6);
        rows.setPadding(new javafx.geometry.Insets(0, 0, 18, 0)); // Platz für die Scrollleiste

        // Kopfzeile mit Uhrzeiten
        Pane hourLabels = new Pane();
        hourLabels.setPrefSize(width, 20);
        for (int h = 0; h <= hours; h++) {
            Label label = new Label((settings.getOpeningTime().getHour() + h) + ":00");
            label.getStyleClass().add("timeline-hour");
            label.setLayoutX(h * HOUR_WIDTH - (h == 0 ? 0 : 14));
            hourLabels.getChildren().add(label);
        }
        rows.getChildren().add(new HBox(Ui.cell("", LABEL_WIDTH), hourLabels));

        // Eine Zeile pro Tisch
        for (int table = 1; table <= settings.getTableCount(); table++) {
            Pane lane = new Pane();
            lane.getStyleClass().add("timeline-lane");
            lane.setPrefSize(width, LANE_HEIGHT);
            lane.setMinSize(width, LANE_HEIGHT);

            for (int h = 1; h < hours; h++) {
                Region line = new Region();
                line.getStyleClass().add("timeline-grid");
                line.setLayoutX(h * HOUR_WIDTH);
                line.setPrefSize(1, LANE_HEIGHT);
                lane.getChildren().add(line);
            }

            for (Reservation reservation : reservations) {
                if (reservation.getTable() == table) {
                    lane.getChildren().add(block(reservation, dayStart, dayEnd, now));
                }
            }

            // Rote Linie für "jetzt"
            if (!now.isBefore(dayStart) && now.isBefore(dayEnd)) {
                Region nowLine = new Region();
                nowLine.getStyleClass().add("timeline-now");
                nowLine.setLayoutX(minutesBetween(dayStart, now) / 60.0 * HOUR_WIDTH);
                nowLine.setPrefSize(2, LANE_HEIGHT);
                lane.getChildren().add(nowLine);
            }

            Label tableLabel = Ui.cell("Tisch " + table, LABEL_WIDTH);
            tableLabel.getStyleClass().add("strong");
            HBox row = new HBox(tableLabel, lane);
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("timeline-scroll");

        HBox legend = new HBox(8,
                Ui.badge("Anwesend", "green"), Ui.badge("Bestätigt", "blue"), Ui.badge("Bald erwartet", "orange"),
                Ui.badge("Abgeschlossen", "gray"), Ui.badge("No-Show / Überfällig", "red"));
        legend.setAlignment(Pos.CENTER_LEFT);

        return Ui.panel("Timeline " + settings.getOpeningTime().format(Ui.TIME) + "–"
                        + settings.getClosingTime().format(Ui.TIME) + " Uhr",
                legend, Ui.muted("Klick auf eine Reservierung, um sie zu bearbeiten."), scroll);
    }

    private Node block(Reservation reservation, LocalDateTime dayStart, LocalDateTime dayEnd, LocalDateTime now) {
        LocalDateTime start = reservation.getStart().isBefore(dayStart) ? dayStart : reservation.getStart();
        LocalDateTime end = reservation.getEnd().isAfter(dayEnd) ? dayEnd : reservation.getEnd();
        double x = minutesBetween(dayStart, start) / 60.0 * HOUR_WIDTH;
        double w = Math.max(24, minutesBetween(start, end) / 60.0 * HOUR_WIDTH - 3);

        Label text = new Label(reservation.getName() + " · " + reservation.getPersons() + " P.");
        text.getStyleClass().add("timeline-text");
        VBox block = new VBox(text);
        block.setAlignment(Pos.CENTER_LEFT);
        block.getStyleClass().addAll("timeline-block", "block-" + Ui.reservationColor(reservation, now));
        block.setLayoutX(x + 1);
        block.setLayoutY(5);
        block.setPrefSize(w, LANE_HEIGHT - 10);
        block.setMaxWidth(w);

        Tooltip.install(block, new Tooltip(ReservationDialog.details(reservation, now)));

        block.setOnMouseClicked(event -> {
            if (ReservationDialog.show(reservation, reservation.getStart().toLocalDate())) {
                render();
            }
        });
        return block;
    }

    private long minutesBetween(LocalDateTime from, LocalDateTime to) {
        return Duration.between(from, to).toMinutes();
    }

    // ---------- Liste mit Aktionen ----------

    private Node listPanel(List<Reservation> reservations, LocalDateTime now) {
        VBox rows = new VBox();
        if (reservations.isEmpty()) {
            rows.getChildren().add(Ui.muted("Heute gibt es keine Reservierungen."));
        }
        for (Reservation reservation : reservations) {
            rows.getChildren().add(reservationRow(reservation, now));
        }
        return Ui.panel("Reservierungen heute",
                Ui.headingRow(Ui.cell("ZEIT", 100), Ui.growCell("GAST / TELEFON"), Ui.cell("PERS.", 50),
                        Ui.cell("TISCH", 50), Ui.cell("STATUS", 120), Ui.cell("AKTIONEN", 390)),
                rows);
    }

    private HBox reservationRow(Reservation reservation, LocalDateTime now) {
        HBox actions = new HBox(6);
        actions.setMinWidth(390);

        if (reservation.getStatus() == ReservationStatus.BESTAETIGT) {
            actions.getChildren().addAll(
                    Ui.small("Angekommen", () -> changeStatus(reservation, ReservationStatus.ANWESEND)),
                    Ui.small("No-Show", () -> changeStatus(reservation, ReservationStatus.NO_SHOW)));
        } else if (reservation.getStatus() == ReservationStatus.ANWESEND) {
            actions.getChildren().add(
                    Ui.small("Abschließen", () -> changeStatus(reservation, ReservationStatus.ABGESCHLOSSEN)));
        }
        actions.getChildren().addAll(
                Ui.small("Tisch", () -> {
                    if (ReservationDialog.assignTable(reservation)) {
                        render();
                    }
                }),
                Ui.small("Bearbeiten", () -> {
                    if (ReservationDialog.show(reservation, reservation.getStart().toLocalDate())) {
                        render();
                    }
                }),
                Ui.small("Stornieren", () -> {
                    if (ReservationDialog.cancel(reservation)) {
                        render();
                    }
                }));

        Label name = new Label(reservation.getName());
        name.getStyleClass().add("strong");
        Label phone = Ui.muted(reservation.hasPhone() ? reservation.getPhone() : "Keine Telefonnummer hinterlegt");
        VBox nameBox = new VBox(2, new HBox(6, name, Ui.phoneButton(reservation.getName(), reservation.getPhone())), phone);
        if (reservation.getNote() != null && !reservation.getNote().isBlank()) {
            nameBox.getChildren().add(Ui.muted("Notiz: " + reservation.getNote()));
        }
        nameBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameBox, javafx.scene.layout.Priority.ALWAYS);

        return Ui.row(
                Ui.cell(reservation.getStart().format(Ui.TIME) + "–" + reservation.getEnd().format(Ui.TIME), 100),
                nameBox,
                Ui.cell(String.valueOf(reservation.getPersons()), 50),
                Ui.cell(String.valueOf(reservation.getTable()), 50),
                Ui.cellBox(Ui.badge(Ui.reservationLabel(reservation, now), Ui.reservationColor(reservation, now)), 120),
                actions);
    }

    private void changeStatus(Reservation reservation, ReservationStatus status) {
        try {
            ReservationService.setStatus(reservation, status);
            render();
        } catch (IllegalArgumentException e) {
            Ui.error(e.getMessage());
        }
    }
}
