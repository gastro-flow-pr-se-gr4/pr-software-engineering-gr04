package at.gastroflow.frontend.model;

import at.gastroflow.backend.demo.model.Product;
import at.gastroflow.backend.dto.CreateOrderRequest;
import at.gastroflow.backend.dto.OrderItemDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrderDraft {

    public static final int MAX_QUANTITY = 99;

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
            this.note = note == null ? "" : note.trim().replaceAll("\\s*\\R\\s*", ", ");
        }

        public double getTotal() {
            return roundToCents(product.getPrice() * quantity);
        }
    }

    private final int table;
    private final UUID id = UUID.randomUUID();
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

    public double getTotal() {
        return roundToCents(lines.stream().mapToDouble(Line::getTotal).sum());
    }

    public int getItemCount() {
        return lines.stream().mapToInt(Line::getQuantity).sum();
    }

    public String getNoteText() {
        return lines.stream()
                .filter(line -> !line.note.isEmpty())
                .map(line -> line.quantity + "× " + line.product.getName() + ": " + line.note)
                .collect(Collectors.joining("\n"));
    }

    public UUID getId() { return id; }

    public CreateOrderRequest toRequest(String waiter) {
        List<OrderItemDTO> items = lines.stream()
                .map(line -> new OrderItemDTO(line.product.getName(), line.product.getCategory(),
                        BigDecimal.valueOf(line.product.getPrice()), line.quantity, line.note))
                .toList();
        return new CreateOrderRequest(id, table, waiter, 1, items);
    }

    private static double roundToCents(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
