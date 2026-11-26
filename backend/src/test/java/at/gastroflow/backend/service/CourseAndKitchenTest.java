package at.gastroflow.backend.service;

import at.gastroflow.backend.dto.CreateOrderRequest;
import at.gastroflow.backend.dto.KitchenTableDTO;
import at.gastroflow.backend.dto.OrderItemDTO;
import at.gastroflow.backend.model.Course;
import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Getränke, Gänge und Abschließen in der Küche (#84). */
class CourseAndKitchenTest {

    private static final int TABLE = 11;

    private static final OrderItemDTO STARTER = new OrderItemDTO("Bruschetta", "Vorspeisen", new BigDecimal("7.90"), 1, "");
    private static final OrderItemDTO BURGER = new OrderItemDTO("Classic Burger", "Burger", new BigDecimal("14.90"), 1, "ohne Zwiebeln");
    private static final OrderItemDTO DESSERT = new OrderItemDTO("Tiramisu", "Desserts", new BigDecimal("6.90"), 1, "");
    private static final OrderItemDTO COLA = new OrderItemDTO("Coca-Cola 0,33 l", "Getränke", new BigDecimal("3.50"), 1, "ohne Eis");

    private CourseService courses;
    private OrderService orders;
    private KitchenService kitchen;

    @BeforeEach
    void setUp() {
        courses = new CourseService(new InMemoryCourseDAO());
        orders = new OrderService(new InMemoryOrderDAO(), courses);
        kitchen = new KitchenService(orders);
    }

    private List<Order> send(OrderItemDTO... items) throws SQLException {
        return orders.createOrder(new CreateOrderRequest(UUID.randomUUID(), TABLE, "Service", 2, List.of(items)));
    }

    // ---------- Gang pro Kategorie ----------

    @Test
    void categoriesHaveSensibleCourses() throws SQLException {
        assertEquals(Course.VORSPEISE, courses.getCourse("Vorspeisen"));
        assertEquals(Course.HAUPTSPEISE, courses.getCourse("Hauptgerichte"));
        assertEquals(Course.HAUPTSPEISE, courses.getCourse("Burger"));
        assertEquals(Course.NACHSPEISE, courses.getCourse("Desserts"));
        assertEquals(Course.GETRAENK, courses.getCourse("Getränke"));
        assertEquals(Course.GETRAENK, courses.getCourse("Kaffee & Tee"));
        assertEquals(Course.HAUPTSPEISE, CourseService.guessCourse("Hauptspeisen")); // nicht "Eis"
    }

    @Test
    void courseCanBeChangedAndSurvivesRenaming() throws SQLException {
        courses.setCourse("Snacks", Course.NACHSPEISE);
        courses.renameCategory("Snacks", "Süßes");

        assertEquals(Course.NACHSPEISE, courses.getCourse("Süßes"));
        assertThrows(IllegalArgumentException.class, () -> courses.setCourse("Snacks", null));
    }

    // ---------- Aufteilen beim Absenden ----------

    @Test
    void submitSplitsByCourseInOrder() throws SQLException {
        List<Order> sent = send(DESSERT, BURGER, COLA, STARTER);

        assertEquals(List.of(Course.GETRAENK, Course.VORSPEISE, Course.HAUPTSPEISE, Course.NACHSPEISE),
                sent.stream().map(Order::getCourse).toList());
        assertEquals(2, sent.get(0).getGuests()); // Gäste nur einmal
        assertEquals(0, sent.get(1).getGuests());
        assertEquals("1× Coca-Cola 0,33 l: ohne Eis", sent.get(0).getNoteText()); // jeder Gang nur seine Wünsche
        assertEquals("1× Classic Burger: ohne Zwiebeln", sent.get(2).getNoteText());
        assertEquals(new BigDecimal("33.20"), orders.getAmountDue(TABLE));
    }

    @Test
    void sameSubmissionCannotBeSentTwice() throws SQLException {
        CreateOrderRequest request = new CreateOrderRequest(UUID.randomUUID(), TABLE, "Service", 1, List.of(BURGER, COLA));
        orders.createOrder(request);

        assertThrows(IllegalStateException.class, () -> orders.createOrder(request));
        assertEquals(2, orders.getAllOrders().size());
    }

    // ---------- Getränke ----------

    @Test
    void drinksDoNotGoToTheKitchenAndCanBeServedDirectly() throws SQLException {
        Order drink = send(COLA).get(0);

        assertTrue(kitchen.getKitchenTables().isEmpty());
        assertTrue(OrderService.canServe(drink));

        orders.changeStatus(drink.getId(), OrderStatus.ABGESCHLOSSEN);
        assertEquals(OrderStatus.ABGESCHLOSSEN, orders.getOrder(drink.getId()).getStatus());
        assertTrue(orders.getOrder(drink.getId()).isPaymentDue()); // bleibt auf der Rechnung
    }

    @Test
    void foodCannotBeServedBeforeTheKitchenIsDone() throws SQLException {
        Order food = send(BURGER).get(0);

        assertFalse(OrderService.canServe(food));
        assertThrows(IllegalStateException.class, () -> orders.changeStatus(food.getId(), OrderStatus.ABGESCHLOSSEN));
    }

    // ---------- Küche Gang für Gang ----------

    @Test
    void coursesRunOneAfterAnother() throws SQLException {
        send(DESSERT, BURGER, COLA, STARTER);

        // Neu: nur die Vorspeise ist dran
        assertEquals(OrderStatus.NEU, kitchen.getStage(TABLE));
        assertEquals(Course.VORSPEISE, kitchen.getNextCourse(TABLE).orElseThrow());
        assertEquals(List.of(TABLE), kitchen.getKitchenTables());

        assertEquals(Course.VORSPEISE, kitchen.startNextCourse(TABLE));
        assertEquals(OrderStatus.IN_ZUBEREITUNG, kitchen.getStage(TABLE));
        assertThrows(IllegalStateException.class, () -> kitchen.startNextCourse(TABLE));

        kitchen.markCourseReady(TABLE);
        assertEquals(OrderStatus.FERTIG, kitchen.getStage(TABLE));
        KitchenTableDTO card = kitchen.getTable(TABLE);
        assertEquals(Course.HAUPTSPEISE, card.nextCourse());
        assertTrue(card.courses().get(0).done()); // grüner Haken bei der Vorspeise
        assertFalse(card.courses().get(1).done());

        // "Weiter mit Hauptspeise" geht direkt in Zubereitung, nicht zurück zu Neu
        assertEquals(Course.HAUPTSPEISE, kitchen.startNextCourse(TABLE));
        assertEquals(OrderStatus.IN_ZUBEREITUNG, kitchen.getStage(TABLE));
        assertTrue(kitchen.getTable(TABLE).courses().get(1).cooking());
        kitchen.markCourseReady(TABLE);

        // auch nachdem alles Fertige serviert wurde, bleibt der Tisch in "Fertig" mit dem nächsten Gang
        for (Order order : kitchen.getKitchenOrders(TABLE, OrderStatus.FERTIG)) {
            orders.changeStatus(order.getId(), OrderStatus.ABGESCHLOSSEN);
        }
        assertEquals(OrderStatus.FERTIG, kitchen.getStage(TABLE));
        assertFalse(kitchen.canClose(TABLE));

        assertEquals(Course.NACHSPEISE, kitchen.startNextCourse(TABLE));
        kitchen.markCourseReady(TABLE);
        assertNull(kitchen.getTable(TABLE).nextCourse());
        assertTrue(kitchen.canClose(TABLE));
        assertTrue(kitchen.getTable(TABLE).courses().stream().allMatch(KitchenTableDTO.CourseProgressDTO::done));

        // bleibt in "Fertig", bis die Küche abschließt
        kitchen.closeTable(TABLE);
        assertNull(kitchen.getStage(TABLE));
        assertTrue(kitchen.getKitchenTables().isEmpty());
        // noch nicht servierte Nachspeise bleibt beim Service
        assertTrue(orders.getActiveOrders(TABLE).stream().anyMatch(OrderService::canServe));
    }

    @Test
    void missingCoursesAreSkipped() throws SQLException {
        send(BURGER, DESSERT); // keine Vorspeise

        assertEquals(Course.HAUPTSPEISE, kitchen.startNextCourse(TABLE));
        kitchen.markCourseReady(TABLE);
        assertEquals(Course.NACHSPEISE, kitchen.getNextCourse(TABLE).orElseThrow());
    }

    @Test
    void kitchenCanOnlyCloseWhenAllCoursesAreDone() throws SQLException {
        send(STARTER, BURGER);
        assertThrows(IllegalStateException.class, () -> kitchen.markCourseReady(TABLE));
        assertThrows(IllegalStateException.class, () -> kitchen.closeTable(TABLE));

        kitchen.startNextCourse(TABLE);
        kitchen.markCourseReady(TABLE);
        assertThrows(IllegalStateException.class, () -> kitchen.closeTable(TABLE)); // Hauptspeise kommt noch
    }

    @Test
    void newOrderAfterClosingStartsFreshInTheKitchen() throws SQLException {
        send(BURGER);
        kitchen.startNextCourse(TABLE);
        kitchen.markCourseReady(TABLE);
        kitchen.closeTable(TABLE);

        send(DESSERT);

        assertEquals(OrderStatus.NEU, kitchen.getStage(TABLE));
    }
}
