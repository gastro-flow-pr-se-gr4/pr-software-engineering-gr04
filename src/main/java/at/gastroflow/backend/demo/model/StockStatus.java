package at.gastroflow.backend.demo.model;

public enum StockStatus {

    AUSREICHEND("Ausreichend"),
    NIEDRIG("Niedriger Bestand"),
    NICHT_VERFUEGBAR("Nicht verfügbar");

    private final String label;

    StockStatus(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
