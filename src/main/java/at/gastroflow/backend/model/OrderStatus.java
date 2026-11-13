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

    @Override
    public String toString() {
        return label;
    }
}
