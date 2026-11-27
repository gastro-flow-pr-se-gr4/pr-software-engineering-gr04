package at.gastroflow.frontend.management;

import at.gastroflow.backend.model.OrderStatus;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public final class Ui {

    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM.");
    public static final String[] WEEKDAYS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};

    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.GERMANY);
    private static final String CSS = "/at/gastroflow/frontend/styles/management.css";

    private Ui() {
    }

    public static String money(double value) {
        return MONEY.format(value);
    }

    public static String percent(double ratio) {
        return String.format(Locale.GERMANY, "%.0f %%", ratio * 100);
    }

    public static LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text.trim(), TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Bitte die Uhrzeit im Format HH:MM eingeben, z. B. 08:00.");
        }
    }

    public static ScrollPane page(VBox content) {
        content.getStyleClass().add("content-layout");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        return scroll;
    }

    public static HBox header(String title, String subtitle, Node... actions) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("page-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("page-subtitle");

        HBox box = new HBox(12, new VBox(6, titleLabel, subtitleLabel), spacer());
        box.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(actions);
        return box;
    }

    public static VBox statCard(String title, String value, String description) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-title");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-number");
        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("stat-description");
        descriptionLabel.setWrapText(true);

        VBox card = new VBox(8, titleLabel, valueLabel, descriptionLabel);
        card.getStyleClass().add("stat-card");
        card.setPrefWidth(220);
        return card;
    }

    public static VBox cards(Region... cards) {
        int perRow = cards.length <= 5 ? cards.length : 4;
        VBox rows = new VBox(20);
        for (int start = 0; start < cards.length; start += perRow) {
            HBox row = new HBox(20);
            for (int i = start; i < Math.min(start + perRow, cards.length); i++) {
                cards[i].setPrefWidth(200);
                cards[i].setMinWidth(120);
                cards[i].setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(cards[i], Priority.ALWAYS);
                row.getChildren().add(cards[i]);
            }
            rows.getChildren().add(row);
        }
        return rows;
    }

    public static VBox panel(String title, Node... content) {
        VBox panel = new VBox(16);
        panel.getStyleClass().add("panel");
        if (title != null) {
            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("section-title");
            panel.getChildren().add(titleLabel);
        }
        panel.getChildren().addAll(content);
        return panel;
    }

    public static HBox columns(Region... regions) {
        HBox box = new HBox(20);
        for (Region region : regions) {
            region.setPrefWidth(420);
            region.setMinWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(region, Priority.ALWAYS);
            box.getChildren().add(region);
        }
        return box;
    }

    public static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    public static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }

    public static Label badge(String text, String color) {
        Label label = new Label(text);
        label.getStyleClass().addAll("status-label", "status-" + color);
        return label;
    }

    public static Label cell(String text, double width) {
        Label label = new Label(text);
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
        return label;
    }

    public static Label growCell(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        return label;
    }

    public static HBox cellBox(Node node, double width) {
        HBox box = new HBox(node);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(width);
        box.setPrefWidth(width);
        return box;
    }

    public static HBox row(Node... nodes) {
        HBox row = new HBox(12, nodes);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillHeight(false);
        row.getStyleClass().add("data-row");
        return row;
    }

    public static HBox headingRow(Node... nodes) {
        HBox row = new HBox(12, nodes);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("table-heading");
        return row;
    }

    public static Button primary(String text, Runnable action) {
        return button(text, "primary-button", action);
    }

    public static Button secondary(String text, Runnable action) {
        return button(text, "secondary-button", action);
    }

    public static Button danger(String text, Runnable action) {
        return button(text, "danger-button", action);
    }

    public static Button small(String text, Runnable action) {
        return button(text, "small-button", action);
    }

    private static Button button(String text, String styleClass, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        button.setOnAction(event -> action.run());
        return button;
    }

    public static HBox segment(List<String> options, String active, Consumer<String> onSelect) {
        HBox box = new HBox(4);
        box.getStyleClass().add("segment");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        for (String option : options) {
            Button button = new Button(option);
            button.getStyleClass().add("segment-button");
            if (option.equals(active)) {
                button.getStyleClass().add("segment-active");
            }
            button.setOnAction(event -> onSelect.accept(option));
            box.getChildren().add(button);
        }
        return box;
    }

    public static void styleDialog(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(Ui.class.getResource(CSS).toExternalForm());
        dialog.getDialogPane().getStyleClass().add("gf-dialog");
    }

    public static boolean confirm(String text, String yesText) {
        ButtonType yes = new ButtonType(yesText, ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, yes, no);
        alert.setHeaderText(null);
        alert.setTitle("Bitte bestätigen");
        styleDialog(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == yes;
    }

    public static void error(String text) {
        show(Alert.AlertType.WARNING, "Hinweis", text);
    }

    public static void info(String text) {
        show(Alert.AlertType.INFORMATION, "GastroFlow", text);
    }

    private static void show(Alert.AlertType type, String title, String text) {
        Alert alert = new Alert(type, text, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle(title);
        styleDialog(alert);
        alert.showAndWait();
    }

    public static String orderColor(OrderStatus status) {
        return switch (status) {
            case NEU -> "orange";
            case IN_ZUBEREITUNG -> "blue";
            case FERTIG -> "green";
            case ABGESCHLOSSEN -> "gray";
            case STORNIERT -> "red";
        };
    }

    public static double parseAmount(String text, String field) {
        try {
            double value = Double.parseDouble(text.trim().replace(",", "."));
            if (value < 0) {
                throw new IllegalArgumentException("„" + field + "“ darf nicht negativ sein.");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bitte bei „" + field + "“ eine Zahl eingeben, z. B. 1,5.");
        }
    }

    public static void showPhone(String name, String phone) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Telefon");
        dialog.setHeaderText("☎  " + name);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(12);
        content.setPrefWidth(380);
        if (phone == null || phone.isBlank()) {
            content.getChildren().add(muted("Keine Telefonnummer hinterlegt."));
        } else {
            TextField number = new TextField(phone);
            number.setEditable(false);
            number.getStyleClass().add("phone-field");
            Label feedback = new Label();
            feedback.getStyleClass().add("success-text");

            HBox buttons = new HBox(10,
                    secondary("Kopieren", () -> {
                        ClipboardContent clip = new ClipboardContent();
                        clip.putString(phone);
                        Clipboard.getSystemClipboard().setContent(clip);
                        feedback.setText("✓ Nummer kopiert");
                    }),
                    primary("Anrufen", () -> callPhone(phone, feedback)));
            content.getChildren().addAll(number, buttons, feedback);
        }
        dialog.getDialogPane().setContent(content);
        styleDialog(dialog);
        dialog.showAndWait();
    }

    private static void callPhone(String phone, Label feedback) {
        String link = "tel:" + phone.replaceAll("[^+0-9]", "");
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> command = os.contains("mac") ? List.of("open", link)
                : os.contains("win") ? List.of("rundll32", "url.dll,FileProtocolHandler", link)
                : List.of("xdg-open", link);
        Thread thread = new Thread(() -> {
            String message;
            try {
                int exit = new ProcessBuilder(command).start().waitFor();
                message = exit == 0 ? "✓ Telefon-App wird geöffnet …"
                        : "Anrufen wird auf diesem Gerät nicht unterstützt – bitte Nummer kopieren.";
            } catch (Exception e) {
                message = "Anrufen wird auf diesem Gerät nicht unterstützt – bitte Nummer kopieren.";
            }
            String text = message;
            Platform.runLater(() -> feedback.setText(text));
        }, "gastroflow-anruf");
        thread.setDaemon(true);
        thread.start();
    }

    public static Button phoneButton(String name, String phone) {
        Button button = small("☎", () -> showPhone(name, phone));
        button.setTooltip(new javafx.scene.control.Tooltip(
                phone == null || phone.isBlank() ? "Keine Telefonnummer hinterlegt" : phone));
        return button;
    }

}
