package at.gastroflow.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/** Rechnung eines Tisches: offener Betrag und alle Bestellungen, die dort noch laufen. */
public record BillDTO(int table, BigDecimal amountDue, List<OrderDTO> orders) {
}
