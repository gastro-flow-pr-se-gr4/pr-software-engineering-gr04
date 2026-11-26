package at.gastroflow.backend.demo.model;

import java.time.LocalDateTime;

public record StockMovement(LocalDateTime time, int ingredientId, String ingredientName,
                            long delta, Unit unit, MovementType type, String reason) {
}
