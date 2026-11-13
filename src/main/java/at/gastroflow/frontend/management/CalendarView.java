package at.gastroflow.frontend.management;

import at.gastroflow.backend.model.Reservation;
import at.gastroflow.backend.service.ReservationService;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Locale;

/** Kalender mit Tages-, Wochen- und Monatsansicht für Reservierungen. */
public class CalendarView {

    private static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN);
    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN);

    private final VBox content = new VBox(24);
    private String mode = "Woche";
    private LocalDate date = LocalDate.now();

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {
        Label period = new Label(periodText());
        period.getStyleClass().add("section-title");

        HBox toolbar = new HBox(10,
                Ui.segment(List.of("Tag", "Woche", "Monat"), mode, option -> {
                    mode = option;
                    render();
                }),
                Ui.spacer(), period, Ui.spacer(),
                Ui.secondary("‹", () -> move(-1)),
                Ui.secondary("Heute", () -> {
                    date = LocalDate.now();
                    render();
                }),
                Ui.secondary("›", () -> move(1)));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Node body = switch (mode) {
            case "Tag" -> dayView();
            case "Monat" -> monthView();
            default -> weekView();
        };

        content.getChildren().setAll(
                Ui.header("Kalender", "Alle Reservierungen planen und verwalten",
                        Ui.primary("+ Reservierung", () -> create(date))),
                Ui.demoHint("Die Reservierungen für heute und die nächsten sieben Tage sind Beispiele."),
                Ui.panel(null, toolbar, body));
    }

    private String periodText() {
        return switch (mode) {
            case "Tag" -> LONG_DATE.format(date);
            case "Monat" -> MONTH.format(date);
            default -> {
                LocalDate monday = date.with(DayOfWeek.MONDAY);
                yield "KW " + monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + " · "
                        + monday.format(Ui.DAY_MONTH) + " – " + monday.plusDays(6).format(Ui.DATE);
            }
        };
    }

    private void move(int direction) {
        date = switch (mode) {
            case "Tag" -> date.plusDays(direction);
            case "Monat" -> date.plusMonths(direction);
            default -> date.plusWeeks(direction);
        };
        render();
    }

    private void create(LocalDate day) {
        if (ReservationDialog.show(null, day)) {
            render();
        }
    }

    private void edit(Reservation reservation) {
        if (ReservationDialog.show(reservation, reservation.getStart().toLocalDate())) {
            render();
        }
    }

    // ---------- Tagesansicht ----------

    private Node dayView() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> reservations = ReservationService.getReservationsOn(date);

        VBox rows = new VBox();
        if (reservations.isEmpty()) {
            rows.getChildren().add(Ui.muted("Keine Reservierungen an diesem Tag."));
        }
        for (Reservation reservation : reservations) {
            Label nameLabel = new Label(reservation.getName());
            nameLabel.getStyleClass().add("strong");
            HBox name = new HBox(6, nameLabel, Ui.phoneButton(reservation.getName(), reservation.getPhone()));
            name.setAlignment(Pos.CENTER_LEFT);
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            HBox actions = new HBox(6,
                    Ui.small("Bearbeiten", () -> edit(reservation)),
                    Ui.small("Löschen", () -> {
                        if (ReservationDialog.cancel(reservation)) {
                            render();
                        }
                    }));
            actions.setMinWidth(170);
            rows.getChildren().add(Ui.row(
                    Ui.cell(reservation.getStart().format(Ui.TIME) + "–" + reservation.getEnd().format(Ui.TIME), 100),
                    name,
                    Ui.cell(reservation.getPersons() + " Pers.", 70),
                    Ui.cell("Tisch " + reservation.getTable(), 70),
                    Ui.cellBox(Ui.badge(Ui.reservationLabel(reservation, now),
                            Ui.reservationColor(reservation, now)), 120),
                    actions));
        }
        return new VBox(
                Ui.headingRow(Ui.cell("ZEIT", 100), Ui.growCell("NAME"), Ui.cell("PERSONEN", 70),
                        Ui.cell("TISCH", 70), Ui.cell("STATUS", 120), Ui.cell("", 170)),
                rows);
    }

    // ---------- Wochenansicht ----------

    private Node weekView() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate monday = date.with(DayOfWeek.MONDAY);
        HBox columns = new HBox(8);

        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);

            Label title = new Label(Ui.WEEKDAYS[i] + " " + day.format(Ui.DAY_MONTH));
            title.getStyleClass().add("strong");
            HBox head = new HBox(4, title, Ui.spacer(), Ui.small("+", () -> create(day)));
            head.setAlignment(Pos.CENTER_LEFT);

            VBox column = new VBox(8, head);
            column.getStyleClass().add("calendar-column");
            if (day.equals(LocalDate.now())) {
                column.getStyleClass().add("calendar-today");
            }
            column.setPrefWidth(100);
            column.setMinHeight(320);
            column.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(column, Priority.ALWAYS);

            List<Reservation> reservations = ReservationService.getReservationsOn(day);
            if (reservations.isEmpty()) {
                column.getChildren().add(Ui.muted("–"));
            }
            for (Reservation reservation : reservations) {
                Label time = new Label(reservation.getStart().format(Ui.TIME));
                time.getStyleClass().add("strong");
                Label name = new Label(reservation.getName());
                name.setWrapText(true);
                Label details = Ui.muted(reservation.getPersons() + " P. · Tisch " + reservation.getTable());

                VBox entry = new VBox(2, time, name, details);
                entry.getStyleClass().addAll("calendar-entry", "entry-" + Ui.reservationColor(reservation, now));
                javafx.scene.control.Tooltip.install(entry,
                        new javafx.scene.control.Tooltip(ReservationDialog.details(reservation, now)));
                entry.setOnMouseClicked(event -> edit(reservation));
                column.getChildren().add(entry);
            }
            columns.getChildren().add(column);
        }
        return columns;
    }

    // ---------- Monatsansicht ----------

    private Node monthView() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate first = date.withDayOfMonth(1);
        LocalDate start = first.with(DayOfWeek.MONDAY);

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 7);
            grid.getColumnConstraints().add(column);

            Label weekday = new Label(Ui.WEEKDAYS[i]);
            weekday.getStyleClass().add("stat-title");
            grid.add(weekday, i, 0);
        }

        for (int week = 0; week < 6; week++) {
            for (int i = 0; i < 7; i++) {
                LocalDate day = start.plusDays(week * 7L + i);

                Label number = new Label(String.valueOf(day.getDayOfMonth()));
                number.getStyleClass().add("strong");
                VBox cell = new VBox(4, number);
                cell.getStyleClass().add("calendar-cell");
                cell.setMinHeight(96);
                cell.setMaxWidth(Double.MAX_VALUE);
                if (!day.getMonth().equals(date.getMonth())) {
                    cell.getStyleClass().add("calendar-other-month");
                }
                if (day.equals(LocalDate.now())) {
                    cell.getStyleClass().add("calendar-today");
                }

                List<Reservation> reservations = ReservationService.getReservationsOn(day);
                for (int r = 0; r < Math.min(3, reservations.size()); r++) {
                    Reservation reservation = reservations.get(r);
                    Label chip = new Label(reservation.getStart().format(Ui.TIME) + " "
                            + lastName(reservation.getName()) + " (" + reservation.getPersons() + ")");
                    chip.getStyleClass().addAll("calendar-chip", "entry-" + Ui.reservationColor(reservation, now));
                    chip.setMaxWidth(Double.MAX_VALUE);
                    cell.getChildren().add(chip);
                }
                if (reservations.size() > 3) {
                    cell.getChildren().add(Ui.muted("+" + (reservations.size() - 3) + " weitere"));
                }

                cell.setOnMouseClicked(event -> {
                    date = day;
                    mode = "Tag";
                    render();
                });
                grid.add(cell, i, week + 1);
            }
        }
        return new VBox(10, Ui.muted("Klick auf einen Tag öffnet die Tagesansicht."), grid);
    }

    private String lastName(String name) {
        String[] parts = name.trim().split(" ");
        return parts[parts.length - 1];
    }
}
