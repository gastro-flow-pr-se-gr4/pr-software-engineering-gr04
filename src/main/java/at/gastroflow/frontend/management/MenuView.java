package at.gastroflow.frontend.management;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class MenuView {

    private final VBox content = new VBox(24);
    private String search = "";

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {

        Button categoryButton = new Button("+ Kategorie");
        categoryButton.getStyleClass().add("secondary-button");
        categoryButton.setDisable(true);

        Button productButton = new Button("+ Produkt");
        productButton.getStyleClass().add("primary-button");
        productButton.setDisable(true);

        HBox layout = new HBox(
                20,
                categoriesPanel(),
                productsPanel()
        );

        content.getChildren().setAll(
                Ui.header(
                        "Speisekarte",
                        "Produkte, Preise, Allergene, Rezepturen und Verfügbarkeit verwalten",
                        categoryButton,
                        productButton
                ),

                Ui.muted("Produktverwaltung noch nicht angebunden."),

                layout
        );
    }

    private VBox categoriesPanel() {

        VBox list = new VBox(4);

        list.getChildren().add(
                categoryRow("Alle Produkte")
        );

        list.getChildren().add(
                Ui.muted("Kategorien noch nicht verfügbar.")
        );

        VBox panel = Ui.panel("Kategorien", list);

        panel.setMinWidth(240);
        panel.setPrefWidth(240);

        return panel;
    }

    private HBox categoryRow(String text) {

        Label name = Ui.growCell(text);

        Label countLabel = new Label("–");
        countLabel.getStyleClass().add("muted");

        HBox row = new HBox(8, name, countLabel);

        row.setAlignment(Pos.CENTER_LEFT);

        row.getStyleClass().addAll(
                "category-row",
                "category-active"
        );

        return row;
    }

    private VBox productsPanel() {

        TextField searchField = new TextField(search);

        searchField.setPromptText("Produkt suchen …");
        searchField.setPrefWidth(240);

        searchField.textProperty().addListener(
                (obs, oldText, newText) -> search = newText
        );

        VBox rows = new VBox();

        fillProductRows(rows);

        HBox top = new HBox(
                12,
                searchField,
                Ui.spacer(),
                Ui.muted(
                        "Allergene: A Gluten · C Ei · D Fisch "
                                + "· G Milch · L Sellerie · M Senf"
                )
        );

        top.setAlignment(Pos.CENTER_LEFT);

        VBox panel = Ui.panel(
                "Alle Produkte",

                top,

                Ui.muted(
                        "Produktdaten werden nach Anbindung "
                                + "der REST-API angezeigt."
                ),

                Ui.headingRow(
                        Ui.cell("", 48),
                        Ui.growCell("PRODUKT"),
                        Ui.cell("PREIS", 72),
                        Ui.cell("PORTIONEN", 82),
                        Ui.cell("AKTIV", 70),
                        Ui.cell("", 170)
                ),

                rows
        );

        HBox.setHgrow(panel, Priority.ALWAYS);

        panel.setMaxWidth(Double.MAX_VALUE);

        return panel;
    }

    private void fillProductRows(VBox rows) {

        rows.getChildren().clear();

        rows.getChildren().add(
                Ui.muted("Produkte derzeit nicht verfügbar.")
        );
    }
}
