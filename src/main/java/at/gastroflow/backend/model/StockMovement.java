package at.gastroflow.backend.model;

import java.time.LocalDateTime;

/**
 * Eine Lagerbuchung (Lagerhistorie).
 * delta ist die Änderung in der Basiseinheit: positiv = Zugang, negativ = Abgang.
 */
public record StockMovement(LocalDateTime time, int ingredientId, String ingredientName,
                            long delta, Unit unit, MovementType type, String reason) {
}
