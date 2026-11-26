package at.gastroflow.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record BillDTO(int table, BigDecimal amountDue, List<OrderDTO> orders) {
}
