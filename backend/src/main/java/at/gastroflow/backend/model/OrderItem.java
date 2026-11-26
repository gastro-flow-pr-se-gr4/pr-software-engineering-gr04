package at.gastroflow.backend.model;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderItem extends BaseEntity {

    private final String productName;
    private final String category;
    private final BigDecimal unitPrice;
    private final int quantity;
    private final String note;

    public OrderItem(String productName, String category, BigDecimal unitPrice, int quantity, String note) {
        super();
        this.productName = productName;
        this.category = category == null ? "" : category;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.note = note == null ? "" : note;
    }

    public OrderItem(UUID id, String productName, String category, BigDecimal unitPrice, int quantity, String note) {
        super(id);
        this.productName = productName;
        this.category = category == null ? "" : category;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.note = note == null ? "" : note;
    }

    public String getProductName() {
        return productName;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getNote() {
        return note;
    }

    public BigDecimal getTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
