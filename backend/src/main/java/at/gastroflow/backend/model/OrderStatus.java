package at.gastroflow.backend.model;

/** Status einer Bestellung von der Aufnahme bis zum Abschluss. */
public enum OrderStatus {

    NEU("Neu"),
    IN_ZUBEREITUNG("In Zubereitung"),
    FERTIG("Fertig"),
    ABGESCHLOSSEN("Abgeschlossen"),
    STORNIERT("Storniert");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    /** Erlaubter nächster Schritt: Neu → In Zubereitung → Fertig → Abgeschlossen (serviert). */
    public OrderStatus next() {
        return switch (this) {
            case NEU -> IN_ZUBEREITUNG;
            case IN_ZUBEREITUNG -> FERTIG;
            case FERTIG -> ABGESCHLOSSEN;
            default -> null;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
