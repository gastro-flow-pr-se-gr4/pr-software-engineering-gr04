package at.gastroflow.frontend.management;

import at.gastroflow.backend.model.Ingredient;
import at.gastroflow.backend.model.RestaurantSettings;
import at.gastroflow.backend.service.AnalyticsService;
import at.gastroflow.backend.service.InventoryService;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Dashboard: Kennzahlen, Diagramme und Auswertungen. */
public class DashboardView {

    private final VBox content = new VBox(24);
    private final Consumer<String> navigate;
    private int rangeDays = 7;

    /** navigate öffnet einen anderen Bereich der Leitung (z. B. "Bestand & Lager"). */
    public DashboardView(Consumer<String> navigate) {
        this.navigate = navigate;
    }

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        RestaurantSettings settings = RestaurantSettings.get();

        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate monthStart = today.withDayOfMonth(1);
        int ordersToday = AnalyticsService.orderCountBetween(today, today);
        int occupied = AnalyticsService.occupiedTables(now).size();
        String monthName = today.getMonth().getDisplayName(TextStyle.FULL, Locale.GERMAN);

        content.getChildren().setAll(
                Ui.header("Dashboard", "Kennzahlen und Entwicklung deines Restaurants"),
                Ui.demoHint("Es gibt noch keine Datenbank. Die Bestellungen der letzten 90 Tage sind "
                        + "automatisch erzeugt; alle Zahlen werden daraus berechnet."));
        Node warning = stockWarning();
        if (warning != null) {
            content.getChildren().add(warning);
        }
        content.getChildren().addAll(
                Ui.cards(
                        Ui.statCard("UMSATZ HEUTE", Ui.money(AnalyticsService.revenueBetween(today, today)),
                                "Alle Bestellungen von heute"),
                        Ui.statCard("UMSATZ DIESE WOCHE", Ui.money(AnalyticsService.revenueBetween(weekStart, today)),
                                "Seit Montag, " + weekStart.format(Ui.DAY_MONTH)),
                        Ui.statCard("UMSATZ DIESEN MONAT", Ui.money(AnalyticsService.revenueBetween(monthStart, today)),
                                "Seit 1. " + monthName),
                        Ui.statCard("BESTELLUNGEN HEUTE", String.valueOf(ordersToday),
                                "Aufgenommene Bestellungen"),
                        Ui.statCard("Ø BESTELLWERT", ordersToday == 0 ? "–"
                                : Ui.money(AnalyticsService.averageOrderValue(today, today)), "Heute"),
                        Ui.statCard("AUSLASTUNG", Ui.percent(occupied / (double) settings.getTableCount()),
                                occupied + " von " + settings.getTableCount() + " Tischen belegt"),
                        Ui.statCard("GÄSTE HEUTE", String.valueOf(AnalyticsService.guestsBetween(today, today)),
                                "Laut Bestellungen"),
                        Ui.statCard("OFFENE BESTELLUNGEN", String.valueOf(AnalyticsService.openOrderCount()),
                                "Neu, in Zubereitung oder fertig")
                ),
                trendPanel(today),
                Ui.columns(topProductsPanel(today), categoriesPanel(today)),
                Ui.columns(peakHoursPanel(today), weekdayPanel(today))
        );
    }

    // ---------- Lagerwarnung ----------

    private Node stockWarning() {
        List<Ingredient> warnings = InventoryService.getWarnings();
        if (warnings.isEmpty()) {
            return null;
        }
        Label title = new Label("⚠  " + warnings.size()
                + (warnings.size() == 1 ? " Zutat hat" : " Zutaten haben") + " einen niedrigen Lagerbestand");
        title.getStyleClass().add("warning-title");
        VBox lines = new VBox(4, title);
        for (Ingredient ingredient : warnings.subList(0, Math.min(3, warnings.size()))) {
            Label line = new Label(InventoryService.warningText(ingredient));
            line.getStyleClass().add("warning-line");
            lines.getChildren().add(line);
        }
        Label link = new Label("Bestand & Lager öffnen ›");
        link.getStyleClass().add("warning-link");

        HBox banner = new HBox(16, lines, Ui.spacer(), link);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getStyleClass().add("warning-banner");
        banner.setOnMouseClicked(event -> navigate.accept("Bestand & Lager"));
        return banner;
    }

    // ---------- Entwicklung (7 / 30 / 90 Tage) ----------

    private record Bucket(String label, LocalDate from, LocalDate to) {
    }

    private List<Bucket> buckets(LocalDate today) {
        int step = rangeDays == 90 ? 7 : 1;
        List<Bucket> buckets = new ArrayList<>();
        LocalDate start = today.minusDays(rangeDays - 1);
        for (LocalDate from = start; !from.isAfter(today); from = from.plusDays(step)) {
            LocalDate to = from.plusDays(step - 1).isAfter(today) ? today : from.plusDays(step - 1);
            String label;
            if (rangeDays == 7) {
                label = Ui.WEEKDAYS[from.getDayOfWeek().getValue() - 1] + " " + from.format(Ui.DAY_MONTH);
            } else if (rangeDays == 30) {
                label = from.format(Ui.DAY_MONTH);
            } else {
                label = "ab " + from.format(Ui.DAY_MONTH);
            }
            buckets.add(new Bucket(label, from, to));
        }
        return buckets;
    }

    private Node trendPanel(LocalDate today) {
        XYChart.Series<String, Number> revenue = new XYChart.Series<>();
        revenue.setName("Aktueller Zeitraum");
        XYChart.Series<String, Number> previous = new XYChart.Series<>();
        previous.setName("Vorheriger Zeitraum");
        XYChart.Series<String, Number> orders = new XYChart.Series<>();
        XYChart.Series<String, Number> occupancy = new XYChart.Series<>();

        double revenueNow = 0;
        double revenueBefore = 0;
        int ordersNow = 0;
        int ordersBefore = 0;
        double occupancyNow = 0;
        double occupancyBefore = 0;
        int dayCount = 0;

        for (Bucket bucket : buckets(today)) {
            LocalDate prevFrom = bucket.from().minusDays(rangeDays);
            LocalDate prevTo = bucket.to().minusDays(rangeDays);

            double value = AnalyticsService.revenueBetween(bucket.from(), bucket.to());
            double prevValue = AnalyticsService.revenueBetween(prevFrom, prevTo);
            revenue.getData().add(new XYChart.Data<>(bucket.label(), value));
            previous.getData().add(new XYChart.Data<>(bucket.label(), prevValue));
            revenueNow += value;
            revenueBefore += prevValue;

            int count = AnalyticsService.orderCountBetween(bucket.from(), bucket.to());
            orders.getData().add(new XYChart.Data<>(bucket.label(), count));
            ordersNow += count;
            ordersBefore += AnalyticsService.orderCountBetween(prevFrom, prevTo);

            double sum = 0;
            int days = 0;
            for (LocalDate day = bucket.from(); !day.isAfter(bucket.to()); day = day.plusDays(1)) {
                sum += AnalyticsService.occupancyOn(day);
                occupancyBefore += AnalyticsService.occupancyOn(day.minusDays(rangeDays));
                days++;
            }
            occupancyNow += sum;
            dayCount += days;
            occupancy.getData().add(new XYChart.Data<>(bucket.label(), Math.round(sum / days * 100)));
        }

        String unit = rangeDays == 90 ? "pro Woche" : "pro Tag";
        String compareText = "ggü. vorherigen " + rangeDays + " Tagen";

        HBox comparison = new HBox(20,
                compareCard("UMSATZ", Ui.money(revenueNow), change(revenueNow, revenueBefore), compareText),
                compareCard("BESTELLUNGEN", String.valueOf(ordersNow), change(ordersNow, ordersBefore), compareText),
                compareCard("Ø AUSLASTUNG", Ui.percent(occupancyNow / dayCount),
                        change(occupancyNow, occupancyBefore), compareText));

        LineChart<String, Number> revenueChart = new LineChart<>(xAxis(), yAxis("Umsatz in €"));
        prepare(revenueChart, 300);
        revenueChart.setLegendVisible(true);
        revenueChart.setCreateSymbols(rangeDays == 7);
        revenueChart.getData().add(revenue);
        revenueChart.getData().add(previous);

        BarChart<String, Number> ordersChart = new BarChart<>(xAxis(), yAxis("Bestellungen"));
        prepare(ordersChart, 260);
        ordersChart.getData().add(orders);

        NumberAxis percentAxis = new NumberAxis(0, 100, 20);
        percentAxis.setLabel("Auslastung in %");
        BarChart<String, Number> occupancyChart = new BarChart<>(xAxis(), percentAxis);
        prepare(occupancyChart, 260);
        occupancyChart.getStyleClass().add("occupancy-chart");
        occupancyChart.getData().add(occupancy);

        HBox title = new HBox(12, sectionLabel("Umsatzentwicklung"), Ui.spacer(),
                Ui.segment(List.of("7 Tage", "30 Tage", "90 Tage"), rangeDays + " Tage", option -> {
                    rangeDays = Integer.parseInt(option.split(" ")[0]);
                    render();
                }));
        title.setAlignment(Pos.CENTER_LEFT);

        VBox ordersBox = new VBox(8, sectionLabel("Bestellungen " + unit), ordersChart);
        VBox occupancyBox = new VBox(8, sectionLabel("Auslastung " + unit), occupancyChart);

        return Ui.panel(null, title, comparison,
                Ui.muted("Der aktuelle Zeitraum enthält den heutigen Tag nur bis jetzt."),
                revenueChart, Ui.columns(ordersBox, occupancyBox));
    }

    // Veränderung in Prozent als farbiges Etikett
    private Label change(double now, double before) {
        if (before <= 0) {
            return Ui.badge("–", "gray");
        }
        double percent = (now - before) / before * 100;
        String text = String.format(Locale.GERMANY, "%s %.0f %%", percent >= 0 ? "▲" : "▼", Math.abs(percent));
        return Ui.badge(text, percent >= 0 ? "green" : "red");
    }

    private VBox compareCard(String title, String value, Label change, String note) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-title");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("compare-value");
        HBox line = new HBox(10, valueLabel, change);
        line.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(4, titleLabel, line, Ui.muted(note));
        card.getStyleClass().add("compare-card");
        card.setPrefWidth(280);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    // ---------- Beliebteste Produkte und Kategorien ----------

    private VBox topProductsPanel(LocalDate today) {
        VBox list = new VBox(10);
        List<Map.Entry<String, Integer>> top = AnalyticsService.topProducts(today.minusDays(29), today, 5);
        int max = top.isEmpty() ? 1 : top.get(0).getValue();
        int rank = 1;
        for (Map.Entry<String, Integer> entry : top) {
            list.getChildren().add(rankingRow(rank++ + ".", entry.getKey(),
                    entry.getValue() + "×", entry.getValue() / (double) max));
        }
        return Ui.panel("Beliebteste Produkte", Ui.muted("Verkaufte Stück, letzte 30 Tage"), list);
    }

    private VBox categoriesPanel(LocalDate today) {
        VBox list = new VBox(10);
        List<Map.Entry<String, Double>> categories = AnalyticsService.revenueByCategory(today.minusDays(29), today);
        double max = categories.isEmpty() ? 1 : categories.get(0).getValue();
        int rank = 1;
        for (Map.Entry<String, Double> entry : categories) {
            list.getChildren().add(rankingRow(rank++ + ".", entry.getKey(),
                    Ui.money(entry.getValue()), entry.getValue() / max));
        }
        return Ui.panel("Umsatzstärkste Kategorien", Ui.muted("Umsatz, letzte 30 Tage"), list);
    }

    private Node rankingRow(String rank, String name, String value, double ratio) {
        Label rankLabel = Ui.cell(rank, 24);
        rankLabel.getStyleClass().add("strong");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("strong");
        HBox line = new HBox(10, rankLabel, Ui.growCell(name), valueLabel);
        line.setAlignment(Pos.CENTER_LEFT);

        ProgressBar bar = new ProgressBar(ratio);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("rank-bar");

        return new VBox(6, line, bar);
    }

    // ---------- Stoßzeiten und Wochentage ----------

    private VBox peakHoursPanel(LocalDate today) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        Map<Integer, Double> perHour = AnalyticsService.averageOrdersPerHour(today.minusDays(30), today.minusDays(1));
        int peakHour = -1;
        double peak = -1;
        for (Map.Entry<Integer, Double> entry : perHour.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey() + "", Math.round(entry.getValue() * 10) / 10.0));
            if (entry.getValue() > peak) {
                peak = entry.getValue();
                peakHour = entry.getKey();
            }
        }
        BarChart<String, Number> chart = new BarChart<>(xAxis(), yAxis("Ø Bestellungen"));
        prepare(chart, 260);
        chart.getData().add(series);

        String text = peakHour < 0 ? "Noch keine Daten."
                : "Am meisten los: " + peakHour + ":00–" + (peakHour + 1) + ":00 Uhr (Ø letzte 30 Tage, Uhrzeit)";
        return Ui.panel("Stoßzeiten", Ui.muted(text), chart);
    }

    private VBox weekdayPanel(LocalDate today) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        Map<DayOfWeek, Double> perWeekday = AnalyticsService.averageRevenueByWeekday(today.minusDays(56), today.minusDays(1));
        for (DayOfWeek day : DayOfWeek.values()) {
            series.getData().add(new XYChart.Data<>(Ui.WEEKDAYS[day.getValue() - 1], Math.round(perWeekday.get(day))));
        }
        BarChart<String, Number> chart = new BarChart<>(xAxis(), yAxis("Ø Umsatz in €"));
        prepare(chart, 260);
        chart.getData().add(series);

        double todayRevenue = AnalyticsService.revenueBetween(today, today);
        double lastWeek = AnalyticsService.revenueBetween(today.minusDays(7), today.minusDays(7));
        String text = "Heute bisher " + Ui.money(todayRevenue) + " · gleicher Tag letzte Woche " + Ui.money(lastWeek);

        return Ui.panel("Vergleich der Wochentage", Ui.muted(text), chart);
    }

    // ---------- Hilfsmethoden für Diagramme ----------

    private CategoryAxis xAxis() {
        CategoryAxis axis = new CategoryAxis();
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

    private void prepare(XYChart<String, Number> chart, double height) {
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
