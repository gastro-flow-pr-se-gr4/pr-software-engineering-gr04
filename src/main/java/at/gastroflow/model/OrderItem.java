package at.gastroflow.model;

public class OrderItem {
    private final Product product;
    private int quantity;

    public OrderItem(Product product) {
        this.product = product;
        this.quantity = 1;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getTotal() {
        return product.price() * quantity;
    }

    @Override
    public String toString() {
        return quantity + " × " + product.name() + String.format(" – %.2f €", getTotal());
    }
}

