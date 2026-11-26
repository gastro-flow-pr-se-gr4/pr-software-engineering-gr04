package at.gastroflow.backend.demo.service;

import at.gastroflow.backend.demo.model.Order;
import at.gastroflow.backend.demo.model.OrderDraft;
import at.gastroflow.backend.demo.model.OrderStatus;
import at.gastroflow.backend.demo.model.Product;
import at.gastroflow.backend.demo.model.Reservation;
import at.gastroflow.backend.demo.model.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests für das Zusammenstellen und Absenden einer Bestellung. */
class OrderServiceTest {

    @TempDir
    Path tempDir;

    private OrderDraft draft;
    private Product burger;
    private Product cola;

    @BeforeEach
    void setUp() {
        InventoryService.init(tempDir.resolve("lager.txt"), false);
        draft = new OrderDraft(1);
        burger = MenuService.findByName("Classic Burger");
        cola = MenuService.getAvailableProducts().stream()
                .filter(p -> p.getName().contains("Cola")).findFirst().orElseThrow();
    }

    private int orderCount() {
        return OrderService.getSentOrders().size();
    }

    // ---------- Positionen und Mengen (#17) ----------

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

    // ---------- Gesamtpreis (#41) ----------

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
        draft.increase(line); // 0.1 * 3 wäre ohne Runden 0.30000000000000004

        assertEquals(0.3, draft.getTotal());
    }

    @Test
    void emptyDraftCostsNothing() {
        assertEquals(0, draft.getTotal());
        assertTrue(draft.isEmpty());
    }

    // ---------- Absenden (#19) ----------

    @Test
    void submitCreatesOrderWithAllItems() {
        OrderDraft.Line line = draft.add(burger);
        draft.increase(line);
        line.setNote(" ohne Zwiebeln ");
        draft.add(cola);

        Order order = OrderService.submit(draft, "Service");

        assertTrue(OrderService.getOpenOrders().contains(order));
        assertEquals(OrderStatus.NEU, order.getStatus());
        assertEquals(2, order.getItems().size());
        assertEquals(2, order.getItems().get(0).getQuantity());
        assertEquals(draft.getTotal(), order.getTotal(), 0.001);
        assertEquals("2× Classic Burger: ohne Zwiebeln", order.getNote());
        assertTrue(InventoryService.isBooked(order));
    }

    // ---------- Leere Bestellungen (#51) ----------

    @Test
    void emptyDraftIsRejected() {
        int before = orderCount();

        assertThrows(IllegalArgumentException.class, () -> OrderService.submit(draft, "Service"));
        assertEquals(before, orderCount());
    }

    @Test
    void emptyOrderIsRejected() {
        int before = orderCount();
        Order empty = new Order(1, "Service", LocalDateTime.now(), 1, List.of(), OrderStatus.NEU);

        assertThrows(IllegalArgumentException.class, () -> OrderService.send(empty));
        assertEquals(before, orderCount());
    }

    // ---------- Doppelte Übermittlung (#42) ----------

    @Test
    void sameDraftCannotBeSentTwice() {
        draft.add(burger);
        OrderService.submit(draft, "Service");
        int afterFirst = orderCount();

        assertThrows(IllegalStateException.class, () -> OrderService.submit(draft, "Service"));
        assertEquals(afterFirst, orderCount());
    }

    @Test
    void sameOrderCannotBeSentTwice() {
        draft.add(burger);
        Order order = OrderService.submit(draft, "Service");
        int number = order.getNumber();
        int afterFirst = orderCount();

        assertThrows(IllegalStateException.class, () -> OrderService.send(order));
        assertEquals(afterFirst, orderCount());
        assertEquals(number, order.getNumber());
    }

    @Test
    void sameProductsCanBeOrderedAgainInNewDraft() {
        draft.add(cola);
        OrderService.submit(draft, "Service");
        int afterFirst = orderCount();

        OrderDraft second = new OrderDraft(1);
        second.add(cola);
        OrderService.submit(second, "Service");

        assertEquals(afterFirst + 1, orderCount());
    }

    // ---------- Fehlermeldungen (#54) ----------

    @Test
    void unavailableProductIsRejectedAndDraftKept() {
        draft.add(burger);
        draft.add(cola);
        int before = orderCount();
        burger.setAvailable(false);
        try {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> OrderService.submit(draft, "Service"));

            assertTrue(error.getMessage().contains("Classic Burger"));
            assertFalse(error.getMessage().contains(cola.getName()));
            assertEquals(before, orderCount());
            assertEquals(2, draft.getLines().size());
            assertFalse(draft.isSent());
        } finally {
            burger.setAvailable(true);
        }

        // nach dem Beheben klappt das Absenden
        OrderService.submit(draft, "Service");
        assertEquals(before + 1, orderCount());
    }

    @Test
    void unknownTableIsRejected() {
        OrderDraft wrongTable = new OrderDraft(999);
        wrongTable.add(burger);

        assertThrows(IllegalArgumentException.class, () -> OrderService.submit(wrongTable, "Service"));
    }

    // ---------- Rechnung und Bezahlen (#83) ----------

    private static final int BILL_TABLE = 9;

    private Order sendTo(int table, Product... products) {
        OrderDraft d = new OrderDraft(table);
        for (Product product : products) {
            d.add(product);
        }
        return OrderService.submit(d, "Service");
    }

    /** Offene Reste anderer Tests am Tisch abschließen. */
    private void clearTable(int table) {
        if (OrderService.getAmountDue(table) > 0) {
            OrderService.payTable(table);
        }
        OrderService.getSentOrders().stream().filter(o -> o.getTable() == table && o.isOpen())
                .forEach(o -> OrderService.changeStatus(o, OrderStatus.ABGESCHLOSSEN));
    }

    @Test
    void sentOrdersStayOnTheBillOfTheirTable() {
        clearTable(BILL_TABLE);
        Order first = sendTo(BILL_TABLE, burger);
        Order second = sendTo(BILL_TABLE, cola, cola);
        Order otherTable = sendTo(BILL_TABLE + 1, burger);

        assertEquals(List.of(first, second), OrderService.getUnpaidOrders(BILL_TABLE));
        assertFalse(OrderService.getUnpaidOrders(BILL_TABLE).contains(otherTable));
        assertEquals(burger.getPrice() + 2 * cola.getPrice(), OrderService.getAmountDue(BILL_TABLE), 0.001);
    }

    @Test
    void orderStaysOnTheBillAfterKitchenIsDone() {
        clearTable(BILL_TABLE);
        Order order = sendTo(BILL_TABLE, burger);

        OrderService.changeStatus(order, OrderStatus.ABGESCHLOSSEN); // von der Küche abgeholt

        assertEquals(List.of(order), OrderService.getUnpaidOrders(BILL_TABLE));
        assertTrue(OrderService.isTableOccupied(BILL_TABLE));
    }

    @Test
    void cancelledOrdersAreNotOnTheBill() {
        clearTable(BILL_TABLE);
        OrderService.cancel(sendTo(BILL_TABLE, burger));

        assertEquals(0, OrderService.getAmountDue(BILL_TABLE));
        assertThrows(IllegalStateException.class, () -> OrderService.payTable(BILL_TABLE));
    }

    @Test
    void payingClearsTheBillAndFreesTheTable() {
        clearTable(BILL_TABLE);
        Order order = sendTo(BILL_TABLE, burger);
        OrderService.changeStatus(order, OrderStatus.ABGESCHLOSSEN); // serviert

        assertEquals(burger.getPrice(), OrderService.payTable(BILL_TABLE), 0.001);

        assertTrue(order.isPaid());
        assertEquals(0, OrderService.getAmountDue(BILL_TABLE));
        assertFalse(OrderService.isTableOccupied(BILL_TABLE));
    }

    @Test
    void readyFoodDoesNotDisappearWhenTheTablePays() {
        clearTable(BILL_TABLE);
        Order order = sendTo(BILL_TABLE, burger);
        OrderService.changeStatus(order, OrderStatus.FERTIG);

        OrderService.payTable(BILL_TABLE);

        assertEquals(OrderStatus.FERTIG, order.getStatus()); // muss noch serviert werden
        assertTrue(OrderService.getOpenOrders().contains(order));
        assertTrue(OrderService.isTableOccupied(BILL_TABLE));
    }

    @Test
    void tableStaysOccupiedWhenPaidBeforeKitchenIsDone() {
        clearTable(BILL_TABLE);
        Order order = sendTo(BILL_TABLE, burger);

        OrderService.payTable(BILL_TABLE);

        assertTrue(order.isPaid());
        assertEquals(OrderStatus.NEU, order.getStatus()); // bleibt in der Küche sichtbar
        assertTrue(OrderService.isTableOccupied(BILL_TABLE));
        assertTrue(OrderService.getOpenOrders().contains(order));
    }

    @Test
    void tableCannotBePaidTwice() {
        clearTable(BILL_TABLE);
        Order order = sendTo(BILL_TABLE, burger);
        OrderService.payTable(BILL_TABLE);

        assertThrows(IllegalStateException.class, () -> OrderService.payTable(BILL_TABLE));
        assertThrows(IllegalStateException.class, () -> order.markPaid(LocalDateTime.now()));
    }

    @Test
    void paidOrderCannotBeCancelled() {
        clearTable(BILL_TABLE);
        Order order = sendTo(BILL_TABLE, burger);
        OrderService.payTable(BILL_TABLE);

        assertThrows(IllegalStateException.class, () -> OrderService.cancel(order));
        assertEquals(OrderStatus.NEU, order.getStatus());
    }

    @Test
    void payingEndsThePresentReservation() {
        clearTable(BILL_TABLE);
        Reservation reservation = ReservationService.add("Testgast", "", 2, BILL_TABLE,
                LocalDate.now().atTime(8, 0), 60, ReservationStatus.ANWESEND, "");
        try {
            sendTo(BILL_TABLE, burger);
            OrderService.payTable(BILL_TABLE);
            assertEquals(ReservationStatus.ABGESCHLOSSEN, reservation.getStatus());
        } finally {
            ReservationService.delete(reservation);
        }
    }

    // ---------- Zubereitung, Fertig, Serviert (#22, #23, #50) ----------

    @Test
    void orderRunsFromNewToServed() {
        Order order = sendTo(BILL_TABLE, burger);

        OrderService.startPreparation(order);
        assertEquals(OrderStatus.IN_ZUBEREITUNG, order.getStatus());

        OrderService.markReady(order);
        assertEquals(OrderStatus.FERTIG, order.getStatus());

        OrderService.markServed(order);
        assertEquals(OrderStatus.ABGESCHLOSSEN, order.getStatus());
        assertFalse(OrderService.getOpenOrders().contains(order));
        assertFalse(order.isPaid()); // servieren ist nicht bezahlen
    }

    @Test
    void stepsCannotBeSkippedOrRepeated() {
        Order order = sendTo(BILL_TABLE, burger);

        assertThrows(IllegalStateException.class, () -> OrderService.markReady(order));
        assertThrows(IllegalStateException.class, () -> OrderService.markServed(order));
        assertEquals(OrderStatus.NEU, order.getStatus());

        OrderService.startPreparation(order);
        assertThrows(IllegalStateException.class, () -> OrderService.startPreparation(order));
        assertThrows(IllegalStateException.class, () -> OrderService.markServed(order));
        assertEquals(OrderStatus.IN_ZUBEREITUNG, order.getStatus());
    }

    @Test
    void cancelledOrderCannotBePrepared() {
        Order order = sendTo(BILL_TABLE, burger);
        OrderService.cancel(order);

        assertThrows(IllegalStateException.class, () -> OrderService.startPreparation(order));
        assertEquals(OrderStatus.STORNIERT, order.getStatus());
    }

    @Test
    void paidOrderCanStillBeServed() {
        clearTable(BILL_TABLE);
        Order order = sendTo(BILL_TABLE, burger);
        OrderService.payTable(BILL_TABLE);

        assertEquals(List.of(order), OrderService.getActiveOrders(BILL_TABLE));

        OrderService.startPreparation(order);
        OrderService.markReady(order);
        OrderService.markServed(order);

        assertTrue(OrderService.getActiveOrders(BILL_TABLE).isEmpty());
        assertFalse(OrderService.isTableOccupied(BILL_TABLE));
    }

    // ---------- Sonderwünsche für die Küche (#53) ----------

    @Test
    void everySpecialRequestNamesItsProduct() {
        draft.add(burger).setNote("ohne Zwiebeln");
        draft.add(cola).setNote("ohne Eis");

        Order order = OrderService.submit(draft, "Service");

        assertEquals("1× Classic Burger: ohne Zwiebeln\n1× " + cola.getName() + ": ohne Eis", order.getNote());
    }

    @Test
    void sameProductWithAndWithoutRequestAreSeparateLines() {
        OrderDraft.Line withRequest = draft.add(burger);
        withRequest.setNote("ohne Zwiebeln");
        OrderDraft.Line normal = draft.add(burger);

        assertEquals(2, draft.getLines().size());
        assertEquals("", normal.getNote());
        assertEquals("1× Classic Burger: ohne Zwiebeln", draft.getNoteText());
        assertEquals(2 * burger.getPrice(), draft.getTotal(), 0.001);
    }

    @Test
    void lineBreaksInARequestStayOnOneLine() {
        draft.add(burger).setNote("ohne Zwiebeln\n\n  extra Käse");

        assertEquals("1× Classic Burger: ohne Zwiebeln, extra Käse", draft.getNoteText());
    }

    @Test
    void stockIsBookedOnceForSplitLines() {
        draft.add(burger).setNote("ohne Zwiebeln");
        draft.add(burger);

        long before = bunStock();
        OrderService.submit(draft, "Service");

        assertEquals(before - 2, bunStock());
    }

    private long bunStock() {
        return InventoryService.getIngredients(true).stream()
                .filter(i -> i.getName().equals("Burgerbrötchen")).findFirst().orElseThrow().getStock();
    }
}
