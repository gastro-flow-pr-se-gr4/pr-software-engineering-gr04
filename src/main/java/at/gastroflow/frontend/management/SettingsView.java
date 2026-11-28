package at.gastroflow.frontend.management;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class SettingsView {

    private final VBox content = new VBox(24);

    // Vorübergehende UI-Konfiguration
    private static final int TABLE_COUNT = 12;

    private static final int[] TABLE_CAPACITIES = {
            4, 4, 4, 4, 4, 4,
            6, 6, 6, 6,
            8, 8
    };

    public SettingsView(Runnable onSaved) {
        // Konstruktor bleibt für die bestehende Navigation erhalten.
    }

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {

        TextField name = new TextField("GastroFlow");
        name.setPrefWidth(280);

        ComboBox<String> opening = hourBox("10:00", 0, 22);
        ComboBox<String> closing = hourBox("22:00", 1, 23);

        Spinner<Integer> delay =
                new Spinner<>(5, 120, 30, 5);

        Spinner<Integer> duration =
                new Spinner<>(30, 300, 90, 15);

        Spinner<Integer> lowStock =
                new Spinner<>(50, 300, 100, 10);

        Spinner<Integer> tableCount =
                new Spinner<>(1, 40, TABLE_COUNT);

        name.setDisable(true);
        opening.setDisable(true);
        closing.setDisable(true);
        delay.setDisable(true);
        duration.setDisable(true);
        lowStock.setDisable(true);
        tableCount.setDisable(true);

        FlowPane capacities = new FlowPane(10, 10);
        capacities.setPrefWrapLength(700);

        for (int i = 0; i < TABLE_CAPACITIES.length; i++) {
            addCapacitySpinner(
                    capacities,
                    i + 1,
                    TABLE_CAPACITIES[i]
            );
        }

        GridPane form = new GridPane();

        form.setHgap(18);
        form.setVgap(14);

        form.addRow(
                0, label("Restaurantname"), name
        );

        form.addRow(
                1, label("Öffnet um"), opening
        );

        form.addRow(
                2, label("Schließt um"), closing
        );

        form.addRow(
                3,
                label("Standard-Reservierungsdauer (Minuten)"),
                duration
        );

        form.addRow(
                4,
                label("Bestellung verzögert ab (Minuten)"),
                delay
        );

        form.addRow(
                5,
                label("Lagerwarnung ab (% des Mindestbestands)"),
                lowStock
        );

        GridPane tables = new GridPane();

        tables.setHgap(18);
        tables.setVgap(14);

        tables.addRow(
                0, label("Anzahl Tische"), tableCount
        );

        Button saveButton = new Button(
                "Einstellungen speichern"
        );

        saveButton.getStyleClass().add("primary-button");
        saveButton.setDisable(true);

        content.getChildren().setAll(

                Ui.header(
                        "Einstellungen",
                        "Grundwerte für Tagesübersicht, Kalender, "
                                + "Bestellungen, Lager und Dashboard"
                ),

                Ui.muted(
                        "Einstellungsverwaltung noch nicht angebunden. "
                                + "Die angezeigten Werte sind vorläufige "
                                + "Standardwerte."
                ),

                Ui.panel(
                        "Restaurant",

                        form,

                        Ui.muted(
                                "Lagerwarnung: 100 % = Warnung, "
                                        + "sobald der Bestand den "
                                        + "Mindestbestand erreicht."
                        )
                ),

                Ui.panel(
                        "Tische und Kapazitäten",

                        tables,

                        Ui.muted("Plätze pro Tisch"),

                        capacities
                ),

                Ui.panel(
                        null,

                        Ui.muted(
                                "Das Speichern von Einstellungen "
                                        + "ist derzeit deaktiviert."
                        ),

                        saveButton
                ),

                Ui.panel(
                        "Hinweis zu den Daten",

                        Ui.muted(
                                "Das Demo-Backend wurde entfernt. "
                                        + "Die Einstellungsverwaltung "
                                        + "wird später mit dem echten "
                                        + "Backend verbunden."
                        )
                )
        );
    }

    private void addCapacitySpinner(
            FlowPane pane,
            int tableNumber,
            int seats
    ) {

        Spinner<Integer> spinner =
                new Spinner<>(1, 30, seats);

        spinner.setPrefWidth(80);
        spinner.setDisable(true);

        Label label = new Label(
                "Tisch " + tableNumber
        );

        label.getStyleClass().add("field-label");

        VBox box = new VBox(4, label, spinner);

        box.setAlignment(Pos.CENTER_LEFT);

        pane.getChildren().add(box);
    }

    private ComboBox<String> hourBox(
            String value,
            int from,
            int to
    ) {

        ComboBox<String> box = new ComboBox<>();

        for (int hour = from; hour <= to; hour++) {

            box.getItems().add(
                    String.format("%02d:00", hour)
            );
        }

        box.setValue(value);

        return box;
    }

    private Label label(String text) {

        Label label = new Label(text);

        label.getStyleClass().add("field-label");

        return label;
    }
}
