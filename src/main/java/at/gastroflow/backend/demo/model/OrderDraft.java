package at.gastroflow.backend.demo.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Eine Bestellung, die der Service gerade zusammenstellt (noch nicht an die Küche gesendet). */
public class OrderDraft {

    public static final int MAX_QUANTITY = 99;

    /** Eine Position: Produkt, Menge und Sonderwunsch. */
    public static class Line {

        private final Product product;
        private int quantity = 1;
        private String note = "";

        private Line(Product product) {
            this.product = product;
        }

        public Product getProduct() { return product; }

        public int getQuantity() { return quantity; }

        public String getNote() { return note; }

        public void setNote(String note) {
            this.note = note == null ? "" : note.trim().replaceAll("\\s*\\R\\s*", ", "); // immer einzeilig
        }

        public double getTotal() {
            return roundToCents(product.getPrice() * quantity);
        }
    }

    private final int table;
    private final List<Line> lines = new ArrayList<>();
    private boolean sent = false;

    public OrderDraft(int table) {
        this.table = table;
    }

    public int getTable() { return table; }

    public List<Line> getLines() { return Collections.unmodifiableList(lines); }

    public boolean isEmpty() { return lines.isEmpty(); }

    public boolean isSent() { return sent; }
    public void markSent() { sent = true; }

    /**
     * Fügt ein Produkt hinzu. Gibt es schon eine Position ohne Sonderwunsch, wird deren Menge erhöht.
     * Positionen mit Sonderwunsch bleiben getrennt, sonst würde der Wunsch für alle Stück gelten.
     */
    public Line add(Product product) {
        for (Line line : lines) {
            if (line.product == product && line.note.isEmpty()) {
                increase(line);
                return line;
            }
        }
        Line line = new Line(product);
        lines.add(line);
        return line;
    }

    public void increase(Line line) {
        if (line.quantity < MAX_QUANTITY) {
            line.quantity++;
        }
    }

    /** Verringert die Menge, aber nie unter 1. Zum Entfernen gibt es remove(). */
    public void decrease(Line line) {
        if (line.quantity > 1) {
            line.quantity--;
        }
    }

    public void remove(Line line) {
        lines.remove(line);
    }

    public void clear() {
        lines.clear();
    }

    /** Gesamtpreis aller Positionen in Euro. */
    public double getTotal() {
        return roundToCents(lines.stream().mapToDouble(Line::getTotal).sum());
    }

    /** Anzahl aller Artikel (2× Burger und 1× Cola = 3). */
    public int getItemCount() {
        return lines.stream().mapToInt(Line::getQuantity).sum();
    }

    /** Alle Sonderwünsche für die Küche, einer pro Zeile, z. B. "1× Classic Burger: ohne Zwiebeln". */
    public String getNoteText() {
        return lines.stream()
                .filter(line -> !line.note.isEmpty())
                .map(line -> line.quantity + "× " + line.product.getName() + ": " + line.note)
                .collect(Collectors.joining("\n"));
    }

    public List<OrderItem> toItems() {
        return lines.stream().map(line -> new OrderItem(line.product, line.quantity)).toList();
    }

    private static double roundToCents(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
