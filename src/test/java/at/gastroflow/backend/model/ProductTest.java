package at.gastroflow.backend.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class ProductTest {

    @Test
    public void testUnavailableProduct() {
        Product product = new Product(
                UUID.randomUUID(),
                "Pizza",
                new BigDecimal("5.00"),
                "Pizza Margherita",
                false
        );

        assertFalse(product.isAvailable());
    }
}
