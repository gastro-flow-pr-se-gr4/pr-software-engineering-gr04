
package at.gastroflow.model;

import java.util.ArrayList;
import java.util.List;

public class Order {

    private final int tableNumber;
    private final List<OrderItem> items = new ArrayList<>();
    private String note = "";

    public Order(int tableNumber) {
        this.tableNumber = tableNumber;
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note == null ? "" : note;
    }

    public void addProduct(Product product) {

        // Nur Positionen ohne Sonderwunsch zusammenfassen
        for (OrderItem item : items) {

            if (item.getProduct().equals(product)
                    && item.getSpecialRequest().isEmpty()) {

                item.setQuantity(item.getQuantity() + 1);
                return;
            }
        }

        // Ansonsten neue Position anlegen
        items.add(new OrderItem(product));
    }

    public double getTotal() {
        return items.stream()
                .mapToDouble(OrderItem::getTotal)
                .sum();
    }
}
