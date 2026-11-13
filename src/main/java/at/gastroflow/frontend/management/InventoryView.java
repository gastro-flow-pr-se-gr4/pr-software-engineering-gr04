package at.gastroflow.frontend.management;

import at.gastroflow.backend.model.Ingredient;
import at.gastroflow.backend.model.MovementType;
import at.gastroflow.backend.model.Product;
import at.gastroflow.backend.model.StockMovement;
import at.gastroflow.backend.model.StockStatus;
import at.gastroflow.backend.model.Unit;
import at.gastroflow.backend.service.InventoryService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Bestand & Lager: Zutaten, Warnungen, Wareneingänge, Korrekturen und Lagerhistorie. */
public class InventoryView {

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd.MM. HH:mm");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String ALL = "Alle";
    private static final String WARNINGS = "Nur Warnungen";
    private static final String ARCHIVED = "Archiviert";
    private static final String ALL_CATEGORIES = "Alle Kategorien";
    private static final String ALL_INGREDIENTS = "Alle Zutaten";

    private final VBox content = new VBox(24);
    private String filter = ALL;
    private String categoryFilter = ALL_CATEGORIES;
    private String search = "";
    private String historyFilter = ALL_INGREDIENTS;

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {
        List<Ingredient> active = InventoryService.getIngredients(false);
        long archived = InventoryService.getIngredients(true).size() - active.size();
        List<Ingredient> warnings = InventoryService.getWarnings();

        content.getChildren().setAll(
                Ui.header("Bestand & Lager", "Zutaten, Lagerbestände, Warnungen und Lagerbuchungen",
                        Ui.primary("+ Zutat", () -> openIngredientDialog(null))),
                Ui.demoHint("Startbestand und Zutaten sind Beispiele. Änderungen werden dauerhaft gespeichert in "
                        + InventoryService.getStorageFile() + "."),
                Ui.cards(
                        Ui.statCard("ZUTATEN", String.valueOf(active.size()), archived + " archiviert"),
                        Ui.statCard("AUSREICHEND", count(active, StockStatus.AUSREICHEND), "Über dem Mindestbestand"),
                        Ui.statCard("NIEDRIGER BESTAND", count(active, StockStatus.NIEDRIG), "Bald nachbestellen"),
                        Ui.statCard("NICHT VERFÜGBAR", count(active, StockStatus.NICHT_VERFUEGBAR), "Leer oder reicht nicht"),
                        Ui.statCard("BUCHUNGEN HEUTE", String.valueOf(InventoryService.movementsOn(LocalDate.now())),
                                "Eingänge, Abzüge, Korrekturen"))
        );
        if (!warnings.isEmpty()) {
            content.getChildren().add(warningsPanel(warnings));
        }
        content.getChildren().addAll(stockPanel(), historyPanel());
    }

    private String count(List<Ingredient> ingredients, StockStatus status) {
        return String.valueOf(ingredients.stream().filter(i -> InventoryService.statusOf(i) == status).count());
    }

    // ---------- Warnungen ----------

    private Node warningsPanel(List<Ingredient> warnings) {
        VBox lines = new VBox(8);
        for (Ingredient ingredient : warnings) {
            StockStatus status = InventoryService.statusOf(ingredient);
            HBox line = new HBox(10, Ui.badge(status.toString(), Ui.stockColor(status)),
                    Ui.growCell(InventoryService.warningText(ingredient)),
                    Ui.small("+ Eingang", () -> openReceiveDialog(ingredient)));
            line.setAlignment(Pos.CENTER_LEFT);
            lines.getChildren().add(line);
        }
        return Ui.panel("Lagerwarnungen", lines);
    }

    // ---------- Lagerübersicht ----------

    private Node stockPanel() {
        List<String> categories = new ArrayList<>();
        categories.add(ALL_CATEGORIES);
        for (Ingredient ingredient : InventoryService.getIngredients(true)) {
            if (!categories.contains(ingredient.getCategory())) {
                categories.add(ingredient.getCategory());
            }
        }
        ComboBox<String> categoryBox = new ComboBox<>(FXCollections.observableArrayList(categories));
        categoryBox.setValue(categories.contains(categoryFilter) ? categoryFilter : ALL_CATEGORIES);
        categoryBox.setOnAction(event -> {
            categoryFilter = categoryBox.getValue();
            render();
        });

        TextField searchField = new TextField(search);
        searchField.setPromptText("Zutat suchen …");
        searchField.setPrefWidth(200);

        VBox rows = new VBox();
        fillRows(rows);
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            search = newText;
            fillRows(rows);
        });

        HBox toolbar = new HBox(12,
                Ui.segment(List.of(ALL, WARNINGS, ARCHIVED), filter, option -> {
                    filter = option;
                    render();
                }),
                Ui.spacer(), categoryBox, searchField);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        return Ui.panel("Lagerübersicht", toolbar,
                Ui.headingRow(Ui.growCell("ZUTAT"), Ui.cell("KATEGORIE", 110), Ui.cell("BESTAND", 58),
                        Ui.cell("EINHEIT", 72), Ui.cell("MINDESTBEST.", 88), Ui.cell("STATUS", 120),
                        Ui.cell("AKTUALISIERT", 82), Ui.cell("AKTIONEN", 150)),
                rows);
    }

    private void fillRows(VBox rows) {
        rows.getChildren().clear();
        String query = search.trim().toLowerCase();
        List<Ingredient> list = InventoryService.getIngredients(true).stream()
                .filter(i -> filter.equals(ARCHIVED) == i.isArchived())
                .filter(i -> !filter.equals(WARNINGS) || InventoryService.statusOf(i) != StockStatus.AUSREICHEND)
                .filter(i -> categoryFilter.equals(ALL_CATEGORIES) || i.getCategory().equals(categoryFilter))
                .filter(i -> query.isEmpty() || i.getName().toLowerCase().contains(query))
                .toList();
        if (list.isEmpty()) {
            rows.getChildren().add(Ui.muted("Keine Zutaten gefunden."));
        }
        for (Ingredient ingredient : list) {
            rows.getChildren().add(ingredientRow(ingredient));
        }
    }

    private HBox ingredientRow(Ingredient ingredient) {
        StockStatus status = InventoryService.statusOf(ingredient);
        List<Product> usedIn = InventoryService.usedIn(ingredient);

        Label name = new Label(ingredient.getName());
        name.getStyleClass().add("strong");
        Label used = Ui.muted(usedIn.isEmpty() ? "keine Rezeptur"
                : usedIn.size() + (usedIn.size() == 1 ? " Gericht" : " Gerichte"));
        Tooltip.install(used, new Tooltip(usedIn.isEmpty() ? "–"
                : String.join("\n", usedIn.stream().map(Product::getName).toList())));
        VBox nameBox = new VBox(2, name, used);
        nameBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        Label stock = Ui.cell(ingredient.getUnit().formatNumber(ingredient.getStock()), 58);
        stock.getStyleClass().add("strong");

        MenuItem correct = new MenuItem("Bestand korrigieren");
        correct.setOnAction(event -> openCorrectDialog(ingredient));
        MenuItem edit = new MenuItem("Bearbeiten");
        edit.setOnAction(event -> openIngredientDialog(ingredient));
        MenuItem archive = new MenuItem(ingredient.isArchived() ? "Wieder aktivieren" : "Archivieren");
        archive.setOnAction(event -> {
            InventoryService.setArchived(ingredient, !ingredient.isArchived());
            render();
        });
        MenuItem delete = new MenuItem("Löschen");
        delete.setOnAction(event -> deleteIngredient(ingredient));
        MenuButton more = new MenuButton("Mehr", null, correct, edit, archive, delete);
        more.getStyleClass().add("small-menu");

        HBox actions = new HBox(6, Ui.small("+ Eingang", () -> openReceiveDialog(ingredient)), more);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setMinWidth(150);

        return Ui.row(nameBox,
                Ui.cell(ingredient.getCategory(), 110),
                stock,
                Ui.cell(ingredient.getUnit().getLabel(), 72),
                Ui.cell(ingredient.getUnit().format(ingredient.getMinStock()), 88),
                Ui.cellBox(Ui.badge(ingredient.isArchived() ? "Archiviert" : status.toString(),
                        ingredient.isArchived() ? "gray" : Ui.stockColor(status)), 120),
                Ui.cell(ingredient.getLastUpdated().format(SHORT), 82),
                actions);
    }

    private void deleteIngredient(Ingredient ingredient) {
        if (!Ui.confirm("Zutat „" + ingredient.getName() + "“ löschen? Die Lagerhistorie bleibt erhalten.", "Löschen")) {
            return;
        }
        try {
            InventoryService.deleteIngredient(ingredient);
            render();
        } catch (IllegalStateException e) {
            Ui.error(e.getMessage());
        }
    }

    // ---------- Lagerhistorie ----------

    private Node historyPanel() {
        List<String> names = new ArrayList<>();
        names.add(ALL_INGREDIENTS);
        InventoryService.getIngredients(true).forEach(i -> names.add(i.getName()));
        ComboBox<String> filterBox = new ComboBox<>(FXCollections.observableArrayList(names));
        filterBox.setValue(names.contains(historyFilter) ? historyFilter : ALL_INGREDIENTS);
        filterBox.setOnAction(event -> {
            historyFilter = filterBox.getValue();
            render();
        });

        List<StockMovement> movements = InventoryService.getMovements().stream()
                .filter(m -> historyFilter.equals(ALL_INGREDIENTS) || m.ingredientName().equals(historyFilter))
                .limit(100)
                .toList();

        VBox rows = new VBox();
        if (movements.isEmpty()) {
            rows.getChildren().add(Ui.muted("Noch keine Buchungen."));
        }
        for (StockMovement movement : movements) {
            String sign = movement.delta() > 0 ? "+ " : "− ";
            Label amount = Ui.cell(sign + movement.unit().format(Math.abs(movement.delta())), 110);
            amount.getStyleClass().addAll("strong", movement.delta() > 0 ? "plus-text" : "minus-text");
            rows.getChildren().add(Ui.row(
                    Ui.cell(movement.time().format(LONG), 130),
                    Ui.cell(movement.ingredientName(), 170),
                    amount,
                    Ui.cellBox(Ui.badge(movement.type().toString(), movementColor(movement.type())), 180),
                    Ui.growCell(movement.reason())));
        }

        HBox top = new HBox(12, Ui.muted("Wareneingänge, Korrekturen, automatische Abzüge und Rückbuchungen "
                + "(neueste zuerst, höchstens 100)."), Ui.spacer(), filterBox);
        top.setAlignment(Pos.CENTER_LEFT);

        return Ui.panel("Lagerhistorie", top,
                Ui.headingRow(Ui.cell("DATUM / UHRZEIT", 130), Ui.cell("ZUTAT", 170), Ui.cell("MENGE", 110),
                        Ui.cell("ART", 180), Ui.growCell("GRUND")),
                rows);
    }

    private String movementColor(MovementType type) {
        return switch (type) {
            case WARENEINGANG, ANFANGSBESTAND -> "green";
            case BESTELLUNG -> "blue";
            case RUECKBUCHUNG -> "orange";
            case KORREKTUR -> "gray";
        };
    }

    // ---------- Dialoge ----------

    /** Neue Zutat (ingredient == null) oder Zutat bearbeiten. */
    private void openIngredientDialog(Ingredient ingredient) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(ingredient == null ? "Zutat hinzufügen" : "Zutat bearbeiten");
        dialog.setHeaderText(ingredient == null ? "Neue Zutat für das Lager" : ingredient.getName());
        ButtonType save = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField name = new TextField(ingredient == null ? "" : ingredient.getName());
        name.setPromptText("z. B. Rindfleisch");

        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(InventoryService.CATEGORIES));
        category.setEditable(true);
        category.setValue(ingredient == null ? InventoryService.CATEGORIES.get(0) : ingredient.getCategory());

        ComboBox<Unit> unit = new ComboBox<>(FXCollections.observableArrayList(
                ingredient == null ? List.of(Unit.values()) : ingredient.getUnit().compatibleUnits()));
        unit.setValue(ingredient == null ? Unit.KILOGRAMM : ingredient.getUnit());

        TextField stock = new TextField("0");
        TextField minStock = new TextField(ingredient == null ? ""
                : ingredient.getUnit().formatNumber(ingredient.getMinStock()).replace(".", ""));
        minStock.setPromptText("z. B. 3");

        // Beim Wechsel der Einheit den Mindestbestand umrechnen
        unit.valueProperty().addListener((obs, oldUnit, newUnit) -> {
            if (oldUnit != null && newUnit != null && oldUnit.isCompatible(newUnit) && !minStock.getText().isBlank()) {
                try {
                    long base = oldUnit.toBase(Ui.parseAmount(minStock.getText(), "Mindestbestand"));
                    minStock.setText(newUnit.formatNumber(base).replace(".", ""));
                } catch (IllegalArgumentException ignored) {
                    // ungültige Eingabe stehen lassen
                }
            }
        });

        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(12);
        form.addRow(0, new Label("Name"), name);
        form.addRow(1, new Label("Kategorie"), category);
        form.addRow(2, new Label("Einheit"), unit);
        int row = 3;
        if (ingredient == null) {
            form.addRow(row++, new Label("Anfangsbestand"), stock);
        }
        form.addRow(row, new Label("Mindestbestand"), minStock);

        VBox box = new VBox(14, form,
                Ui.muted(ingredient == null ? "Mengen in der gewählten Einheit, z. B. 2,5 (kg)."
                        : "Den Bestand änderst du über „+ Eingang“ oder „Bestand korrigieren“."),
                error);
        box.setPrefWidth(440);
        dialog.getDialogPane().setContent(box);

        dialog.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, event -> {
            try {
                long min = unit.getValue().toBase(Ui.parseAmount(minStock.getText(), "Mindestbestand"));
                if (ingredient == null) {
                    long start = unit.getValue().toBase(Ui.parseAmount(stock.getText(), "Anfangsbestand"));
                    InventoryService.addIngredient(name.getText(), category.getValue(), unit.getValue(), start, min);
                } else {
                    InventoryService.updateIngredient(ingredient, name.getText(), category.getValue(), unit.getValue(), min);
                }
            } catch (IllegalArgumentException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });

        Ui.styleDialog(dialog);
        dialog.showAndWait();
        render();
    }

    /** Wareneingang: Bestand erhöhen. */
    private void openReceiveDialog(Ingredient ingredient) {
        amountDialog(ingredient, "Wareneingang", "Gelieferte Menge", "Lieferung", false);
    }

    /** Korrektur: neuen tatsächlichen Bestand eintragen. */
    private void openCorrectDialog(Ingredient ingredient) {
        amountDialog(ingredient, "Bestand korrigieren", "Neuer Bestand", "", true);
    }

    private void amountDialog(Ingredient ingredient, String title, String amountLabel,
                              String defaultReason, boolean correction) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(ingredient.getName() + " · aktuell " + ingredient.getUnit().format(ingredient.getStock()));
        ButtonType save = new ButtonType("Buchen", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField amount = new TextField(correction
                ? ingredient.getUnit().formatNumber(ingredient.getStock()).replace(".", "") : "");
        amount.setPromptText("z. B. 5");
        ComboBox<Unit> unit = new ComboBox<>(FXCollections.observableArrayList(ingredient.getUnit().compatibleUnits()));
        unit.setValue(ingredient.getUnit());
        TextField reason = new TextField(defaultReason);
        reason.setPromptText(correction ? "z. B. Inventur, verdorben, Bruch" : "z. B. Lieferung Metro");

        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(12);
        form.addRow(0, new Label(amountLabel), new HBox(8, amount, unit));
        form.addRow(1, new Label(correction ? "Grund (Pflicht)" : "Grund"), reason);

        VBox box = new VBox(14, form, error);
        box.setPrefWidth(420);
        dialog.getDialogPane().setContent(box);

        dialog.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, event -> {
            try {
                long base = unit.getValue().toBase(Ui.parseAmount(amount.getText(), amountLabel));
                if (correction) {
                    InventoryService.correct(ingredient, base, reason.getText());
                } else {
                    InventoryService.receive(ingredient, base, reason.getText());
                }
            } catch (IllegalArgumentException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });

        Ui.styleDialog(dialog);
        dialog.showAndWait();
        render();
    }
}
