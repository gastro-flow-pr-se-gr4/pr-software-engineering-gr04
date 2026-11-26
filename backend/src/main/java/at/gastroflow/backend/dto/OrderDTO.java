package at.gastroflow.backend.dto;

import at.gastroflow.backend.model.Course;
import at.gastroflow.backend.model.OrderStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record OrderDTO(UUID id, int number, int table, String waiter, LocalDateTime createdAt, int guests,
                       OrderStatus status, LocalDateTime paidAt, List<OrderItemDTO> items,
                       BigDecimal total, String note, Course course, boolean kitchenClosed) {

    @JsonIgnore
    public boolean isKitchenOrder() {
        return course != null && course.isKitchen();
    }

    @JsonIgnore
    public boolean canServe() {
        return status == OrderStatus.FERTIG || (!isKitchenOrder() && status == OrderStatus.NEU);
    }

    @JsonIgnore
    public boolean isPaid() {
        return paidAt != null;
    }

    @JsonIgnore
    public boolean isOpen() {
        return status != OrderStatus.ABGESCHLOSSEN && status != OrderStatus.STORNIERT;
    }

    @JsonIgnore
    public boolean isPaymentDue() {
        return !isPaid() && status != OrderStatus.STORNIERT;
    }

    @JsonIgnore
    public boolean isActive() {
        return isOpen() || isPaymentDue();
    }

    @JsonIgnore
    public String getItemsText() {
        return items.stream().map(item -> item.quantity() + "× " + item.productName())
                .collect(Collectors.joining(", "));
    }

    @JsonIgnore
    public long getMinutesSinceCreated(LocalDateTime now) {
        return Math.max(0, Duration.between(createdAt, now).toMinutes());
    }
}
