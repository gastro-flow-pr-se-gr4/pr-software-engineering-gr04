package at.gastroflow.backend.service;

import at.gastroflow.backend.dto.CreateOrderRequest;
import at.gastroflow.backend.dto.OrderItemDTO;
import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests für die Bestellverwaltung im echten Backend (ohne Datenbank, mit InMemoryOrderDAO). */
class OrderServiceTest {

    private InMemoryOrderDAO dao;
    private OrderService service;

    private static final OrderItemDTO BURGER = item("Classic Burger", "14.90", 1, "");
    private static final OrderItemDTO COLA = item("Coca-Cola 0,33 l", "3.50", 1, "");

    @BeforeEach
    void setUp() {
        dao = new InMemoryOrderDAO();
        service = new OrderService(dao);
    }

    private static OrderItemDTO item(String name, String price, int quantity, String note) {
        return new OrderItemDTO(name, "Test", new BigDecimal(price), quantity, note);
    }

    private Order send(int table, OrderItemDTO... items) throws SQLException {
        return service.createOrder(new CreateOrderRequest(UUID.randomUUID(), table, "Service", 1, List.of(items)));
    }

    // ---------- Absenden (#19) und Gesamtpreis (#41) ----------

    @Test
    void createOrderSavesAllItems() throws SQLException {
        Order order = send(1, item("Classic Burger", "14.90", 2, " ohne Zwiebeln "), COLA);

        assertEquals(OrderStatus.NEU, order.getStatus());
        assertEquals(10001, order.getNumber());
        assertEquals(2, order.getItems().size());
        assertEquals(new BigDecimal("33.30"), order.getTotal());
        assertEquals("2× Classic Burger: ohne Zwiebeln", order.getNoteText());
        assertEquals(List.of(order), service.getOpenOrders());
    }

    @Test
    void lineBreaksInANoteStayOnOneLine() throws SQLException {
        Order order = send(1, item("Classic Burger", "14.90", 1, "ohne Zwiebeln\n\n  extra Käse"));

        assertEquals("1× Classic Burger: ohne Zwiebeln, extra Käse", order.getNoteText());
    }

    // ---------- Leere und ungültige Bestellungen (#51, #54) ----------

    @Test
    void emptyOrderIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createOrder(new CreateOrderRequest(UUID.randomUUID(), 1, "Service", 1, List.of())));
        assertEquals(0, dao.saveCalls);
    }

    @Test
    void invalidTableQuantityOrPriceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> send(0, BURGER));
        assertThrows(IllegalArgumentException.class, () -> send(1, item("Classic Burger", "14.90", 0, "")));
        assertThrows(IllegalArgumentException.class, () -> send(1, item("Classic Burger", "14.90", 100, "")));
        assertThrows(IllegalArgumentException.class, () -> send(1, item("Classic Burger", "-1", 1, "")));
        assertThrows(IllegalArgumentException.class, () -> send(1, item(" ", "1", 1, "")));
        assertEquals(0, dao.saveCalls);
    }

    // ---------- Doppelte Übermittlung (#42) ----------

    @Test
    void sameOrderCannotBeSentTwice() throws SQLException {
        CreateOrderRequest request = new CreateOrderRequest(UUID.randomUUID(), 1, "Service", 1, List.of(BURGER));
        service.createOrder(request);

        assertThrows(IllegalStateException.class, () -> service.createOrder(request));
        assertEquals(1, service.getAllOrders().size());
    }

    // ---------- Statusschritte (#22, #23, #50, #43) ----------

    @Test
    void orderRunsFromNewToServed() throws SQLException {
        Order order = send(1, BURGER);

        service.changeStatus(order.getId(), OrderStatus.IN_ZUBEREITUNG);
        service.changeStatus(order.getId(), OrderStatus.FERTIG);
        Order served = service.changeStatus(order.getId(), OrderStatus.ABGESCHLOSSEN);

        assertEquals(OrderStatus.ABGESCHLOSSEN, served.getStatus());
        assertTrue(service.getOpenOrders().isEmpty());
        assertFalse(served.isPaid()); // servieren ist nicht bezahlen
    }

    @Test
    void stepsCannotBeSkippedOrRepeated() throws SQLException {
        Order order = send(1, BURGER);

        assertThrows(IllegalStateException.class, () -> service.changeStatus(order.getId(), OrderStatus.FERTIG));
        assertThrows(IllegalStateException.class, () -> service.changeStatus(order.getId(), OrderStatus.ABGESCHLOSSEN));
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(order.getId(), OrderStatus.STORNIERT));

        service.changeStatus(order.getId(), OrderStatus.IN_ZUBEREITUNG);
        assertThrows(IllegalStateException.class, () -> service.changeStatus(order.getId(), OrderStatus.IN_ZUBEREITUNG));
        assertEquals(OrderStatus.IN_ZUBEREITUNG, service.getOrder(order.getId()).getStatus());
    }

    @Test
    void unknownOrderIsReported() {
        assertThrows(NoSuchElementException.class, () -> service.getOrder(UUID.randomUUID()));
    }

    // ---------- Rechnung und Bezahlen (#82, #83) ----------

    @Test
    void sentOrdersStayOnTheBillOfTheirTable() throws SQLException {
        Order first = send(9, BURGER);
        Order second = send(9, item("Coca-Cola 0,33 l", "3.50", 2, ""));
        send(10, BURGER);

        assertEquals(List.of(first.getId(), second.getId()),
                service.getUnpaidOrders(9).stream().map(Order::getId).toList());
        assertEquals(new BigDecimal("21.90"), service.getAmountDue(9));
        assertTrue(service.isTableOccupied(9));
    }

    @Test
    void payingClearsTheBillAndFreesTheTable() throws SQLException {
        Order order = send(9, BURGER);
        service.changeStatus(order.getId(), OrderStatus.IN_ZUBEREITUNG);
        service.changeStatus(order.getId(), OrderStatus.FERTIG);
        service.changeStatus(order.getId(), OrderStatus.ABGESCHLOSSEN);

        assertEquals(new BigDecimal("14.90"), service.payTable(9));

        assertTrue(service.getOrder(order.getId()).isPaid());
        assertEquals(BigDecimal.ZERO, service.getAmountDue(9));
        assertFalse(service.isTableOccupied(9));
    }

    @Test
    void readyFoodStaysWhenTheTablePays() throws SQLException {
        Order order = send(9, BURGER);
        service.changeStatus(order.getId(), OrderStatus.IN_ZUBEREITUNG);
        service.changeStatus(order.getId(), OrderStatus.FERTIG);

        service.payTable(9);

        assertEquals(OrderStatus.FERTIG, service.getOrder(order.getId()).getStatus()); // muss noch serviert werden
        assertTrue(service.isTableOccupied(9));
        service.changeStatus(order.getId(), OrderStatus.ABGESCHLOSSEN);
        assertFalse(service.isTableOccupied(9));
    }

    @Test
    void tableCannotBePaidTwiceOrWhenEmpty() throws SQLException {
        assertThrows(IllegalStateException.class, () -> service.payTable(9));
        send(9, BURGER);
        service.payTable(9);
        assertThrows(IllegalStateException.class, () -> service.payTable(9));
    }

    // ---------- Stornieren (#52) ----------

    @Test
    void cancelledOrdersAreNotOnTheBill() throws SQLException {
        Order order = send(9, BURGER);
        service.cancel(order.getId());

        assertEquals(BigDecimal.ZERO, service.getAmountDue(9));
        assertFalse(service.isTableOccupied(9));
        assertThrows(IllegalStateException.class, () -> service.cancel(order.getId()));
        assertThrows(IllegalStateException.class, () -> service.changeStatus(order.getId(), OrderStatus.IN_ZUBEREITUNG));
    }

    @Test
    void paidOrderCannotBeCancelled() throws SQLException {
        Order order = send(9, BURGER);
        service.payTable(9);

        assertThrows(IllegalStateException.class, () -> service.cancel(order.getId()));
        assertEquals(OrderStatus.NEU, service.getOrder(order.getId()).getStatus());
    }
}
