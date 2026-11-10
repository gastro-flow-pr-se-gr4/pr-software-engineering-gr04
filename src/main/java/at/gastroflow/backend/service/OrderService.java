package at.gastroflow.backend.service;

import at.gastroflow.backend.model.Order;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Temporäre Speicherung */
public class OrderService {
    private static final List<Order> SENT_ORDERS = new ArrayList<>();

    public static void send(Order order) {
        SENT_ORDERS.add(order);
    }

    public static List<Order> getSentOrders() {
        return Collections.unmodifiableList(SENT_ORDERS);
    }
}

