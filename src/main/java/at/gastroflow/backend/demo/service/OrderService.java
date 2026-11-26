package at.gastroflow.backend.demo.service;

import at.gastroflow.backend.demo.model.Order;
import at.gastroflow.backend.demo.model.OrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrderService {
    private static final List<Order> SENT_ORDERS = new ArrayList<>();
    private static int nextNumber = 1000;

    public static void send(Order order) {
        addExisting(order);
        InventoryService.bookOrder(order);
    }

    public static void addExisting(Order order) {
        order.setNumber(nextNumber++);
        SENT_ORDERS.add(order);
    }

    public static List<Order> getSentOrders() {
        return Collections.unmodifiableList(SENT_ORDERS);
    }

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

    public static boolean cancel(Order order) {
        if (!order.isOpen()) {
            throw new IllegalStateException("Nur offene Bestellungen können storniert werden.");
        }
        boolean alreadyPrepared = order.getStatus() != OrderStatus.NEU;
        order.setStatus(OrderStatus.STORNIERT);
        return InventoryService.reverseOrder(order, alreadyPrepared);
    }
}
