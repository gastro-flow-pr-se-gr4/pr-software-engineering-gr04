package at.gastroflow.backend.mapper;

import at.gastroflow.backend.dto.OrderDTO;
import at.gastroflow.backend.dto.OrderItemDTO;
import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderItem;

public class OrderMapper {

    public OrderDTO toDTO(Order order) {
        return new OrderDTO(
                order.getId(),
                order.getNumber(),
                order.getTableNumber(),
                order.getWaiter(),
                order.getCreatedAt(),
                order.getGuests(),
                order.getStatus(),
                order.getPaidAt(),
                order.getItems().stream().map(this::toDTO).toList(),
                order.getTotal(),
                order.getNoteText(),
                order.getCourse(),
                order.isKitchenClosed()
        );
    }

    public OrderItemDTO toDTO(OrderItem item) {
        return new OrderItemDTO(item.getProductName(), item.getCategory(), item.getUnitPrice(),
                item.getQuantity(), item.getNote());
    }
}
