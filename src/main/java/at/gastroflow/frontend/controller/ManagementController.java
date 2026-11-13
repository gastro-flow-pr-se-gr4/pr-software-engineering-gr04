package at.gastroflow.frontend.controller;

import at.gastroflow.backend.model.RestaurantSettings;
import at.gastroflow.backend.service.DemoData;
import at.gastroflow.backend.service.InventoryService;
import at.gastroflow.frontend.management.InventoryView;
import at.gastroflow.frontend.management.CalendarView;
import at.gastroflow.frontend.management.DashboardView;
import at.gastroflow.frontend.management.DayOverviewView;
import at.gastroflow.frontend.management.MenuView;
import at.gastroflow.frontend.management.OrdersView;
import at.gastroflow.frontend.management.SettingsView;
import at.gastroflow.frontend.management.StaffView;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Leitungsansicht mit interner Navigation (Sidebar) und sieben Bereichen. */
public class ManagementController {

    @FXML
    private VBox navBox;

    @FXML
    private StackPane contentPane;

    @FXML
    private Label restaurantLabel;

    // Bereichsname -> Aufbau des Inhalts
    private final Map<String, Supplier<Node>> sections = new LinkedHashMap<>();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();

    // Wird beim Öffnen der Leitungsansicht ausgeführt
    @FXML
    private void initialize() {
        DemoData.load();
        InventoryService.ensureLoaded();

        addSection("▦", "Dashboard", () -> new DashboardView(this::showSection).build());
        addSection("☰", "Speisekarte", () -> new MenuView().build());
        addSection("▣", "Bestand & Lager", () -> new InventoryView().build());
        addSection("◷", "Tagesübersicht", () -> new DayOverviewView().build());
        addSection("▤", "Kalender", () -> new CalendarView().build());
        addSection("≡", "Bestellungen", () -> new OrdersView().build());
        addSection("☺", "Personal", () -> new StaffView().build());
        addSection("⚙", "Einstellungen", () -> new SettingsView(this::updateHeader).build());

        updateHeader();
        showSection("Dashboard");
    }

    private void addSection(String icon, String name, Supplier<Node> builder) {
        sections.put(name, builder);

        Button button = new Button(icon + "   " + name);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> showSection(name));

        navButtons.put(name, button);
        navBox.getChildren().add(button);
    }

    // Zeigt einen Bereich im Inhaltsbereich an
    private void showSection(String name) {
        contentPane.getChildren().setAll(sections.get(name).get());

        navButtons.forEach((section, button) -> {
            button.getStyleClass().remove("nav-active");
            if (section.equals(name)) {
                button.getStyleClass().add("nav-active");
            }
        });
    }

    private void updateHeader() {
        restaurantLabel.setText(RestaurantSettings.get().getName());
    }

    // Zurück zur Anmeldung
    @FXML
    private void handleLogout() throws IOException {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/at/gastroflow/frontend/views/login.fxml"
                )
        );

        Stage stage = (Stage)
                contentPane.getScene().getWindow();

        Scene scene = new Scene(
                loader.load(),
                stage.getScene().getWidth(),
                stage.getScene().getHeight()
        );

        stage.setScene(scene);
        stage.setTitle("GastroFlow – Anmeldung");
    }
}
