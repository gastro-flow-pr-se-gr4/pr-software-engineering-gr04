package at.gastroflow.backend.demo.model;

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
