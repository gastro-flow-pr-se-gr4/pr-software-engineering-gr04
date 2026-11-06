
package at.gastroflow.model;

public class OrderItem {

    private final Product product;
    private int quantity;
    private String specialRequest = "";

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

    public String getSpecialRequest() {
        return specialRequest;
    }

    public void setSpecialRequest(String specialRequest) {
        this.specialRequest = specialRequest == null
                ? ""
                : specialRequest.trim();
    }

    public double getTotal() {
        return product.price() * quantity;
    }

    @Override
    public String toString() {
        String text = quantity + " × " + product.name();

        if (!specialRequest.isEmpty()) {
            text += " (" + specialRequest + ")";
        }

        return text + String.format(" – %.2f €", getTotal());
    }
}
