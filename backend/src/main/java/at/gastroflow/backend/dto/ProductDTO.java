package at.gastroflow.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class ProductDTO {
    private UUID id;
    private String name;
    private BigDecimal price;
    private String description;

    public ProductDTO(UUID id, String name, BigDecimal price, String description) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.description = description;
    }

    public UUID getId() {
        return id;
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
}
