package at.gastroflow.frontend.model;

import at.gastroflow.backend.demo.model.Product;
import at.gastroflow.backend.demo.service.MenuService;
import at.gastroflow.backend.dto.CreateOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderDraftTest {

    private OrderDraft draft;
    private Product burger;
    private Product cola;

    @BeforeEach
    void setUp() {
        draft = new OrderDraft(1);
        burger = MenuService.findByName("Classic Burger");
        cola = MenuService.getAvailableProducts().stream()
                .filter(p -> p.getName().contains("Cola")).findFirst().orElseThrow();
    }

    @Test
    void addingSameProductIncreasesQuantity() {
        OrderDraft.Line first = draft.add(burger);
        OrderDraft.Line second = draft.add(burger);

        assertSame(first, second);
        assertEquals(1, draft.getLines().size());
        assertEquals(2, first.getQuantity());
    }

    @Test
    void quantityStaysBetweenOneAndMaximum() {
        OrderDraft.Line line = draft.add(burger);

        draft.decrease(line);
        assertEquals(1, line.getQuantity());

        for (int i = 0; i < 200; i++) {
            draft.increase(line);
        }
        assertEquals(OrderDraft.MAX_QUANTITY, line.getQuantity());
    }

    @Test
    void removeDeletesOnlyThatLine() {
        OrderDraft.Line line = draft.add(burger);
        draft.add(cola);

        draft.remove(line);

        assertEquals(1, draft.getLines().size());
        assertSame(cola, draft.getLines().get(0).getProduct());
    }

    @Test
    void totalIsSumOfAllLines() {
        OrderDraft.Line line = draft.add(burger);
        draft.increase(line);
        draft.add(cola);

        assertEquals(2 * burger.getPrice() + cola.getPrice(), draft.getTotal(), 0.001);
        assertEquals(3, draft.getItemCount());
    }

    @Test
    void totalIsRoundedToCents() {
        Product cheap = new Product(9001, "Test", 0.1, "Test", "", null, true);
        OrderDraft.Line line = draft.add(cheap);
        draft.increase(line);
        draft.increase(line);

        assertEquals(0.3, draft.getTotal());
    }

    @Test
    void emptyDraftCostsNothing() {
        assertEquals(0, draft.getTotal());
        assertTrue(draft.isEmpty());
    }

    @Test
    void sameProductWithAndWithoutRequestAreSeparateLines() {
        OrderDraft.Line withRequest = draft.add(burger);
        withRequest.setNote("ohne Zwiebeln");
        OrderDraft.Line normal = draft.add(burger);

        assertEquals(2, draft.getLines().size());
        assertEquals("", normal.getNote());
        assertEquals("1× Classic Burger: ohne Zwiebeln", draft.getNoteText());
    }

    @Test
    void lineBreaksInARequestStayOnOneLine() {
        draft.add(burger).setNote("ohne Zwiebeln\n\n  extra Käse");

        assertEquals("1× Classic Burger: ohne Zwiebeln, extra Käse", draft.getNoteText());
    }

    @Test
    void requestCarriesAllLinesAndTheSameIdEveryTime() {
        draft.add(burger).setNote("ohne Zwiebeln");
        draft.add(cola);

        CreateOrderRequest request = draft.toRequest("Service");

        assertEquals(1, request.table());
        assertEquals(2, request.items().size());
        assertEquals("ohne Zwiebeln", request.items().get(0).note());
        assertEquals(0, BigDecimal.valueOf(burger.getPrice()).compareTo(request.items().get(0).unitPrice()));
        assertEquals(request.id(), draft.toRequest("Service").id());
    }
}
