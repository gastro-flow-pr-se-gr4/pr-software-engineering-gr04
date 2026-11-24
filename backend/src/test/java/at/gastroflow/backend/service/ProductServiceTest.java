package at.gastroflow.backend.service;

import at.gastroflow.backend.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProductServiceTest {

    @Test
    public void testNegaticPrice(){
        ProductService productService = new ProductService();

        Product product = new Product(
                "Pizza",
                new BigDecimal("-5.00"),
                        "Pizza Magherita",
                        true
        );

        assertThrows(IllegalArgumentException.class, () -> {
            productService.saveProduct(product);
        });
    }

    @Test
    public void testEmptyName() {
        ProductService productService = new ProductService();

        Product product = new Product(
                "",
                new BigDecimal("5.00"),
                "Pizza Margherita",
                true
        );

        assertThrows(IllegalArgumentException.class, () -> {
            productService.saveProduct(product);
        });
    }

    @Test
    public void testNullPrice() {
        ProductService productService = new ProductService();

        Product product = new Product(
                "Pizza",
                null,
                "Pizza Margherita",
                true
        );

        assertThrows(IllegalArgumentException.class, () -> {
            productService.saveProduct(product);
        });
    }

    @Test
    public void testNullProduct() {
        ProductService productService = new ProductService();

        assertThrows(IllegalArgumentException.class, () -> {
            productService.saveProduct(null);
        });
    }

    @Test
    public void testNullName() {
        ProductService productService = new ProductService();

        Product product = new Product(
                null,
                new BigDecimal("5.00"),
                "Pizza Margherita",
                true
        );

        assertThrows(IllegalArgumentException.class, () -> {
            productService.saveProduct(product);
        });
    }

    @Test
    public void testUpdateNegativePrice() {
        ProductService productService = new ProductService();

        Product product = new Product(
                "Pizza",
                new BigDecimal("-5.00"),
                "Pizza Margherita",
                true
        );

        assertThrows(IllegalArgumentException.class, () -> {
            productService.updateProduct(product);
        });
    }
}
