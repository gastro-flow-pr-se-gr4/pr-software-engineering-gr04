package at.gastroflow.frontend.management;

import at.gastroflow.backend.model.Reservation;
import at.gastroflow.backend.model.ReservationStatus;
import at.gastroflow.backend.model.RestaurantSettings;
import at.gastroflow.backend.service.ReservationService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Dialoge für Reservierungen (genutzt von Tagesübersicht und Kalender – gleiche Daten). */
public final class ReservationDialog {

    private ReservationDialog() {
    }

    /**
     * Neue Reservierung (existing == null) oder bestehende bearbeiten.
     * Gibt true zurück, wenn gespeichert wurde.
     */
    public static boolean show(Reservation existing, LocalDate suggestedDate) {
        RestaurantSettings settings = RestaurantSettings.get();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Neue Reservierung" : "Reservierung bearbeiten");
        dialog.setHeaderText(existing == null ? "Neue Reservierung anlegen" : "Reservierung von " + existing.getName());
        ButtonType save = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        // Eingabefelder
        TextField name = new TextField(existing == null ? "" : existing.getName());
        name.setPromptText("Name des Gastes");
        TextField phone = new TextField(existing == null ? "" : existing.getPhone());
        phone.setPromptText("z. B. +43 660 1234567 (optional)");

        DatePicker date = new DatePicker(existing == null ? suggestedDate : existing.getStart().toLocalDate());

        ComboBox<String> time = new ComboBox<>();
        for (LocalTime t = settings.getOpeningTime(); t.isBefore(settings.getClosingTime()); t = t.plusMinutes(15)) {
            time.getItems().add(t.format(Ui.TIME));
        }
        String startText = existing != null
                ? existing.getStart().toLocalTime().format(Ui.TIME)
                : (time.getItems().contains("18:00") ? "18:00" : time.getItems().get(0));
        if (!time.getItems().contains(startText)) {
            time.getItems().add(0, startText);
        }
        time.setValue(startText);

        Spinner<Integer> duration = new Spinner<>(30, 300,
                existing == null ? settings.getDefaultReservationMinutes() : existing.getDurationMinutes(), 15);
        Spinner<Integer> persons = new Spinner<>(1, 30, existing == null ? 2 : existing.getPersons());

        ComboBox<Integer> table = new ComboBox<>();
        for (int i = 1; i <= settings.getTableCount(); i++) {
            table.getItems().add(i);
        }
        table.setValue(existing == null ? 1 : Math.min(existing.getTable(), settings.getTableCount()));
        Label seats = Ui.muted("");

        ComboBox<ReservationStatus> status = new ComboBox<>(
                FXCollections.observableArrayList(ReservationStatus.values()));
        status.setValue(existing == null ? ReservationStatus.BESTAETIGT : existing.getStatus());

        TextArea note = new TextArea(existing == null ? "" : existing.getNote());
        note.setPromptText("z. B. Kinderstuhl, Allergie, Geburtstag …");
        note.setPrefRowCount(2);
        note.setWrapText(true);

        // Live-Prüfung: Tisch frei und genug Plätze?
        Label availability = new Label();
        availability.setWrapText(true);
        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        Runnable check = () -> {
            LocalDateTime start = startOf(date, time);
            if (start == null || table.getValue() == null) {
                availability.setText("");
                return;
            }
            int capacity = settings.getCapacity(table.getValue());
            seats.setText(capacity + " Plätze");
            List<Integer> free = ReservationService.getFreeTables(start, duration.getValue(), persons.getValue(), existing);
            String freeText = free.isEmpty() ? "Zu dieser Zeit ist kein passender Tisch frei."
                    : "Passende freie Tische: " + join(free);

            Optional<Reservation> conflict = ReservationService.findConflict(
                    table.getValue(), start, duration.getValue(), existing);
            if (persons.getValue() > capacity) {
                availability.setText("✗ Tisch " + table.getValue() + " hat nur " + capacity + " Plätze. " + freeText);
                availability.getStyleClass().setAll("label", "error-text");
            } else if (conflict.isPresent()) {
                Reservation other = conflict.get();
                availability.setText("✗ Tisch " + table.getValue() + " ist belegt (" + other.getName() + ", "
                        + other.getStart().format(Ui.TIME) + "–" + other.getEnd().format(Ui.TIME) + " Uhr). " + freeText);
                availability.getStyleClass().setAll("label", "error-text");
            } else {
                availability.setText("✓ Tisch " + table.getValue() + " ist frei und hat genug Plätze.");
                availability.getStyleClass().setAll("label", "success-text");
            }
        };
        date.valueProperty().addListener((obs, oldValue, newValue) -> check.run());
        time.valueProperty().addListener((obs, oldValue, newValue) -> check.run());
        duration.valueProperty().addListener((obs, oldValue, newValue) -> check.run());
        persons.valueProperty().addListener((obs, oldValue, newValue) -> check.run());
        table.valueProperty().addListener((obs, oldValue, newValue) -> check.run());
        check.run();

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(12);
        form.addRow(0, new Label("Gastname"), name);
        form.addRow(1, new Label("Telefonnummer"), phone);
        form.addRow(2, new Label("Datum"), date);
        form.addRow(3, new Label("Uhrzeit"), time);
        form.addRow(4, new Label("Personen"), persons);
        form.addRow(5, new Label("Tisch"), new HBox(10, table, seats));
        form.addRow(6, new Label("Aufenthalt (Minuten)"), duration);
        form.addRow(7, new Label("Status"), status);
        form.addRow(8, new Label("Notizen"), note);

        VBox content = new VBox(14, form, availability, error);
        content.setPrefWidth(500);
        dialog.getDialogPane().setContent(content);

        // Speichern nur, wenn alles gültig ist – sonst bleibt der Dialog offen
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                LocalDateTime start = startOf(date, time);
                if (existing == null) {
                    ReservationService.add(name.getText(), phone.getText(), persons.getValue(), table.getValue(),
                            start, duration.getValue(), status.getValue(), note.getText());
                } else {
                    ReservationService.update(existing, name.getText(), phone.getText(), persons.getValue(),
                            table.getValue(), start, duration.getValue(), status.getValue(), note.getText());
                }
            } catch (IllegalArgumentException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });

        Ui.styleDialog(dialog);
        Optional<ButtonType> result = dialog.showAndWait();
        return result.isPresent() && result.get() == save;
    }

    /** Lässt einen freien, passenden Tisch auswählen. Gibt true zurück, wenn ein Tisch zugewiesen wurde. */
    public static boolean assignTable(Reservation reservation) {
        List<Integer> free = ReservationService.getFreeTables(
                reservation.getStart(), reservation.getDurationMinutes(), reservation.getPersons(), reservation);
        if (free.isEmpty()) {
            Ui.error("Zu dieser Zeit ist kein passender Tisch für " + reservation.getPersons() + " Personen frei.");
            return false;
        }
        Integer suggestion = free.contains(reservation.getTable()) ? reservation.getTable() : free.get(0);
        ChoiceDialog<Integer> dialog = new ChoiceDialog<>(suggestion, free);
        dialog.setTitle("Tisch zuweisen");
        dialog.setHeaderText("Freie Tische für " + reservation.getName() + " (" + reservation.getPersons() + " Personen, "
                + reservation.getStart().format(Ui.TIME) + "–" + reservation.getEnd().format(Ui.TIME) + " Uhr)");
        dialog.setContentText("Tisch:");
        Ui.styleDialog(dialog);

        Optional<Integer> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return false;
        }
        try {
            ReservationService.assignTable(reservation, result.get());
            return true;
        } catch (IllegalArgumentException e) {
            Ui.error(e.getMessage());
            return false;
        }
    }

    /** Fragt nach und löscht die Reservierung. */
    public static boolean cancel(Reservation reservation) {
        boolean yes = Ui.confirm("Reservierung von " + reservation.getName() + " am "
                + reservation.getStart().format(Ui.DATE) + " um " + reservation.getStart().format(Ui.TIME)
                + " Uhr stornieren?", "Stornieren");
        if (yes) {
            ReservationService.delete(reservation);
        }
        return yes;
    }

    /** Text für Tooltips: alle Infos zu einer Reservierung. */
    public static String details(Reservation reservation, LocalDateTime now) {
        return reservation.getName() + "\n"
                + reservation.getStart().format(Ui.TIME) + "–" + reservation.getEnd().format(Ui.TIME) + " Uhr"
                + " (" + reservation.getDurationMinutes() + " Min.)\n"
                + reservation.getPersons() + " Personen · Tisch " + reservation.getTable() + "\n"
                + "Telefon: " + (reservation.hasPhone() ? reservation.getPhone() : "Keine Telefonnummer hinterlegt") + "\n"
                + "Status: " + Ui.reservationLabel(reservation, now)
                + (reservation.getNote() == null || reservation.getNote().isBlank() ? "" : "\nNotiz: " + reservation.getNote());
    }

    private static LocalDateTime startOf(DatePicker date, ComboBox<String> time) {
        if (date.getValue() == null || time.getValue() == null) {
            return null;
        }
        return date.getValue().atTime(LocalTime.parse(time.getValue(), Ui.TIME));
    }

    private static String join(List<Integer> tables) {
        return tables.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
