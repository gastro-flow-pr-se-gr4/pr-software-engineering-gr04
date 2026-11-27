package at.gastroflow.backend.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Order extends BaseEntity {

    private int number;
    private final int tableNumber;
    private final String waiter;
    private final LocalDateTime createdAt;
    private final int guests;
    private final List<OrderItem> items;
    private OrderStatus status;
    private LocalDateTime paidAt;
    private Course course = Course.HAUPTSPEISE;
    private boolean kitchenClosed = false;
    private UUID submissionId;

    public Order(UUID id, int tableNumber, String waiter, LocalDateTime createdAt, int guests, List<OrderItem> items) {
        this(id, 0, tableNumber, waiter, createdAt, guests, items, OrderStatus.NEU, null);
    }

    public Order(UUID id, int number, int tableNumber, String waiter, LocalDateTime createdAt, int guests,
                 List<OrderItem> items, OrderStatus status, LocalDateTime paidAt) {
        super(id == null ? UUID.randomUUID() : id);
        this.number = number;
        this.tableNumber = tableNumber;
        this.waiter = waiter;
        this.createdAt = createdAt;
        this.guests = guests;
        this.items = new ArrayList<>(items);
        this.status = status;
        this.paidAt = paidAt;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public String getWaiter() {
        return waiter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public int getGuests() {
        return guests;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course == null ? Course.HAUPTSPEISE : course;
    }

    public boolean isKitchenOrder() {
        return course.isKitchen();
    }

    public boolean isKitchenClosed() {
        return kitchenClosed;
    }

    public void setKitchenClosed(boolean kitchenClosed) {
        this.kitchenClosed = kitchenClosed;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(UUID submissionId) {
        this.submissionId = submissionId;
    }

    public boolean isPaid() {
        return paidAt != null;
    }

    public boolean isOpen() {
        return status != OrderStatus.ABGESCHLOSSEN && status != OrderStatus.STORNIERT;
    }

    public boolean isPaymentDue() {
        return !isPaid() && status != OrderStatus.STORNIERT;
    }

    public boolean isActive() {
        return isOpen() || isPaymentDue();
    }

    public BigDecimal getTotal() {
        return items.stream().map(OrderItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getNoteText() {
        return items.stream()
                .filter(item -> !item.getNote().isBlank())
                .map(item -> item.getQuantity() + "× " + item.getProductName() + ": " + item.getNote())
                .collect(Collectors.joining("\n"));
    }
}
