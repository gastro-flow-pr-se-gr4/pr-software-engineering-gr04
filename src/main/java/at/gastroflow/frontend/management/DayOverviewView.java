package at.gastroflow.frontend.management;

import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class DayOverviewView {

    private static final double HOUR_WIDTH = 84;
    private static final double LANE_HEIGHT = 44;
    private static final double LABEL_WIDTH = 80;

    // Vorübergehende UI-Konfiguration
    private static final int TABLE_COUNT = 12;
    private static final int OPENING_HOUR = 10;
    private static final int CLOSING_HOUR = 22;

    private final VBox content = new VBox(24);

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        Button reservationButton = new Button("+ Reservierung");
        reservationButton.getStyleClass().add("primary-button");
        reservationButton.setDisable(true);

        content.getChildren().setAll(
                Ui.header(
                        "Tagesübersicht",
                        "Heute, " + today.format(Ui.DATE)
                                + " · Stand "
                                + now.format(Ui.TIME) + " Uhr",
                        Ui.secondary("Aktualisieren", this::render),
                        reservationButton
                ),

                Ui.cards(
                        Ui.statCard(
                                "ANWESENDE GÄSTE",
                                "–",
                                "Noch nicht angebunden"
                        ),
                        Ui.statCard(
                                "BELEGTE TISCHE",
                                "–",
                                "Noch nicht angebunden"
                        ),
                        Ui.statCard(
                                "FREIE TISCHE",
                                "–",
                                "Noch nicht angebunden"
                        ),
                        Ui.statCard(
                                "KOMMENDE RESERVIERUNGEN",
                                "–",
                                "Noch nicht angebunden"
                        ),
                        Ui.statCard(
                                "AUSLASTUNG",
                                "–",
                                "Noch nicht angebunden"
                        )
                ),

                timelinePanel(now),
                listPanel()
        );
    }

    private Node timelinePanel(LocalDateTime now) {

        LocalDateTime dayStart = now.toLocalDate()
                .atTime(OPENING_HOUR, 0);

        LocalDateTime dayEnd = now.toLocalDate()
                .atTime(CLOSING_HOUR, 0);

        int hours = CLOSING_HOUR - OPENING_HOUR;
        double width = hours * HOUR_WIDTH;

        VBox rows = new VBox(6);
        rows.setPadding(new Insets(0, 0, 18, 0));

        Pane hourLabels = new Pane();
        hourLabels.setPrefSize(width, 20);

        for (int h = 0; h <= hours; h++) {

            Label label = new Label(
                    (OPENING_HOUR + h) + ":00"
            );

            label.getStyleClass().add("timeline-hour");

            label.setLayoutX(
                    h * HOUR_WIDTH - (h == 0 ? 0 : 14)
            );

            hourLabels.getChildren().add(label);
        }

        rows.getChildren().add(
                new HBox(
                        Ui.cell("", LABEL_WIDTH),
                        hourLabels
                )
        );

        for (int table = 1; table <= TABLE_COUNT; table++) {

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

            // Aktuelle Uhrzeit in der Timeline anzeigen
            if (!now.isBefore(dayStart) && now.isBefore(dayEnd)) {

                Region nowLine = new Region();
                nowLine.getStyleClass().add("timeline-now");

                nowLine.setLayoutX(
                        minutesBetween(dayStart, now)
                                / 60.0 * HOUR_WIDTH
                );

                nowLine.setPrefSize(2, LANE_HEIGHT);

                lane.getChildren().add(nowLine);
            }

            Label tableLabel = Ui.cell(
                    "Tisch " + table,
                    LABEL_WIDTH
            );

            tableLabel.getStyleClass().add("strong");

            HBox row = new HBox(tableLabel, lane);
            row.setAlignment(Pos.CENTER_LEFT);

            rows.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(rows);

        scroll.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("timeline-scroll");

        HBox legend = new HBox(
                8,
                Ui.badge("Anwesend", "green"),
                Ui.badge("Bestätigt", "blue"),
                Ui.badge("Bald erwartet", "orange"),
                Ui.badge("Abgeschlossen", "gray"),
                Ui.badge("No-Show / Überfällig", "red")
        );

        legend.setAlignment(Pos.CENTER_LEFT);

        return Ui.panel(
                "Timeline "
                        + dayStart.format(Ui.TIME)
                        + "–"
                        + dayEnd.format(Ui.TIME)
                        + " Uhr",
                legend,
                Ui.muted("Reservierungsverwaltung noch nicht angebunden."),
                scroll
        );
    }

    private long minutesBetween(
            LocalDateTime from,
            LocalDateTime to
    ) {
        return Duration.between(from, to).toMinutes();
    }

    private Node listPanel() {

        VBox rows = new VBox();

        rows.getChildren().add(
                Ui.muted("Reservierungen derzeit nicht verfügbar.")
        );

        return Ui.panel(
                "Reservierungen heute",

                Ui.headingRow(
                        Ui.cell("ZEIT", 100),
                        Ui.growCell("GAST / TELEFON"),
                        Ui.cell("PERS.", 50),
                        Ui.cell("TISCH", 50),
                        Ui.cell("STATUS", 120),
                        Ui.cell("AKTIONEN", 390)
                ),

                rows
        );
    }
}
