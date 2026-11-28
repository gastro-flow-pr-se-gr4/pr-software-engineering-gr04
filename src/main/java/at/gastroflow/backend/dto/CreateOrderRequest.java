package at.gastroflow.backend.dto;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(UUID id, int table, String waiter, int guests, List<OrderItemDTO> items) {
}
