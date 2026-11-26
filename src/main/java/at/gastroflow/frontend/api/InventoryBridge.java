package at.gastroflow.frontend.api;

import at.gastroflow.backend.demo.model.Order;
import at.gastroflow.backend.demo.model.OrderItem;
import at.gastroflow.backend.demo.model.OrderStatus;
import at.gastroflow.backend.demo.service.InventoryService;
import at.gastroflow.backend.dto.OrderDTO;

import java.util.List;
import java.util.Optional;

/**
 * Das Lager liegt noch in der Demo (bis es ein echtes Backend dafür gibt).
 * Diese Brücke übersetzt eine Bestellung aus der REST-API in das Format,
 * das die Lagerverwaltung kennt – am Demo-Code selbst wird nichts geändert.
 * Die Bestellnummer ist der Schlüssel, deshalb passt die Buchung immer zur richtigen Bestellung.
 */
public final class InventoryBridge {

    private InventoryBridge() {
    }

    /** Zieht die Zutaten laut Rezeptur vom Lager ab (genau einmal pro Bestellnummer). */
    public static boolean book(OrderDTO order) {
        return InventoryService.bookOrder(toInventoryOrder(order));
    }

    /** Storno: bucht zurück, wenn die Zutaten noch nicht verbraucht wurden. */
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
