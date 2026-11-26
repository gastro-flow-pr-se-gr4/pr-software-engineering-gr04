package at.gastroflow.backend.model;

public enum Course {

    GETRAENK("Getränk"),
    VORSPEISE("Vorspeise"),
    HAUPTSPEISE("Hauptspeise"),
    NACHSPEISE("Nachspeise");

    private final String label;

    Course(String label) {
        this.label = label;
    }

    public boolean isKitchen() {
        return this != GETRAENK;
    }

    @Override
    public String toString() {
        return label;
    }
}
