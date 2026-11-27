package at.gastroflow.backend.model;

import java.math.BigDecimal;
import java.util.UUID;

public class Product extends BaseEntity {
    private String name;
    private BigDecimal price;
    private String description;
    private boolean available;

    //Produkt erstellen
    public Product(String name, BigDecimal price, String description, boolean available) {
        super();
        this.name = name;
        this.price = price;
        this.description = description;
        this.available = available;
    }

    //Produkt aus Datenbanl laden
    public Product(UUID id, String name, BigDecimal price, String description, boolean available) {
        super(id);
        this.name = name;
        this.price = price;
        this.description = description;
        this.available = available;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAvailable() {
        return available;
    }
}
