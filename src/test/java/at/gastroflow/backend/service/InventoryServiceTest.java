package at.gastroflow.backend.service;

import at.gastroflow.backend.model.Ingredient;
import at.gastroflow.backend.model.MovementType;
import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderItem;
import at.gastroflow.backend.model.OrderStatus;
import at.gastroflow.backend.model.Product;
import at.gastroflow.backend.model.StockStatus;
import at.gastroflow.backend.model.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests für Lagerbuchungen, Portionen, Status und Speicherung. */
class InventoryServiceTest {

    @TempDir
    Path tempDir;

    private Path file;

    @BeforeEach
    void setUp() {
        file = tempDir.resolve("lager.txt");
        InventoryService.init(file, false); // frische Beispieldaten, synchron speichern
    }

    private Ingredient ingredient(String name) {
        return InventoryService.getIngredients(true).stream()
                .filter(i -> i.getName().equals(name)).findFirst().orElseThrow();
    }

    private Order order(String product, int quantity, OrderStatus status) {
        Product p = MenuService.findByName(product);
        return new Order(1, "Test", LocalDateTime.now(), 1, List.of(new OrderItem(p, quantity)), status);
    }

    @Test
    void orderDeductsRecipeIngredients() {
        // Beispiel aus der Anforderung: 3 × Classic Burger
        OrderService.send(order("Classic Burger", 3, OrderStatus.NEU));

        assertEquals(12 - 3, ingredient("Burgerbrötchen").getStock());
        assertEquals(8000 - 540, ingredient("Rindfleisch").getStock());
        assertEquals(5000 - 90, ingredient("Käse").getStock());
        assertEquals(2000 - 150, ingredient("Tomaten").getStock());
        assertEquals(4000 - 60, ingredient("Salat").getStock());
    }

    @Test
    void orderIsBookedOnlyOnce() {
        Order order = order("Classic Burger", 2, OrderStatus.NEU);
        OrderService.send(order);
        InventoryService.bookOrder(order);
        InventoryService.bookOrder(order);

        assertEquals(10, ingredient("Burgerbrötchen").getStock());
    }

    @Test
    void cancelBeforePreparationRestoresStock() {
        Order order = order("Classic Burger", 2, OrderStatus.NEU);
        OrderService.send(order);

        assertTrue(OrderService.cancel(order));
        assertEquals(12, ingredient("Burgerbrötchen").getStock());
        assertEquals(8000, ingredient("Rindfleisch").getStock());
        assertTrue(InventoryService.getMovements().stream()
                .anyMatch(m -> m.type() == MovementType.RUECKBUCHUNG));
    }

    @Test
    void cancelAfterPreparationDoesNotRestore() {
        Order order = order("Classic Burger", 2, OrderStatus.NEU);
        OrderService.send(order);
        OrderService.changeStatus(order, OrderStatus.IN_ZUBEREITUNG);

        assertFalse(OrderService.cancel(order));
        assertEquals(10, ingredient("Burgerbrötchen").getStock());
    }

    @Test
    void cancelTwiceRestoresOnlyOnce() {
        Order order = order("Classic Burger", 1, OrderStatus.NEU);
        OrderService.send(order);
        OrderService.cancel(order);

        assertFalse(InventoryService.reverseOrder(order, false));
        assertEquals(12, ingredient("Burgerbrötchen").getStock());
    }

    @Test
    void insufficientStockBooksNothingAndNeverGoesNegative() {
        // 13 Burger, aber nur 12 Brötchen: es darf GAR NICHTS abgebucht werden
        Order order = order("Classic Burger", 13, OrderStatus.NEU);
        OrderService.send(order);

        assertEquals(12, ingredient("Burgerbrötchen").getStock());
        assertEquals(8000, ingredient("Rindfleisch").getStock());
        assertTrue(InventoryService.problemFor(order).isPresent());
        assertFalse(InventoryService.isBooked(order));
    }

    @Test
    void portionsComeFromScarcestIngredient() {
        // 12 Brötchen → höchstens 12 Classic Burger, obwohl Fleisch für 44 reichen würde
        assertEquals(12, InventoryService.portionsFor(MenuService.findByName("Classic Burger")));

        InventoryService.correct(ingredient("Burgerbrötchen"), 0, "Test");
        assertEquals(0, InventoryService.portionsFor(MenuService.findByName("Classic Burger")));
    }

    @Test
    void statusFollowsMinimumStock() {
        Ingredient broetchen = ingredient("Burgerbrötchen"); // 12 Stk., Minimum 15
        assertEquals(StockStatus.NIEDRIG, InventoryService.statusOf(broetchen));

        InventoryService.receive(broetchen, 20, "Lieferung");
        assertEquals(StockStatus.AUSREICHEND, InventoryService.statusOf(broetchen));

        InventoryService.correct(broetchen, 0, "Verdorben");
        assertEquals(StockStatus.NICHT_VERFUEGBAR, InventoryService.statusOf(broetchen));
    }

    @Test
    void demoDataHasThreeLowStockWarnings() {
        assertEquals(3, InventoryService.getWarnings().size());
    }

    @Test
    void negativeCorrectionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> InventoryService.correct(ingredient("Käse"), -1, "Test"));
    }

    @Test
    void ingredientUsedInRecipeCannotBeDeleted() {
        assertThrows(IllegalStateException.class,
                () -> InventoryService.deleteIngredient(ingredient("Rindfleisch")));
    }

    @Test
    void unitsConvertToBaseUnits() {
        assertEquals(1500, Unit.KILOGRAMM.toBase(1.5));
        assertEquals(250, Unit.LITER.toBase(0.25));
        assertEquals("1,5 kg", Unit.KILOGRAMM.format(1500));
        assertTrue(Unit.GRAMM.isCompatible(Unit.KILOGRAMM));
        assertFalse(Unit.GRAMM.isCompatible(Unit.LITER));
    }

    @Test
    void stockIsSavedAndLoadedAgain() {
        InventoryService.receive(ingredient("Käse"), 2500, "Lieferung");
        InventoryService.addIngredient("Oliven", "Obst & Gemüse", Unit.KILOGRAMM, 1200, 500);

        // Neu laden aus derselben Datei
        InventoryService.init(file, false);

        assertEquals(7500, ingredient("Käse").getStock());
        assertEquals(1200, ingredient("Oliven").getStock());
        assertFalse(MenuService.findByName("Classic Burger").getRecipe().isEmpty());
        assertTrue(InventoryService.getMovements().stream()
                .anyMatch(m -> m.type() == MovementType.WARENEINGANG && m.delta() == 2500));
    }
}
