package at.gastroflow.backend.service;

import at.gastroflow.backend.dto.KitchenTableDTO;
import at.gastroflow.backend.mapper.OrderMapper;
import at.gastroflow.backend.model.Course;
import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderStatus;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Küche: jeder Tisch wird Gang für Gang gekocht (Vorspeise → Hauptspeise → Nachspeise).
 * Getränke erscheinen hier nicht, die bringt der Service.
 */
public class KitchenService {

    private final OrderService orderService;
    private final OrderMapper mapper = new OrderMapper();

    public KitchenService(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Laufende Küchenbestellungen eines Tisches (ohne Getränke, ohne Abgeschlossene), nach Gang und Zeit. */
    public List<Order> getKitchenOrders(int table) throws SQLException {
        return kitchenOrders().stream().filter(order -> order.getTableNumber() == table).toList();
    }

    public List<Order> getKitchenOrders(int table, OrderStatus status) throws SQLException {
        return getKitchenOrders(table).stream().filter(order -> order.getStatus() == status).toList();
    }

    /** Tische, bei denen die Küche noch etwas zu tun hat oder abschließen kann (ältester zuerst). */
    public List<Integer> getKitchenTables() throws SQLException {
        List<Integer> tables = kitchenOrders().stream()
                .sorted(Comparator.comparing(Order::getCreatedAt))
                .map(Order::getTableNumber)
                .distinct()
                .toList();
        List<Integer> result = new java.util.ArrayList<>();
        for (int table : tables) {
            if (getStage(table) != null) {
                result.add(table);
            }
        }
        return result;
    }

    /** Nächster Gang, der noch nicht begonnen wurde – leer, wenn alles begonnen ist. */
    public Optional<Course> getNextCourse(int table) throws SQLException {
        return getKitchenOrders(table, OrderStatus.NEU).stream().map(Order::getCourse).min(Comparator.naturalOrder());
    }

    /**
     * In welcher Küchenspalte steht der Tisch?
     * NEU: noch nichts begonnen. IN_ZUBEREITUNG: ein Gang wird gekocht.
     * FERTIG: ein Gang ist fertig oder serviert – es kommt noch einer, oder die Küche kann abschließen.
     * null: die Küche hat für diesen Tisch nichts (mehr) zu tun.
     */
    public OrderStatus getStage(int table) throws SQLException {
        List<Order> orders = getKitchenOrders(table);
        boolean preparing = orders.stream().anyMatch(order -> order.getStatus() == OrderStatus.IN_ZUBEREITUNG);
        boolean waiting = orders.stream().anyMatch(order -> order.getStatus() == OrderStatus.NEU);
        boolean started = orders.stream().anyMatch(order -> order.getStatus() != OrderStatus.NEU);

        if (preparing) {
            return OrderStatus.IN_ZUBEREITUNG;
        }
        if (started) {
            return OrderStatus.FERTIG;
        }
        if (waiting) {
            return OrderStatus.NEU;
        }
        return null;
    }

    /**
     * Startet den nächsten Gang: alle neuen Bestellungen dieses Gangs gehen direkt in Zubereitung
     * (auch bei "Weiter mit …" – nicht zurück nach Neu). Nicht bestellte Gänge werden übersprungen.
     */
    public synchronized Course startNextCourse(int table) throws SQLException {
        if (!getKitchenOrders(table, OrderStatus.IN_ZUBEREITUNG).isEmpty()) {
            throw new IllegalStateException("Bei Tisch " + table + " ist noch ein Gang in Zubereitung. Bitte zuerst „Fertig“ drücken.");
        }
        Course course = getNextCourse(table).orElseThrow(() ->
                new IllegalStateException("Für Tisch " + table + " gibt es keinen weiteren Gang."));
        for (Order order : getKitchenOrders(table, OrderStatus.NEU)) {
            if (order.getCourse() == course) {
                orderService.changeStatus(order.getId(), OrderStatus.IN_ZUBEREITUNG);
            }
        }
        return course;
    }

    /** Alles, was bei einem Tisch gerade gekocht wird, ist fertig. */
    public synchronized void markCourseReady(int table) throws SQLException {
        List<Order> preparing = getKitchenOrders(table, OrderStatus.IN_ZUBEREITUNG);
        if (preparing.isEmpty()) {
            throw new IllegalStateException("Bei Tisch " + table + " wird gerade nichts zubereitet.");
        }
        for (Order order : preparing) {
            orderService.changeStatus(order.getId(), OrderStatus.FERTIG);
        }
    }

    /** Abschließen geht erst, wenn alle Gänge fertig sind und keiner mehr wartet. */
    public boolean canClose(int table) throws SQLException {
        List<Order> orders = getKitchenOrders(table);
        return !orders.isEmpty() && orders.stream().allMatch(order ->
                order.getStatus() == OrderStatus.FERTIG || order.getStatus() == OrderStatus.ABGESCHLOSSEN);
    }

    /**
     * Die Küche ist mit dem Tisch fertig und nimmt ihn aus der Übersicht.
     * Noch nicht servierte Gänge bleiben beim Service, bis sie serviert sind.
     */
    public synchronized void closeTable(int table) throws SQLException {
        if (!canClose(table)) {
            throw new IllegalStateException("Tisch " + table + " kann erst abgeschlossen werden, wenn alle Gänge fertig sind.");
        }
        List<UUID> ids = getKitchenOrders(table).stream().map(Order::getId).toList();
        orderService.dao().closeKitchen(ids);
    }

    /** Alles, was die Küchenansicht für einen Tisch braucht. */
    public KitchenTableDTO getTable(int table) throws SQLException {
        List<Order> orders = getKitchenOrders(table);
        List<KitchenTableDTO.CourseProgressDTO> courses = new java.util.ArrayList<>();
        for (Course course : orders.stream().map(Order::getCourse).distinct().sorted().toList()) {
            List<Order> ofCourse = orders.stream().filter(order -> order.getCourse() == course).toList();
            boolean done = ofCourse.stream().allMatch(order -> order.getStatus() == OrderStatus.FERTIG
                    || order.getStatus() == OrderStatus.ABGESCHLOSSEN);
            boolean cooking = ofCourse.stream().anyMatch(order -> order.getStatus() == OrderStatus.IN_ZUBEREITUNG);
            courses.add(new KitchenTableDTO.CourseProgressDTO(course, done, cooking));
        }
        return new KitchenTableDTO(table, getStage(table), getNextCourse(table).orElse(null), canClose(table),
                courses, orders.stream().map(mapper::toDTO).toList());
    }

    public List<KitchenTableDTO> getTables() throws SQLException {
        List<KitchenTableDTO> result = new java.util.ArrayList<>();
        for (int table : getKitchenTables()) {
            result.add(getTable(table));
        }
        return result;
    }

    // Küchenbestellung, die noch läuft und die Küche noch nicht abgeschlossen hat
    private List<Order> kitchenOrders() throws SQLException {
        return orderService.getActiveOrders().stream()
                .filter(order -> order.isKitchenOrder() && order.getStatus() != OrderStatus.STORNIERT
                        && !order.isKitchenClosed())
                .sorted(Comparator.comparing(Order::getCourse).thenComparing(Order::getCreatedAt))
                .toList();
    }
}
