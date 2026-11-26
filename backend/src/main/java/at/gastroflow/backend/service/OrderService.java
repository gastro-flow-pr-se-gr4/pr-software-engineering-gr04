package at.gastroflow.backend.service;

import at.gastroflow.backend.dao.OrderDAO;
import at.gastroflow.backend.dto.CreateOrderRequest;
import at.gastroflow.backend.dto.OrderItemDTO;
import at.gastroflow.backend.model.Course;
import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderItem;
import at.gastroflow.backend.model.OrderStatus;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Bestellverwaltung: Regeln für Anlegen, Statusschritte, Bezahlen und Stornieren.
 * Fehler werfen eine Exception mit verständlicher Meldung:
 * IllegalArgumentException = ungültige Eingabe, IllegalStateException = Schritt gerade nicht erlaubt,
 * NoSuchElementException = Bestellung gibt es nicht.
 */
public class OrderService {

    public static final int MAX_QUANTITY = 99;
    public static final int MAX_NOTE_LENGTH = 500;

    private final OrderDAO orderDAO;
    private final CourseService courseService;

    public OrderService() {
        this(new OrderDAO(), new CourseService());
    }

    public OrderService(OrderDAO orderDAO, CourseService courseService) {
        this.orderDAO = orderDAO;
        this.courseService = courseService;
    }

    // ---------- Anlegen ----------

    /**
     * Prüft die Anfrage und speichert sie aufgeteilt nach Gängen: eine Bestellung pro Gang
     * (Getränke, Vorspeise, Hauptspeise, Nachspeise, in dieser Reihenfolge), alle mit Status Neu.
     * Getränke gehen nicht in die Küche. Bei einem Fehler wird nichts gespeichert.
     */
    public synchronized List<Order> createOrder(CreateOrderRequest request) throws SQLException {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Die Bestellung ist leer. Bitte zuerst ein Produkt auswählen.");
        }
        if (request.table() < 1) {
            throw new IllegalArgumentException("Bitte einen gültigen Tisch wählen.");
        }
        if (request.guests() < 0) {
            throw new IllegalArgumentException("Die Anzahl der Gäste darf nicht negativ sein.");
        }
        if (request.id() != null && orderDAO.existsSubmission(request.id())) {
            throw new IllegalStateException("Diese Bestellung wurde bereits an die Küche gesendet.");
        }

        Map<Course, List<OrderItem>> byCourse = new EnumMap<>(Course.class);
        for (OrderItemDTO item : request.items()) {
            OrderItem checked = checkItem(item);
            byCourse.computeIfAbsent(courseService.getCourse(checked.getCategory()), c -> new ArrayList<>()).add(checked);
        }

        String waiter = request.waiter() == null || request.waiter().isBlank() ? "Service" : request.waiter().trim();
        UUID submission = request.id() != null ? request.id() : UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        List<Order> orders = new ArrayList<>();
        byCourse.forEach((course, items) -> {
            int guests = orders.isEmpty() ? request.guests() : 0; // Gäste nur einmal zählen, nicht pro Gang
            Order order = new Order(null, request.table(), waiter, now, guests, items);
            order.setCourse(course);
            order.setSubmissionId(submission);
            orders.add(order);
        });
        orderDAO.saveAll(orders);
        return orders;
    }

    private OrderItem checkItem(OrderItemDTO item) {
        if (item == null || item.productName() == null || item.productName().isBlank()) {
            throw new IllegalArgumentException("Eine Position hat keinen Produktnamen.");
        }
        String name = item.productName().trim();
        if (item.quantity() < 1 || item.quantity() > MAX_QUANTITY) {
            throw new IllegalArgumentException("Die Menge von " + name + " muss zwischen 1 und " + MAX_QUANTITY + " liegen.");
        }
        if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Der Preis von " + name + " ist ungültig.");
        }
        String note = item.note() == null ? "" : item.note().trim().replaceAll("\\s*\\R\\s*", ", "); // immer einzeilig
        if (note.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Der Sonderwunsch zu " + name + " ist zu lang.");
        }
        return new OrderItem(name, item.category(), item.unitPrice(), item.quantity(), note);
    }

    // ---------- Abfragen ----------

    public List<Order> getAllOrders() throws SQLException {
        return orderDAO.findAll();
    }

    public Order getOrder(UUID id) throws SQLException {
        Order order = orderDAO.findById(id);
        if (order == null) {
            throw new NoSuchElementException("Diese Bestellung gibt es nicht.");
        }
        return order;
    }

    /** Noch nicht serviert und nicht storniert – das, woran Küche und Service arbeiten. */
    public List<Order> getOpenOrders() throws SQLException {
        return orderDAO.findActive().stream().filter(Order::isOpen).toList();
    }

    /** Alles, was noch läuft: unbezahlt oder noch nicht serviert. */
    public List<Order> getActiveOrders() throws SQLException {
        return orderDAO.findActive();
    }

    public List<Order> getActiveOrders(int table) throws SQLException {
        return orderDAO.findActive().stream().filter(order -> order.getTableNumber() == table).toList();
    }

    /** Gesendete, noch nicht bezahlte Bestellungen eines Tisches (älteste zuerst). */
    public List<Order> getUnpaidOrders(int table) throws SQLException {
        return getActiveOrders(table).stream().filter(Order::isPaymentDue).toList();
    }

    public List<Order> getOrdersOn(LocalDate date) throws SQLException {
        return orderDAO.findSince(date.atStartOfDay()).stream()
                .filter(order -> order.getCreatedAt().toLocalDate().equals(date))
                .toList();
    }

    /** Offener Betrag eines Tisches in Euro. */
    public BigDecimal getAmountDue(int table) throws SQLException {
        return sum(getUnpaidOrders(table));
    }

    /** Ein Tisch ist besetzt, solange etwas nicht serviert oder nicht bezahlt ist. */
    public boolean isTableOccupied(int table) throws SQLException {
        return !getActiveOrders(table).isEmpty();
    }

    // ---------- Bezahlen ----------

    /**
     * Kassiert alle offenen Bestellungen eines Tisches ab und gibt den bezahlten Betrag zurück.
     * Was noch nicht serviert ist, bleibt sichtbar und hält den Tisch besetzt.
     */
    public synchronized BigDecimal payTable(int table) throws SQLException {
        List<Order> unpaid = getUnpaidOrders(table);
        if (unpaid.isEmpty()) {
            throw new IllegalStateException("Für Tisch " + table + " ist nichts zu bezahlen.");
        }
        orderDAO.markPaid(unpaid.stream().map(Order::getId).toList(), LocalDateTime.now());
        return sum(unpaid);
    }

    // ---------- Weg der Bestellung: Neu → In Zubereitung → Fertig → serviert ----------

    /**
     * Setzt den nächsten Status. Schritte können nicht übersprungen oder wiederholt werden.
     * Getränke kommen nicht aus der Küche und können direkt serviert werden.
     * Bezahlt wird getrennt davon, auch eine bezahlte Bestellung kann noch serviert werden.
     */
    public synchronized Order changeStatus(UUID id, OrderStatus target) throws SQLException {
        Order order = getOrder(id);
        if (target == null || target == OrderStatus.NEU || target == OrderStatus.STORNIERT) {
            throw new IllegalArgumentException("Dieser Status kann so nicht gesetzt werden.");
        }
        boolean drinkServed = !order.isKitchenOrder() && order.getStatus() == OrderStatus.NEU
                && target == OrderStatus.ABGESCHLOSSEN;
        if (!drinkServed && order.getStatus().next() != target) {
            throw new IllegalStateException("Bestellung #" + order.getNumber() + " ist „" + order.getStatus()
                    + "“ und kann nicht auf „" + target + "“ gesetzt werden.");
        }
        orderDAO.updateStatus(id, target);
        order.setStatus(target);
        return order;
    }

    /** Storniert eine offene, noch nicht bezahlte Bestellung. */
    public synchronized Order cancel(UUID id) throws SQLException {
        Order order = getOrder(id);
        if (!order.isOpen()) {
            throw new IllegalStateException("Nur offene Bestellungen können storniert werden.");
        }
        if (order.isPaid()) {
            throw new IllegalStateException("Bereits bezahlte Bestellungen können nicht storniert werden.");
        }
        orderDAO.updateStatus(id, OrderStatus.STORNIERT);
        order.setStatus(OrderStatus.STORNIERT);
        return order;
    }

    /** Kann der Service diese Bestellung jetzt als serviert markieren? */
    public static boolean canServe(Order order) {
        return order.getStatus() == OrderStatus.FERTIG
                || (!order.isKitchenOrder() && order.getStatus() == OrderStatus.NEU);
    }

    // für die Küche (KitchenService)
    OrderDAO dao() {
        return orderDAO;
    }

    private BigDecimal sum(List<Order> orders) {
        return orders.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
