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

/** Eine Bestellung, wie sie über die REST-API verschickt wird. */
public record OrderDTO(UUID id, int number, int table, String waiter, LocalDateTime createdAt, int guests,
                       OrderStatus status, LocalDateTime paidAt, List<OrderItemDTO> items,
                       BigDecimal total, String note, Course course, boolean kitchenClosed) {

    /** Muss die Küche diese Bestellung zubereiten? Getränke bringt der Service selbst. */
    @JsonIgnore
    public boolean isKitchenOrder() {
        return course != null && course.isKitchen();
    }

    /** Kann der Service sie jetzt als serviert markieren? (fertig aus der Küche oder ein Getränk) */
    @JsonIgnore
    public boolean canServe() {
        return status == OrderStatus.FERTIG || (!isKitchenOrder() && status == OrderStatus.NEU);
    }

    @JsonIgnore
    public boolean isPaid() {
        return paidAt != null;
    }

    /** Noch nicht serviert und nicht storniert. */
    @JsonIgnore
    public boolean isOpen() {
        return status != OrderStatus.ABGESCHLOSSEN && status != OrderStatus.STORNIERT;
    }

    @JsonIgnore
    public boolean isPaymentDue() {
        return !isPaid() && status != OrderStatus.STORNIERT;
    }

    /** Läuft noch: nicht serviert oder nicht bezahlt. */
    @JsonIgnore
    public boolean isActive() {
        return isOpen() || isPaymentDue();
    }

    /** Z. B. "2× Classic Burger, 1× Coca-Cola 0,33 l" */
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
