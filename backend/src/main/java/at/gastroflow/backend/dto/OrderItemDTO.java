package at.gastroflow.backend.dto;

import java.math.BigDecimal;

/** Eine Bestellposition, wie sie über die REST-API verschickt wird. */
public record OrderItemDTO(String productName, String category, BigDecimal unitPrice, int quantity, String note) {

    public BigDecimal total() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
