package at.gastroflow.backend.model;

/** Art einer Lagerbuchung. */
public enum MovementType {

    ANFANGSBESTAND("Anfangsbestand"),
    WARENEINGANG("Wareneingang"),
    KORREKTUR("Korrektur"),
    BESTELLUNG("Abzug durch Bestellung"),
    RUECKBUCHUNG("Rückbuchung");

    private final String label;

    MovementType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
