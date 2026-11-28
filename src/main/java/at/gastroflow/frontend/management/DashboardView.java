package at.gastroflow.frontend.management;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class DashboardView {

    private final VBox content = new VBox(24);
    private final Consumer<String> navigate;

    private int rangeDays = 7;

    public DashboardView(Consumer<String> navigate) {
        this.navigate = navigate;
    }

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);

        String monthName = today.getMonth()
                .getDisplayName(TextStyle.FULL, Locale.GERMAN);

        content.getChildren().setAll(

                Ui.header(
                        "Dashboard",
                        "Kennzahlen und Entwicklung deines Restaurants"
                ),

                Ui.muted("Dashboard-Statistiken noch nicht angebunden."),

                stockPanel(),

                Ui.cards(
                        Ui.statCard(
                                "UMSATZ HEUTE",
                                "–",
                                "Alle Bestellungen von heute"
                        ),
                        Ui.statCard(
                                "UMSATZ DIESE WOCHE",
                                "–",
                                "Seit Montag, "
                                        + weekStart.format(Ui.DAY_MONTH)
                        ),
                        Ui.statCard(
                                "UMSATZ DIESEN MONAT",
                                "–",
                                "Seit 1. " + monthName
                        ),
                        Ui.statCard(
                                "BESTELLUNGEN HEUTE",
                                "–",
                                "Aufgenommene Bestellungen"
                        ),
                        Ui.statCard(
                                "Ø BESTELLWERT",
                                "–",
                                "Heute"
                        ),
                        Ui.statCard(
                                "AUSLASTUNG",
                                "–",
                                "Belegte Tische"
                        ),
                        Ui.statCard(
                                "GÄSTE HEUTE",
                                "–",
                                "Laut Bestellungen"
                        ),
                        Ui.statCard(
                                "OFFENE BESTELLUNGEN",
                                "–",
                                "Neu, in Zubereitung oder fertig"
                        )
                ),

                trendPanel(today),

                Ui.columns(
                        topProductsPanel(),
                        categoriesPanel()
                ),

                Ui.columns(
                        peakHoursPanel(),
                        weekdayPanel()
                )
        );
    }

    private Node stockPanel() {

        HBox row = new HBox(
                16,
                Ui.muted("Lagerwarnungen derzeit nicht verfügbar."),
                Ui.spacer(),
                Ui.secondary(
                        "Bestand & Lager öffnen ›",
                        () -> navigate.accept("Bestand & Lager")
                )
        );

        row.setAlignment(Pos.CENTER_LEFT);

        return Ui.panel("Lagerübersicht", row);
    }

    private Node trendPanel(LocalDate today) {

        String unit = rangeDays == 90
                ? "pro Woche"
                : "pro Tag";

        String compareText =
                "ggü. vorherigen " + rangeDays + " Tagen";

        HBox comparison = new HBox(
                20,
                compareCard("UMSATZ", "–", compareText),
                compareCard("BESTELLUNGEN", "–", compareText),
                compareCard("Ø AUSLASTUNG", "–", compareText)
        );

        LineChart<String, Number> revenueChart =
                new LineChart<>(
                        periodAxis(today),
                        yAxis("Umsatz in €")
                );

        prepare(revenueChart, 300);

        revenueChart.setCreateSymbols(rangeDays == 7);

        BarChart<String, Number> ordersChart =
                new BarChart<>(
                        periodAxis(today),
                        yAxis("Bestellungen")
                );

        prepare(ordersChart, 260);

        NumberAxis percentAxis =
                new NumberAxis(0, 100, 20);

        percentAxis.setLabel("Auslastung in %");

        BarChart<String, Number> occupancyChart =
                new BarChart<>(
                        periodAxis(today),
                        percentAxis
                );

        prepare(occupancyChart, 260);

        occupancyChart.getStyleClass().add("occupancy-chart");

        HBox title = new HBox(
                12,
                sectionLabel("Umsatzentwicklung"),
                Ui.spacer(),

                Ui.segment(
                        List.of("7 Tage", "30 Tage", "90 Tage"),
                        rangeDays + " Tage",
                        option -> {
                            rangeDays = Integer.parseInt(
                                    option.split(" ")[0]
                            );

                            render();
                        }
                )
        );

        title.setAlignment(Pos.CENTER_LEFT);

        VBox ordersBox = new VBox(
                8,
                sectionLabel("Bestellungen " + unit),
                ordersChart
        );

        VBox occupancyBox = new VBox(
                8,
                sectionLabel("Auslastung " + unit),
                occupancyChart
        );

        return Ui.panel(
                null,
                title,
                comparison,
                Ui.muted("Statistikdaten derzeit nicht verfügbar."),
                revenueChart,
                Ui.columns(ordersBox, occupancyBox)
        );
    }

    private VBox compareCard(
            String title,
            String value,
            String note
    ) {

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-title");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("compare-value");

        HBox line = new HBox(
                10,
                valueLabel,
                Ui.badge("–", "gray")
        );

        line.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(
                4,
                titleLabel,
                line,
                Ui.muted(note)
        );

        card.getStyleClass().add("compare-card");

        card.setPrefWidth(280);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);

        HBox.setHgrow(card, Priority.ALWAYS);

        return card;
    }

    private VBox topProductsPanel() {

        return Ui.panel(
                "Beliebteste Produkte",
                Ui.muted("Verkaufte Stück, letzte 30 Tage"),
                Ui.muted("Produktstatistiken noch nicht verfügbar.")
        );
    }

    private VBox categoriesPanel() {

        return Ui.panel(
                "Umsatzstärkste Kategorien",
                Ui.muted("Umsatz, letzte 30 Tage"),
                Ui.muted("Kategoriestatistiken noch nicht verfügbar.")
        );
    }

    private VBox peakHoursPanel() {

        BarChart<String, Number> chart =
                new BarChart<>(
                        new CategoryAxis(),
                        yAxis("Ø Bestellungen")
                );

        prepare(chart, 260);

        return Ui.panel(
                "Stoßzeiten",
                Ui.muted("Statistikdaten noch nicht verfügbar."),
                chart
        );
    }

    private VBox weekdayPanel() {

        CategoryAxis axis = new CategoryAxis();

        axis.getCategories().setAll(
                Ui.WEEKDAYS
        );

        axis.setAutoRanging(false);

        BarChart<String, Number> chart =
                new BarChart<>(
                        axis,
                        yAxis("Ø Umsatz in €")
                );

        prepare(chart, 260);

        return Ui.panel(
                "Vergleich der Wochentage",
                Ui.muted("Umsatzdaten noch nicht verfügbar."),
                chart
        );
    }

    private CategoryAxis periodAxis(LocalDate today) {

        CategoryAxis axis = new CategoryAxis();

        int step = rangeDays == 90 ? 7 : 1;

        LocalDate start = today.minusDays(rangeDays - 1);

        for (LocalDate day = start;
             !day.isAfter(today);
             day = day.plusDays(step)) {

            String label;

            if (rangeDays == 7) {

                label = Ui.WEEKDAYS[
                        day.getDayOfWeek().getValue() - 1
                        ] + " " + day.format(Ui.DAY_MONTH);

            } else if (rangeDays == 30) {

                label = day.format(Ui.DAY_MONTH);

            } else {

                label = "ab " + day.format(Ui.DAY_MONTH);
            }

            axis.getCategories().add(label);
        }

        axis.setAutoRanging(false);

        if (rangeDays == 30) {
            axis.setTickLabelRotation(-60);
        }

        return axis;
    }

    private NumberAxis yAxis(String label) {

        NumberAxis axis = new NumberAxis();

        axis.setLabel(label);
        axis.setForceZeroInRange(true);

        return axis;
    }

    private void prepare(
            XYChart<String, Number> chart,
            double height
    ) {

        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(height);
        chart.setMinHeight(height);
    }

    private Label sectionLabel(String text) {

        Label label = new Label(text);
        label.getStyleClass().add("section-title");

        return label;
    }
}
