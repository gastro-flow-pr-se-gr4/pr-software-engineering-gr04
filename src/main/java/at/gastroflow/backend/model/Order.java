package at.gastroflow.backend.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Eine Bestellung an einem Tisch. */
public class Order {

    private int number;
    private final int table;
    private final String waiter;
    private final LocalDateTime createdAt;
    private final int guests;
    private final List<OrderItem> items;
    private OrderStatus status;
    private String note = "";

    public Order(int table, String waiter, LocalDateTime createdAt, int guests,
                 List<OrderItem> items, OrderStatus status) {
        this.table = table;
        this.waiter = waiter;
        this.createdAt = createdAt;
        this.guests = guests;
        this.items = new ArrayList<>(items);
        this.status = status;
    }

    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public int getTable() { return table; }

    public String getWaiter() { return waiter; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public int getGuests() { return guests; }

    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }

    /** Sonderwunsch zur Bestellung (leer, wenn keiner). */
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note == null ? "" : note.trim(); }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    /** Noch nicht abgeschlossen oder storniert (neu, in Zubereitung oder fertig). */
    public boolean isOpen() {
        return status != OrderStatus.ABGESCHLOSSEN && status != OrderStatus.STORNIERT;
    }

    public double getTotal() {
        return items.stream().mapToDouble(OrderItem::getTotal).sum();
    }

    public long getMinutesSinceCreated(LocalDateTime now) {
        return Math.max(0, Duration.between(createdAt, now).toMinutes());
    }

    /** Z. B. "2× Classic Burger, 1× Coca-Cola 0,33 l" */
    public String getItemsText() {
        return items.stream()
                .map(item -> item.getQuantity() + "× " + item.getProductName())
                .collect(Collectors.joining(", "));
    }
}
