package at.gastroflow.frontend.management;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Locale;

public class CalendarView {

    private static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern(
                    "EEEE, dd.MM.yyyy", Locale.GERMAN
            );

    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern(
                    "MMMM yyyy", Locale.GERMAN
            );

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
                Ui.segment(
                        List.of("Tag", "Woche", "Monat"),
                        mode,
                        option -> {
                            mode = option;
                            render();
                        }
                ),
                Ui.spacer(),
                period,
                Ui.spacer(),
                Ui.secondary("‹", () -> move(-1)),
                Ui.secondary("Heute", () -> {
                    date = LocalDate.now();
                    render();
                }),
                Ui.secondary("›", () -> move(1))
        );

        toolbar.setAlignment(Pos.CENTER_LEFT);

        Node body = switch (mode) {
            case "Tag" -> dayView();
            case "Monat" -> monthView();
            default -> weekView();
        };

        Button reservationButton = new Button("+ Reservierung");
        reservationButton.getStyleClass().add("primary-button");
        reservationButton.setDisable(true);

        content.getChildren().setAll(
                Ui.header(
                        "Kalender",
                        "Alle Reservierungen planen und verwalten",
                        reservationButton
                ),
                Ui.muted("Reservierungsverwaltung noch nicht angebunden."),
                Ui.panel(null, toolbar, body)
        );
    }

    private String periodText() {
        return switch (mode) {
            case "Tag" -> LONG_DATE.format(date);
            case "Monat" -> MONTH.format(date);
            default -> {
                LocalDate monday = date.with(DayOfWeek.MONDAY);

                yield "KW "
                        + monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                        + " · "
                        + monday.format(Ui.DAY_MONTH)
                        + " – "
                        + monday.plusDays(6).format(Ui.DATE);
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

    private Node dayView() {
        VBox rows = new VBox();

        rows.getChildren().add(
                Ui.muted("Reservierungen derzeit nicht verfügbar.")
        );

        return new VBox(
                Ui.headingRow(
                        Ui.cell("ZEIT", 100),
                        Ui.growCell("NAME"),
                        Ui.cell("PERSONEN", 70),
                        Ui.cell("TISCH", 70),
                        Ui.cell("STATUS", 120),
                        Ui.cell("", 170)
                ),
                rows
        );
    }

    private Node weekView() {
        LocalDate monday = date.with(DayOfWeek.MONDAY);
        HBox columns = new HBox(8);

        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);

            Label title = new Label(
                    Ui.WEEKDAYS[i] + " "
                            + day.format(Ui.DAY_MONTH)
            );

            title.getStyleClass().add("strong");

            Button addButton = new Button("+");
            addButton.getStyleClass().add("small-action");
            addButton.setDisable(true);

            HBox head = new HBox(
                    4, title, Ui.spacer(), addButton
            );

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

            column.getChildren().add(Ui.muted("–"));

            columns.getChildren().add(column);
        }

        return columns;
    }

    private Node monthView() {
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

                Label number = new Label(
                        String.valueOf(day.getDayOfMonth())
                );

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

                cell.setOnMouseClicked(event -> {
                    date = day;
                    mode = "Tag";
                    render();
                });

                grid.add(cell, i, week + 1);
            }
        }

        return new VBox(
                10,
                Ui.muted("Klick auf einen Tag öffnet die Tagesansicht."),
                grid
        );
    }
}
