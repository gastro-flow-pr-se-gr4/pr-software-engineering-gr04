package at.gastroflow.backend.service;

import at.gastroflow.backend.dao.OrderDAO;
import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Ersatz für die Datenbank in Tests: hält Bestellungen nur im Speicher. */
public class InMemoryOrderDAO extends OrderDAO {

    private final List<Order> orders = new ArrayList<>();
    private int nextNumber = 10001;
    public int saveCalls = 0;

    @Override
    public void createTables() {
    }

    @Override
    public List<Order> findAll() {
        return sorted(orders);
    }

    @Override
    public List<Order> findByTable(int table) {
        return sorted(orders.stream().filter(o -> o.getTableNumber() == table).toList());
    }

    @Override
    public List<Order> findActive() {
        return sorted(orders.stream().filter(Order::isActive).toList());
    }

    @Override
    public List<Order> findSince(LocalDateTime from) {
        return sorted(orders.stream().filter(o -> !o.getCreatedAt().isBefore(from)).toList());
    }

    @Override
    public Order findById(UUID id) {
        return orders.stream().filter(o -> o.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public void save(Order order) {
        saveAll(List.of(order));
    }

    @Override
    public void saveAll(List<Order> newOrders) {
        saveCalls++;
        for (Order order : newOrders) {
            order.setNumber(nextNumber++);
            orders.add(order);
        }
    }

    @Override
    public boolean existsSubmission(UUID submissionId) {
        return orders.stream().anyMatch(o -> submissionId.equals(o.getSubmissionId()));
    }

    @Override
    public void closeKitchen(List<UUID> ids) {
        ids.forEach(id -> findById(id).setKitchenClosed(true));
    }

    @Override
    public void updateStatus(UUID id, OrderStatus status) {
        findById(id).setStatus(status);
    }

    @Override
    public void markPaid(List<UUID> ids, LocalDateTime paidAt) {
        ids.forEach(id -> {
            Order order = findById(id);
            if (order.getPaidAt() == null) {
                order.setPaidAt(paidAt);
            }
        });
    }

    private List<Order> sorted(List<Order> list) {
        return list.stream().sorted(Comparator.comparing(Order::getCreatedAt).thenComparing(Order::getNumber)).toList();
    }
}
