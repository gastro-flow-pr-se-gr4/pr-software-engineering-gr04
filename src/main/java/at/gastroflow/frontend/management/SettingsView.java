package at.gastroflow.frontend.management;

import at.gastroflow.backend.model.Reservation;
import at.gastroflow.backend.model.RestaurantSettings;
import at.gastroflow.backend.service.InventoryService;
import at.gastroflow.backend.service.ReservationService;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Einstellungen des Restaurants, die in allen Bereichen der Leitung gelten. */
public class SettingsView {

    private final VBox content = new VBox(24);
    private final Runnable onSaved;
    private final List<Spinner<Integer>> capacitySpinners = new ArrayList<>();

    public SettingsView(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {
        RestaurantSettings settings = RestaurantSettings.get();

        TextField name = new TextField(settings.getName());
        name.setPrefWidth(280);
        ComboBox<String> opening = hourBox(settings.getOpeningTime(), 0, 22);
        ComboBox<String> closing = hourBox(settings.getClosingTime(), 1, 23);
        Spinner<Integer> delay = new Spinner<>(5, 120, settings.getDelayMinutes(), 5);
        Spinner<Integer> duration = new Spinner<>(30, 300, settings.getDefaultReservationMinutes(), 15);
        Spinner<Integer> lowStock = new Spinner<>(50, 300, settings.getLowStockPercent(), 10);

        // Tische und Plätze pro Tisch
        Spinner<Integer> tableCount = new Spinner<>(1, 40, settings.getTableCount());
        FlowPane capacities = new FlowPane(10, 10);
        capacities.setPrefWrapLength(700);
        capacitySpinners.clear();
        for (int seats : settings.getTableCapacities()) {
            addCapacitySpinner(capacities, seats);
        }
        tableCount.valueProperty().addListener((obs, oldCount, newCount) -> {
            while (capacitySpinners.size() < newCount) {
                addCapacitySpinner(capacities, 4);
            }
            while (capacitySpinners.size() > newCount) {
                capacitySpinners.remove(capacitySpinners.size() - 1);
                capacities.getChildren().remove(capacities.getChildren().size() - 1);
            }
        });

        Label message = new Label();
        message.setWrapText(true);
        message.managedProperty().bind(message.textProperty().isNotEmpty());

        GridPane form = new GridPane();
        form.setHgap(18);
        form.setVgap(14);
        form.addRow(0, label("Restaurantname"), name);
        form.addRow(1, label("Öffnet um"), opening);
        form.addRow(2, label("Schließt um"), closing);
        form.addRow(3, label("Standard-Reservierungsdauer (Minuten)"), duration);
        form.addRow(4, label("Bestellung verzögert ab (Minuten)"), delay);
        form.addRow(5, label("Lagerwarnung ab (% des Mindestbestands)"), lowStock);

        GridPane tables = new GridPane();
        tables.setHgap(18);
        tables.setVgap(14);
        tables.addRow(0, label("Anzahl Tische"), tableCount);

        content.getChildren().setAll(
                Ui.header("Einstellungen", "Grundwerte für Tagesübersicht, Kalender, Bestellungen, Lager und Dashboard"),
                Ui.panel("Restaurant", form,
                        Ui.muted("Lagerwarnung: 100 % = Warnung, sobald der Bestand den Mindestbestand erreicht. "
                                + "120 % = schon etwas früher warnen.")),
                Ui.panel("Tische und Kapazitäten", tables,
                        Ui.muted("Plätze pro Tisch – wird beim Reservieren geprüft."), capacities),
                Ui.panel(null, message,
                        Ui.primary("Einstellungen speichern", () -> save(name, opening, closing, delay, duration, lowStock, message))),
                Ui.panel("Hinweis zu den Daten",
                        Ui.muted("Es gibt noch keine Datenbank. Lagerbestände, Lagerhistorie und Rezepturen werden dauerhaft "
                                + "gespeichert in " + InventoryService.getStorageFile() + ". Speisekarte, Reservierungen, "
                                + "Personal und Einstellungen gelten nur, solange GastroFlow läuft."))
        );
    }

    private void addCapacitySpinner(FlowPane pane, int seats) {
        Spinner<Integer> spinner = new Spinner<>(1, 30, seats);
        spinner.setPrefWidth(80);
        capacitySpinners.add(spinner);
        Label label = new Label("Tisch " + capacitySpinners.size());
        label.getStyleClass().add("field-label");
        VBox box = new VBox(4, label, spinner);
        box.setAlignment(Pos.CENTER_LEFT);
        pane.getChildren().add(box);
    }

    private void save(TextField name, ComboBox<String> opening, ComboBox<String> closing,
                      Spinner<Integer> delay, Spinner<Integer> duration, Spinner<Integer> lowStock, Label message) {
        try {
            List<Integer> capacities = capacitySpinners.stream().map(Spinner::getValue).toList();

            // Keine künftige Reservierung darf ihren Tisch oder genug Plätze verlieren
            Optional<Reservation> blocked = ReservationService.getReservations().stream()
                    .filter(r -> r.getStart().isAfter(LocalDateTime.now()))
                    .filter(r -> r.getStatus().blocksTable())
                    .filter(r -> r.getTable() > capacities.size() || r.getPersons() > capacities.get(r.getTable() - 1))
                    .findFirst();
            if (blocked.isPresent()) {
                Reservation r = blocked.get();
                throw new IllegalArgumentException("Tisch " + r.getTable() + " ist noch für " + r.getName() + " ("
                        + r.getPersons() + " Personen, " + r.getStart().format(Ui.DATE) + ") reserviert. Bitte zuerst umbuchen.");
            }

            RestaurantSettings.get().update(name.getText(), capacities,
                    Ui.parseTime(opening.getValue()), Ui.parseTime(closing.getValue()),
                    delay.getValue(), duration.getValue(), lowStock.getValue());
            onSaved.run();
            render();
            Ui.info("Einstellungen gespeichert.");
        } catch (IllegalArgumentException e) {
            message.setText(e.getMessage());
            message.getStyleClass().setAll("label", "error-text");
        }
    }

    private ComboBox<String> hourBox(LocalTime value, int from, int to) {
        ComboBox<String> box = new ComboBox<>();
        for (int hour = from; hour <= to; hour++) {
            box.getItems().add(String.format("%02d:00", hour));
        }
        box.setValue(value.format(Ui.TIME));
        return box;
    }

    private Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }
}
