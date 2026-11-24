package at.gastroflow.backend.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ProductDTOTest {

    @Test
    public void testAvailableProduct() {
        ProductDTO product = new ProductDTO(
                UUID.randomUUID(),
                "Pizza",
                new BigDecimal("5.00"),
                "Pizza Margherita",
                true
        );

        assertTrue(product.isAvailable());
    }

    @Test
    public void testUnavailableProduct() {
        ProductDTO product = new ProductDTO(
                UUID.randomUUID(),
                "Pizza",
                new BigDecimal("5.00"),
                "Pizza Margherita",
                false
        );

        assertFalse(product.isAvailable());
    }
}
