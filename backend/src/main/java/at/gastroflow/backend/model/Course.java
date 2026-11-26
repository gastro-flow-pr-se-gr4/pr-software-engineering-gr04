package at.gastroflow.backend.model;

/** Gang einer Bestellung. Getränke gehen nicht in die Küche, die anderen Gänge werden nacheinander gekocht. */
public enum Course {

    GETRAENK("Getränk"),
    VORSPEISE("Vorspeise"),
    HAUPTSPEISE("Hauptspeise"),
    NACHSPEISE("Nachspeise");

    private final String label;

    Course(String label) {
        this.label = label;
    }

    /** Muss die Küche diesen Gang zubereiten? */
    public boolean isKitchen() {
        return this != GETRAENK;
    }

    @Override
    public String toString() {
        return label;
    }
}
