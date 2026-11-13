package at.gastroflow.backend.model;

import java.time.LocalDateTime;

/** Eine Zutat im Lager. Bestand und Mindestbestand stehen in der Basiseinheit (Stück, g, ml). */
public class Ingredient {

    private final int id;
    private String name;
    private String category;
    private Unit unit;
    private long stock;
    private long minStock;
    private boolean archived;
    private LocalDateTime lastUpdated;

    public Ingredient(int id, String name, String category, Unit unit, long stock,
                      long minStock, boolean archived, LocalDateTime lastUpdated) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.stock = stock;
        this.minStock = minStock;
        this.archived = archived;
        this.lastUpdated = lastUpdated;
    }

    public int getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    /** Einheit für die Anzeige (z. B. Kilogramm). */
    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }

    public long getStock() { return stock; }
    public void setStock(long stock) { this.stock = stock; }

    public long getMinStock() { return minStock; }
    public void setMinStock(long minStock) { this.minStock = minStock; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    @Override
    public String toString() {
        return name;
    }
}
