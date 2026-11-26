package at.gastroflow.backend.demo.model;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RestaurantSettings {

    private static final RestaurantSettings INSTANCE = new RestaurantSettings();

    private String name = "GastroFlow";
    private List<Integer> tableCapacities = new ArrayList<>(List.of(4, 4, 4, 4, 4, 4, 6, 6, 6, 6, 8, 8));
    private LocalTime openingTime = LocalTime.of(8, 0);
    private LocalTime closingTime = LocalTime.of(23, 0);
    private int delayMinutes = 20;
    private int defaultReservationMinutes = 90;
    private int lowStockPercent = 100;

    private RestaurantSettings() {
    }

    public static RestaurantSettings get() {
        return INSTANCE;
    }

    public void update(String name, List<Integer> tableCapacities,
                       LocalTime openingTime, LocalTime closingTime,
                       int delayMinutes, int defaultReservationMinutes, int lowStockPercent) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Restaurantnamen eingeben.");
        }
        if (tableCapacities == null || tableCapacities.isEmpty()) {
            throw new IllegalArgumentException("Es muss mindestens einen Tisch geben.");
        }
        for (int seats : tableCapacities) {
            if (seats < 1) {
                throw new IllegalArgumentException("Jeder Tisch braucht mindestens einen Platz.");
            }
        }
        if (!openingTime.isBefore(closingTime)) {
            throw new IllegalArgumentException("Die Öffnungszeit muss vor der Schließzeit liegen.");
        }
        if (delayMinutes < 1 || defaultReservationMinutes < 15) {
            throw new IllegalArgumentException("Bitte gültige Minutenwerte eingeben.");
        }
        if (lowStockPercent < 50 || lowStockPercent > 300) {
            throw new IllegalArgumentException("Die Lagerwarnung muss zwischen 50 und 300 % liegen.");
        }

        this.name = name.trim();
        this.tableCapacities = new ArrayList<>(tableCapacities);
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.delayMinutes = delayMinutes;
        this.defaultReservationMinutes = defaultReservationMinutes;
        this.lowStockPercent = lowStockPercent;
    }

    public String getName() { return name; }

    public int getTableCount() { return tableCapacities.size(); }

    public List<Integer> getTableCapacities() { return Collections.unmodifiableList(tableCapacities); }

    public int getCapacity(int table) {
        return table >= 1 && table <= tableCapacities.size() ? tableCapacities.get(table - 1) : 0;
    }

    public int getTotalSeats() {
        return tableCapacities.stream().mapToInt(Integer::intValue).sum();
    }

    public LocalTime getOpeningTime() { return openingTime; }

    public LocalTime getClosingTime() { return closingTime; }

    public int getDelayMinutes() { return delayMinutes; }

    public int getDefaultReservationMinutes() { return defaultReservationMinutes; }

    public int getLowStockPercent() { return lowStockPercent; }
}
