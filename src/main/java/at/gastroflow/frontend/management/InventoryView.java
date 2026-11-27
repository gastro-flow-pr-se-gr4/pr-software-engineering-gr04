package at.gastroflow.frontend.management;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

public class InventoryView {

    private static final String ALL = "Alle";
    private static final String WARNINGS = "Nur Warnungen";
    private static final String ARCHIVED = "Archiviert";
    private static final String ALL_CATEGORIES = "Alle Kategorien";
    private static final String ALL_INGREDIENTS = "Alle Zutaten";

    private final VBox content = new VBox(24);

    private String filter = ALL;
    private String search = "";

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {

        Button addButton = new Button("+ Zutat");
        addButton.getStyleClass().add("primary-button");
        addButton.setDisable(true);

        content.getChildren().setAll(
                Ui.header(
                        "Bestand & Lager",
                        "Zutaten, Lagerbestände, Warnungen und Lagerbuchungen",
                        addButton
                ),

                Ui.muted("Lagerverwaltung noch nicht angebunden."),

                Ui.cards(
                        Ui.statCard(
                                "ZUTATEN",
                                "–",
                                "Noch nicht angebunden"
                        ),
                        Ui.statCard(
                                "AUSREICHEND",
                                "–",
                                "Über dem Mindestbestand"
                        ),
                        Ui.statCard(
                                "NIEDRIGER BESTAND",
                                "–",
                                "Bald nachbestellen"
                        ),
                        Ui.statCard(
                                "NICHT VERFÜGBAR",
                                "–",
                                "Leer oder reicht nicht"
                        ),
                        Ui.statCard(
                                "BUCHUNGEN HEUTE",
                                "–",
                                "Eingänge, Abzüge, Korrekturen"
                        )
                ),

                stockPanel(),
                historyPanel()
        );
    }

    private Node stockPanel() {

        ComboBox<String> categoryBox = new ComboBox<>(
                FXCollections.observableArrayList(ALL_CATEGORIES)
        );

        categoryBox.setValue(ALL_CATEGORIES);

        TextField searchField = new TextField(search);
        searchField.setPromptText("Zutat suchen …");
        searchField.setPrefWidth(200);

        searchField.textProperty().addListener(
                (obs, oldText, newText) -> search = newText
        );

        HBox toolbar = new HBox(
                12,
                Ui.segment(
                        List.of(ALL, WARNINGS, ARCHIVED),
                        filter,
                        option -> {
                            filter = option;
                            render();
                        }
                ),
                Ui.spacer(),
                categoryBox,
                searchField
        );

        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox rows = new VBox();

        rows.getChildren().add(
                Ui.muted("Lagerbestände derzeit nicht verfügbar.")
        );

        return Ui.panel(
                "Lagerübersicht",

                toolbar,

                Ui.headingRow(
                        Ui.growCell("ZUTAT"),
                        Ui.cell("KATEGORIE", 110),
                        Ui.cell("BESTAND", 58),
                        Ui.cell("EINHEIT", 72),
                        Ui.cell("MINDESTBEST.", 88),
                        Ui.cell("STATUS", 120),
                        Ui.cell("AKTUALISIERT", 82),
                        Ui.cell("AKTIONEN", 150)
                ),

                rows
        );
    }

    private Node historyPanel() {

        ComboBox<String> filterBox = new ComboBox<>(
                FXCollections.observableArrayList(ALL_INGREDIENTS)
        );

        filterBox.setValue(ALL_INGREDIENTS);

        VBox rows = new VBox();

        rows.getChildren().add(
                Ui.muted("Lagerbuchungen derzeit nicht verfügbar.")
        );

        HBox top = new HBox(
                12,
                Ui.muted(
                        "Wareneingänge, Korrekturen, automatische "
                                + "Abzüge und Rückbuchungen "
                                + "(neueste zuerst, höchstens 100)."
                ),
                Ui.spacer(),
                filterBox
        );

        top.setAlignment(Pos.CENTER_LEFT);

        return Ui.panel(
                "Lagerhistorie",

                top,

                Ui.headingRow(
                        Ui.cell("DATUM / UHRZEIT", 130),
                        Ui.cell("ZUTAT", 170),
                        Ui.cell("MENGE", 110),
                        Ui.cell("ART", 180),
                        Ui.growCell("GRUND")
                ),

                rows
        );
    }
}
