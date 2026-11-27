package at.gastroflow.frontend.management;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

public class StaffView {

    private final VBox content = new VBox(24);

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {

        Button addButton = new Button("+ Mitarbeiter");

        addButton.getStyleClass().add("primary-button");
        addButton.setDisable(true);

        VBox rows = new VBox();

        rows.getChildren().add(
                Ui.muted("Personaldaten derzeit nicht verfügbar.")
        );

        content.getChildren().setAll(

                Ui.header(
                        "Personal",
                        "Wer arbeitet heute wann – und wer ist gerade da?",
                        addButton
                ),

                Ui.cards(
                        Ui.statCard(
                                "IM DIENST",
                                "–",
                                "Arbeiten gerade"
                        ),
                        Ui.statCard(
                                "PAUSE",
                                "–",
                                "Kurz nicht verfügbar"
                        ),
                        Ui.statCard(
                                "NOCH NICHT ERSCHIENEN",
                                "–",
                                "Schicht noch offen"
                        ),
                        Ui.statCard(
                                "FEIERABEND",
                                "–",
                                "Schicht beendet"
                        )
                ),

                Ui.muted(
                        "Personalverwaltung noch nicht angebunden."
                ),

                Ui.panel(
                        "Schichtplan heute",

                        Ui.muted(
                                "Mitarbeiter und Schichten werden "
                                        + "nach Anbindung des Backends angezeigt."
                        ),

                        Ui.headingRow(
                                Ui.growCell("NAME"),
                                Ui.cell("ROLLE", 80),
                                Ui.cell("SCHICHT", 110),
                                Ui.cell("BEGINN", 60),
                                Ui.cell("ENDE", 60),
                                Ui.cell("STATUS", 200),
                                Ui.cell("", 170)
                        ),

                        rows
                )
        );
    }
}
