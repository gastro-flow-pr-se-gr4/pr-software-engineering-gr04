package at.gastroflow.model;

public record Product(String name, double price, String category) {
    @Override
    public String toString() {
        return name + String.format(" (%.2f €)", price); }
}
