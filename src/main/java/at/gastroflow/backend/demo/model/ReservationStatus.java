package at.gastroflow.backend.demo.model;

public enum ReservationStatus {

    BESTAETIGT("Bestätigt"),
    ANWESEND("Anwesend"),
    ABGESCHLOSSEN("Abgeschlossen"),
    NO_SHOW("No-Show");

    private final String label;

    ReservationStatus(String label) {
        this.label = label;
    }

    public boolean blocksTable() {
        return this == BESTAETIGT || this == ANWESEND;
    }

    @Override
    public String toString() {
        return label;
    }
}
