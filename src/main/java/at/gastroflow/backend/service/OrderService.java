package at.gastroflow.backend.service;

import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Temporäre Speicherung aller Bestellungen (gemeinsam für Service, Küche und Leitung). */
public class OrderService {
    private static final List<Order> SENT_ORDERS = new ArrayList<>();
    private static int nextNumber = 1000;

    /** Neue Bestellung: wird gespeichert und automatisch vom Lager abgebucht (laut Rezeptur). */
    public static void send(Order order) {
        addExisting(order);
        InventoryService.bookOrder(order);
    }

    /** Übernimmt eine bereits vorhandene Bestellung ohne Lagerbuchung (z. B. Verlauf früherer Tage). */
    public static void addExisting(Order order) {
        order.setNumber(nextNumber++);
        SENT_ORDERS.add(order);
    }

    public static List<Order> getSentOrders() {
        return Collections.unmodifiableList(SENT_ORDERS);
    }

    /** Bestellungen von from (inklusive) bis to (exklusive). */
    public static List<Order> getOrdersBetween(LocalDateTime from, LocalDateTime to) {
        List<Order> result = new ArrayList<>();
        for (Order order : SENT_ORDERS) {
            LocalDateTime time = order.getCreatedAt();
            if (!time.isBefore(from) && time.isBefore(to)) {
                result.add(order);
            }
        }
        return result;
    }

    public static List<Order> getOrdersOn(LocalDate date) {
        return getOrdersBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    public static List<Order> getOpenOrders() {
        return SENT_ORDERS.stream().filter(Order::isOpen).toList();
    }

    public static void changeStatus(Order order, OrderStatus status) {
        order.setStatus(status);
    }

    /**
     * Storniert eine offene Bestellung. Ist sie noch neu (nicht zubereitet),
     * werden die Zutaten zurückgebucht. Gibt true zurück, wenn zurückgebucht wurde.
     */
    public static boolean cancel(Order order) {
        if (!order.isOpen()) {
            throw new IllegalStateException("Nur offene Bestellungen können storniert werden.");
        }
        boolean alreadyPrepared = order.getStatus() != OrderStatus.NEU;
        order.setStatus(OrderStatus.STORNIERT);
        return InventoryService.reverseOrder(order, alreadyPrepared);
    }
}
