package at.gastroflow.backend.demo.model;

public class OrderItem {

    private final String productName;
    private final String category;
    private final double unitPrice;
    private final int quantity;

    public OrderItem(Product product, int quantity) {
        this(product.getName(), product.getCategory(), product.getPrice(), quantity);
    }

    public OrderItem(String productName, String category, double unitPrice, int quantity) {
        this.productName = productName;
        this.category = category;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public String getProductName() { return productName; }

    public String getCategory() { return category; }

    public double getUnitPrice() { return unitPrice; }

    public int getQuantity() { return quantity; }

    public double getTotal() {
        return unitPrice * quantity;
    }
}
