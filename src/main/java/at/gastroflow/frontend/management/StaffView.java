package at.gastroflow.frontend.management;

import at.gastroflow.backend.model.Employee;
import at.gastroflow.backend.model.EmployeeStatus;
import at.gastroflow.backend.service.StaffService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/** Personalübersicht mit Schichten und aktuellem Status. */
public class StaffView {

    private static final List<String> ROLES = List.of("Service", "Küche", "Bar", "Leitung");
    private static final List<String> SHIFTS = List.of("Frühschicht", "Mittelschicht", "Spätschicht");

    private final VBox content = new VBox(24);

    public Node build() {
        render();
        return Ui.page(content);
    }

    private void render() {
        List<Employee> employees = StaffService.getEmployees();

        VBox rows = new VBox();
        if (employees.isEmpty()) {
            rows.getChildren().add(Ui.muted("Noch keine Mitarbeiter angelegt."));
        }
        for (Employee employee : employees) {
            rows.getChildren().add(employeeRow(employee));
        }

        content.getChildren().setAll(
                Ui.header("Personal", "Wer arbeitet heute wann – und wer ist gerade da?",
                        Ui.primary("+ Mitarbeiter", () -> openDialog(null))),
                Ui.cards(
                        Ui.statCard("IM DIENST", count(EmployeeStatus.IM_DIENST), "Arbeiten gerade"),
                        Ui.statCard("PAUSE", count(EmployeeStatus.PAUSE), "Kurz nicht verfügbar"),
                        Ui.statCard("NOCH NICHT ERSCHIENEN", count(EmployeeStatus.NICHT_ERSCHIENEN), "Schicht noch offen"),
                        Ui.statCard("FEIERABEND", count(EmployeeStatus.FEIERABEND), "Schicht beendet")),
                Ui.demoHint("Die Mitarbeiter und Schichten sind Beispiele – es gibt noch keine Personalverwaltung im System."),
                Ui.panel("Schichtplan heute",
                        Ui.muted("Den Status kannst du direkt in der Liste ändern."),
                        Ui.headingRow(Ui.growCell("NAME"), Ui.cell("ROLLE", 80), Ui.cell("SCHICHT", 110),
                                Ui.cell("BEGINN", 60), Ui.cell("ENDE", 60), Ui.cell("STATUS", 200), Ui.cell("", 170)),
                        rows)
        );
    }

    private HBox employeeRow(Employee employee) {
        Label name = Ui.growCell(employee.getName());
        name.getStyleClass().add("strong");

        ComboBox<EmployeeStatus> status = new ComboBox<>(FXCollections.observableArrayList(EmployeeStatus.values()));
        status.setValue(employee.getStatus());
        status.getStyleClass().add("status-combo-" + Ui.employeeColor(employee.getStatus()));
        status.setPrefWidth(200);
        status.setMinWidth(200);
        status.setOnAction(event -> {
            StaffService.setStatus(employee, status.getValue());
            render();
        });

        HBox actions = new HBox(6,
                Ui.small("Bearbeiten", () -> openDialog(employee)),
                Ui.small("Löschen", () -> {
                    if (Ui.confirm(employee.getName() + " aus der Personalliste löschen?", "Löschen")) {
                        StaffService.delete(employee);
                        render();
                    }
                }));
        actions.setMinWidth(170);

        return Ui.row(name,
                Ui.cell(employee.getRole(), 80),
                Ui.cell(employee.getShift(), 110),
                Ui.cell(employee.getShiftStart().format(Ui.TIME), 60),
                Ui.cell(employee.getShiftEnd().format(Ui.TIME), 60),
                status,
                actions);
    }

    private String count(EmployeeStatus status) {
        return String.valueOf(StaffService.getEmployees().stream().filter(e -> e.getStatus() == status).count());
    }

    /** Dialog zum Anlegen (employee == null) oder Bearbeiten. */
    private void openDialog(Employee employee) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(employee == null ? "Mitarbeiter hinzufügen" : "Mitarbeiter bearbeiten");
        dialog.setHeaderText(employee == null ? "Neue Mitarbeiterin / neuer Mitarbeiter" : employee.getName());
        ButtonType save = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField name = new TextField(employee == null ? "" : employee.getName());
        name.setPromptText("Vor- und Nachname");
        ComboBox<String> role = new ComboBox<>(FXCollections.observableArrayList(ROLES));
        role.setValue(employee == null ? "Service" : employee.getRole());
        ComboBox<String> shift = new ComboBox<>(FXCollections.observableArrayList(SHIFTS));
        shift.setValue(employee == null ? "Frühschicht" : employee.getShift());
        TextField start = new TextField(employee == null ? "08:00" : employee.getShiftStart().format(Ui.TIME));
        TextField end = new TextField(employee == null ? "16:00" : employee.getShiftEnd().format(Ui.TIME));
        ComboBox<EmployeeStatus> status = new ComboBox<>(FXCollections.observableArrayList(EmployeeStatus.values()));
        status.setValue(employee == null ? EmployeeStatus.NICHT_ERSCHIENEN : employee.getStatus());

        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(12);
        form.addRow(0, new Label("Name"), name);
        form.addRow(1, new Label("Rolle"), role);
        form.addRow(2, new Label("Schicht"), shift);
        form.addRow(3, new Label("Dienstbeginn"), start);
        form.addRow(4, new Label("Dienstende"), end);
        form.addRow(5, new Label("Status"), status);

        VBox box = new VBox(14, form, error);
        box.setPrefWidth(420);
        dialog.getDialogPane().setContent(box);

        dialog.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, event -> {
            try {
                if (employee == null) {
                    StaffService.add(name.getText(), role.getValue(), shift.getValue(),
                            Ui.parseTime(start.getText()), Ui.parseTime(end.getText()), status.getValue());
                } else {
                    StaffService.update(employee, name.getText(), role.getValue(), shift.getValue(),
                            Ui.parseTime(start.getText()), Ui.parseTime(end.getText()), status.getValue());
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
