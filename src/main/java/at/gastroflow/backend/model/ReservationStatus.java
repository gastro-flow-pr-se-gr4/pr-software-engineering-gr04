package at.gastroflow.backend.model;

/** Gespeicherter Status einer Reservierung. */
public enum ReservationStatus {

    BESTAETIGT("Bestätigt"),
    ANWESEND("Anwesend"),
    ABGESCHLOSSEN("Abgeschlossen"),
    NO_SHOW("No-Show");

    private final String label;

    ReservationStatus(String label) {
        this.label = label;
    }

    /** Nur bestätigte und anwesende Reservierungen belegen einen Tisch. */
    public boolean blocksTable() {
        return this == BESTAETIGT || this == ANWESEND;
    }

    @Override
    public String toString() {
        return label;
    }
}
