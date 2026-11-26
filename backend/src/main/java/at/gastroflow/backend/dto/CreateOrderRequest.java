package at.gastroflow.backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * Anfrage zum Anlegen einer Bestellung (POST /api/orders).
 * Die id erzeugt das Frontend einmal pro Entwurf. Wird dieselbe Bestellung
 * versehentlich zweimal geschickt, lehnt das Backend die zweite ab.
 */
public record CreateOrderRequest(UUID id, int table, String waiter, int guests, List<OrderItemDTO> items) {
}
