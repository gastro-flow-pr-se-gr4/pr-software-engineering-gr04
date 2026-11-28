package at.gastroflow.backend.dto;

import java.math.BigDecimal;

public record OrderItemDTO(String productName, String category, BigDecimal unitPrice, int quantity, String note) {

    public BigDecimal total() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
