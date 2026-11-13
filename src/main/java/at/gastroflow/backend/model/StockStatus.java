package at.gastroflow.backend.model;

/** Lagerstatus einer Zutat (Grün / Orange / Rot). */
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
