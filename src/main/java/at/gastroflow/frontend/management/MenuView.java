package at.gastroflow.frontend.management;

import at.gastroflow.backend.model.Ingredient;
import at.gastroflow.backend.model.Product;
import at.gastroflow.backend.model.RecipeItem;
import at.gastroflow.backend.model.Unit;
import at.gastroflow.backend.service.InventoryService;
import at.gastroflow.backend.service.MenuService;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Speisekartenverwaltung: Kategorien, Produkte, Rezepturen und mögliche Portionen. */
public class MenuView {

    private final VBox content = new VBox(24);
    private String selectedCategory = null; // null = alle Produkte
    private String search = "";

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {
        HBox layout = new HBox(20, categoriesPanel(), productsPanel());

        content.getChildren().setAll(
                Ui.header("Speisekarte", "Produkte, Preise, Allergene, Rezepturen und Verfügbarkeit verwalten",
                        Ui.secondary("+ Kategorie", this::addCategory),
                        Ui.primary("+ Produkt", () -> openProductDialog(null))),
                layout
        );
    }

    // ---------- Kategorien ----------

    private VBox categoriesPanel() {
        VBox list = new VBox(4);
        list.getChildren().add(categoryRow(null, "Alle Produkte", MenuService.getProducts().size()));
        for (String category : MenuService.getCategories()) {
            list.getChildren().add(categoryRow(category, category,
                    MenuService.getProductsInCategory(category).size()));
        }
        VBox panel = Ui.panel("Kategorien", list);
        panel.setMinWidth(240);
        panel.setPrefWidth(240);
        return panel;
    }

    private HBox categoryRow(String category, String text, int count) {
        Label name = Ui.growCell(text);
        Label countLabel = new Label(String.valueOf(count));
        countLabel.getStyleClass().add("muted");

        HBox row = new HBox(8, name, countLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("category-row");
        boolean active = category == null ? selectedCategory == null : category.equals(selectedCategory);
        if (active) {
            row.getStyleClass().add("category-active");
        }
        row.setOnMouseClicked(event -> {
            selectedCategory = category;
            render();
        });

        if (category != null) {
            row.getChildren().addAll(
                    Ui.small("✎", () -> renameCategory(category)),
                    Ui.small("✕", () -> deleteCategory(category)));
        }
        return row;
    }

    private void addCategory() {
        askText("Kategorie hinzufügen", "Name der neuen Kategorie:", "").ifPresent(name -> {
            try {
                MenuService.addCategory(name);
                render();
            } catch (IllegalArgumentException e) {
                Ui.error(e.getMessage());
            }
        });
    }

    private void renameCategory(String category) {
        askText("Kategorie umbenennen", "Neuer Name für „" + category + "“:", category).ifPresent(name -> {
            try {
                MenuService.renameCategory(category, name);
                if (category.equals(selectedCategory)) {
                    selectedCategory = name.trim();
                }
                render();
            } catch (IllegalArgumentException e) {
                Ui.error(e.getMessage());
            }
        });
    }

    private void deleteCategory(String category) {
        if (!Ui.confirm("Kategorie „" + category + "“ löschen?", "Löschen")) {
            return;
        }
        try {
            MenuService.deleteCategory(category);
            if (category.equals(selectedCategory)) {
                selectedCategory = null;
            }
            render();
        } catch (IllegalStateException e) {
            Ui.error(e.getMessage());
        }
    }

    private Optional<String> askText(String title, String text, String value) {
        TextInputDialog dialog = new TextInputDialog(value);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(text);
        Ui.styleDialog(dialog);
        return dialog.showAndWait();
    }

    // ---------- Produkte ----------

    private VBox productsPanel() {
        TextField searchField = new TextField(search);
        searchField.setPromptText("Produkt suchen …");
        searchField.setPrefWidth(240);

        VBox rows = new VBox();
        fillProductRows(rows);
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            search = newText;
            fillProductRows(rows);
        });

        HBox top = new HBox(12, searchField, Ui.spacer(),
                Ui.muted("Allergene: A Gluten · C Ei · D Fisch · G Milch · L Sellerie · M Senf"));
        top.setAlignment(Pos.CENTER_LEFT);

        VBox panel = Ui.panel(selectedCategory == null ? "Alle Produkte" : selectedCategory, top,
                Ui.muted("Portionen = wie oft das Gericht mit dem aktuellen Lager noch möglich ist (knappste Zutat). "
                        + "Gerichte teilen sich Zutaten – die Werte sind Einzelschätzungen und dürfen nicht addiert werden."),
                Ui.headingRow(Ui.cell("", 48), Ui.growCell("PRODUKT"), Ui.cell("PREIS", 72),
                        Ui.cell("PORTIONEN", 82), Ui.cell("AKTIV", 70), Ui.cell("", 170)),
                rows);
        HBox.setHgrow(panel, Priority.ALWAYS);
        panel.setMaxWidth(Double.MAX_VALUE);
        return panel;
    }

    private void fillProductRows(VBox rows) {
        rows.getChildren().clear();
        String query = search.trim().toLowerCase();
        List<Product> products = MenuService.getProducts().stream()
                .filter(p -> selectedCategory == null || p.getCategory().equals(selectedCategory))
                .filter(p -> query.isEmpty() || p.getName().toLowerCase().contains(query))
                .toList();

        if (products.isEmpty()) {
            rows.getChildren().add(Ui.muted("Keine Produkte gefunden."));
        }
        for (Product product : products) {
            rows.getChildren().add(productRow(product));
        }
    }

    private HBox productRow(Product product) {
        Label name = new Label(product.getName());
        name.getStyleClass().add("strong");
        Label details = Ui.muted(product.getCategory() + "  ·  " + (product.getAllergens().isEmpty()
                ? "keine Allergene" : "Allergene: " + product.getAllergens())
                + "  ·  " + (product.getRecipe().isEmpty() ? "keine Rezeptur"
                : product.getRecipe().size() + (product.getRecipe().size() == 1 ? " Zutat" : " Zutaten")));
        VBox nameBox = new VBox(3, name, details);
        nameBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        Label price = Ui.cell(Ui.money(product.getPrice()), 72);
        price.getStyleClass().add("strong");

        CheckBox available = new CheckBox(product.isAvailable() ? "Ja" : "Nein");
        available.setSelected(product.isAvailable());
        available.setMinWidth(70);
        available.setOnAction(event -> {
            MenuService.setAvailable(product, available.isSelected());
            render();
        });

        HBox actions = new HBox(8,
                Ui.small("Bearbeiten", () -> openProductDialog(product)),
                Ui.small("Löschen", () -> deleteProduct(product)));
        actions.setMinWidth(170);

        HBox row = Ui.row(thumbnail(product.getImagePath(), product.getName()), nameBox,
                price, Ui.cellBox(portionsBadge(product), 82), available, actions);
        if (!product.isAvailable()) {
            row.getStyleClass().add("row-disabled");
        }
        return row;
    }

    private Node portionsBadge(Product product) {
        Integer portions = InventoryService.portionsFor(product);
        if (portions == null) {
            return Ui.muted("–");
        }
        String color = portions == 0 ? "red" : portions <= 5 ? "orange" : "green";
        return Ui.badge(String.valueOf(portions), color);
    }

    private Node thumbnail(String imagePath, String name) {
        StackPane box = new StackPane();
        box.getStyleClass().add("thumbnail");
        box.setMinSize(48, 48);
        box.setMaxSize(48, 48);
        if (imagePath != null) {
            ImageView image = new ImageView(new Image(imagePath, 48, 48, true, true, true));
            box.getChildren().add(image);
        } else {
            Label letter = new Label(name.isEmpty() ? "?" : name.substring(0, 1));
            letter.getStyleClass().add("thumbnail-letter");
            box.getChildren().add(letter);
        }
        return box;
    }

    private void deleteProduct(Product product) {
        if (Ui.confirm("„" + product.getName() + "“ endgültig von der Speisekarte löschen?\n"
                + "Bestehende Bestellungen bleiben unverändert.", "Löschen")) {
            MenuService.deleteProduct(product);
            InventoryService.persist();
            render();
        }
    }

    // ---------- Produkt-Dialog inklusive Rezeptur ----------

    /** Eine Zeile im Rezeptur-Editor. */
    private static final class RecipeRow {
        final ComboBox<Ingredient> ingredient = new ComboBox<>();
        final TextField amount = new TextField();
        final ComboBox<Unit> unit = new ComboBox<>();
        HBox node;
    }

    /** Dialog zum Hinzufügen (product == null) oder Bearbeiten eines Produkts. */
    private void openProductDialog(Product product) {
        if (MenuService.getCategories().isEmpty()) {
            Ui.error("Bitte zuerst eine Kategorie anlegen.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(product == null ? "Produkt hinzufügen" : "Produkt bearbeiten");
        dialog.setHeaderText(product == null ? "Neues Produkt für die Speisekarte" : product.getName());
        ButtonType save = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField name = new TextField(product == null ? "" : product.getName());
        name.setPromptText("z. B. Pizza Funghi");
        TextField price = new TextField(product == null ? "" : String.format("%.2f", product.getPrice()).replace('.', ','));
        price.setPromptText("z. B. 12,90");

        ComboBox<String> category = new ComboBox<>();
        category.getItems().addAll(MenuService.getCategories());
        category.setValue(product != null ? product.getCategory()
                : selectedCategory != null ? selectedCategory : MenuService.getCategories().get(0));

        TextField allergens = new TextField(product == null ? "" : product.getAllergens());
        allergens.setPromptText("z. B. A, C, G");

        CheckBox available = new CheckBox("Kann bestellt werden");
        available.setSelected(product == null || product.isAvailable());

        // Produktbild
        String[] imagePath = {product == null ? null : product.getImagePath()};
        HBox imageBox = new HBox(10);
        imageBox.setAlignment(Pos.CENTER_LEFT);
        rebuildImageBox(imageBox, imagePath, name, dialog);

        // Rezeptur
        List<RecipeRow> recipeRows = new ArrayList<>();
        VBox recipeBox = new VBox(8);
        if (product != null) {
            for (RecipeItem item : product.getRecipe()) {
                InventoryService.findIngredient(item.ingredientId()).ifPresent(ingredient ->
                        addRecipeRow(recipeBox, recipeRows, ingredient, item.amount()));
            }
        }
        Node addRow = Ui.small("+ Zutat hinzufügen", () -> addRecipeRow(recipeBox, recipeRows, null, 0));

        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(12);
        form.addRow(0, new Label("Name"), name);
        form.addRow(1, new Label("Preis (€)"), price);
        form.addRow(2, new Label("Kategorie"), category);
        form.addRow(3, new Label("Allergene"), allergens);
        form.addRow(4, new Label("Bild"), imageBox);
        form.addRow(5, new Label("Verfügbar"), available);

        Label recipeTitle = new Label("Rezeptur (Menge pro Portion)");
        recipeTitle.getStyleClass().add("field-label");
        VBox box = new VBox(14, form, new javafx.scene.control.Separator(), recipeTitle,
                Ui.muted("Diese Zutaten werden bei jeder Bestellung automatisch vom Lager abgezogen."),
                recipeBox, addRow, error);
        box.setPrefWidth(580);
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(560);
        scroll.getStyleClass().add("dialog-scroll");
        dialog.getDialogPane().setContent(scroll);

        dialog.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, event -> {
            try {
                double value = parsePrice(price.getText());
                List<RecipeItem> recipe = readRecipe(recipeRows);
                Product target = product;
                if (target == null) {
                    target = MenuService.addProduct(name.getText(), value, category.getValue(),
                            allergens.getText(), imagePath[0], available.isSelected());
                } else {
                    MenuService.updateProduct(target, name.getText(), value, category.getValue(),
                            allergens.getText(), imagePath[0], available.isSelected());
                }
                InventoryService.setRecipe(target, recipe);
            } catch (IllegalArgumentException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });

        Ui.styleDialog(dialog);
        dialog.showAndWait();
        render();
    }

    private void addRecipeRow(VBox recipeBox, List<RecipeRow> rows, Ingredient preset, long amountBase) {
        RecipeRow row = new RecipeRow();
        row.ingredient.getItems().addAll(InventoryService.getIngredients(false));
        if (preset != null && !row.ingredient.getItems().contains(preset)) {
            row.ingredient.getItems().add(preset); // archivierte Zutat, die schon in der Rezeptur steht
        }
        row.ingredient.setPromptText("Zutat wählen");
        row.ingredient.setPrefWidth(230);
        row.amount.setPromptText("Menge");
        row.amount.setPrefWidth(90);
        row.unit.setPrefWidth(130);

        row.ingredient.valueProperty().addListener((obs, oldValue, ingredient) -> {
            if (ingredient != null) {
                row.unit.getItems().setAll(ingredient.getUnit().compatibleUnits());
                row.unit.setValue(ingredient.getUnit().baseUnit());
            }
        });
        if (preset != null) {
            row.ingredient.setValue(preset);
            Unit base = preset.getUnit().baseUnit();
            row.unit.setValue(base);
            row.amount.setText(base.formatNumber(amountBase).replace(".", ""));
        }

        row.node = new HBox(8, row.ingredient, row.amount, row.unit);
        row.node.setAlignment(Pos.CENTER_LEFT);
        row.node.getChildren().add(Ui.small("✕", () -> {
            rows.remove(row);
            recipeBox.getChildren().remove(row.node);
        }));
        rows.add(row);
        recipeBox.getChildren().add(row.node);
    }

    private List<RecipeItem> readRecipe(List<RecipeRow> rows) {
        List<RecipeItem> items = new ArrayList<>();
        for (RecipeRow row : rows) {
            if (row.ingredient.getValue() == null && row.amount.getText().isBlank()) {
                continue; // leere Zeile ignorieren
            }
            if (row.ingredient.getValue() == null) {
                throw new IllegalArgumentException("Bitte in jeder Rezeptur-Zeile eine Zutat auswählen.");
            }
            Ingredient ingredient = row.ingredient.getValue();
            double value = Ui.parseAmount(row.amount.getText(), "Menge " + ingredient.getName());
            long base = row.unit.getValue().toBase(value);
            if (base <= 0) {
                throw new IllegalArgumentException("Die Menge für „" + ingredient.getName() + "“ muss größer als 0 sein.");
            }
            items.add(new RecipeItem(ingredient.getId(), base));
        }
        return items;
    }

    /** Zeigt Vorschau sowie die Buttons zum Auswählen und Entfernen des Bildes. */
    private void rebuildImageBox(HBox imageBox, String[] imagePath, TextField name, Dialog<ButtonType> dialog) {
        imageBox.getChildren().setAll(
                thumbnail(imagePath[0], name.getText()),
                Ui.small("Bild wählen …", () -> {
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("Produktbild wählen");
                    chooser.getExtensionFilters().add(
                            new FileChooser.ExtensionFilter("Bilder", "*.png", "*.jpg", "*.jpeg", "*.gif"));
                    File file = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
                    if (file != null) {
                        imagePath[0] = file.toURI().toString();
                        rebuildImageBox(imageBox, imagePath, name, dialog);
                    }
                }),
                Ui.small("Entfernen", () -> {
                    imagePath[0] = null;
                    rebuildImageBox(imageBox, imagePath, name, dialog);
                }));
    }

    private double parsePrice(String text) {
        try {
            return Double.parseDouble(text.trim().replace("€", "").replace(",", ".").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bitte einen gültigen Preis eingeben, z. B. 12,90.");
        }
    }
}
