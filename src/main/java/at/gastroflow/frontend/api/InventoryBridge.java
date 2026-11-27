package at.gastroflow.frontend.api;

import at.gastroflow.backend.dto.OrderDTO;

import java.util.List;
import java.util.Optional;

public final class InventoryBridge {

    private InventoryBridge() {
    }

    public static boolean book(OrderDTO order) {
        return InventoryService.bookOrder(toInventoryOrder(order));
    }

    public static boolean reverse(OrderDTO order, boolean alreadyPrepared) {
        return InventoryService.reverseOrder(toInventoryOrder(order), alreadyPrepared);
    }

    public static Optional<String> problemFor(OrderDTO order) {
        return InventoryService.problemFor(toInventoryOrder(order));
    }

    public static List<String> scarceIngredients(OrderDTO order) {
        return InventoryService.scarceIngredientsFor(toInventoryOrder(order)).stream()
                .map(ingredient -> ingredient.getName()).toList();
    }

    private static Order toInventoryOrder(OrderDTO dto) {
        List<OrderItem> items = dto.items().stream()
                .map(item -> new OrderItem(item.productName(), item.category(), item.unitPrice().doubleValue(), item.quantity()))
                .toList();
        Order order = new Order(dto.table(), dto.waiter(), dto.createdAt(), dto.guests(), items, OrderStatus.NEU);
        order.setNumber(dto.number());
        return order;
    }
}
