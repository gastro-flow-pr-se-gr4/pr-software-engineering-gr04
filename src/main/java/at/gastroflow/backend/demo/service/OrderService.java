package at.gastroflow.backend.demo.service;

import at.gastroflow.backend.demo.model.Order;
import at.gastroflow.backend.demo.model.OrderDraft;
import at.gastroflow.backend.demo.model.OrderStatus;
import at.gastroflow.backend.demo.model.Product;
import at.gastroflow.backend.demo.model.Reservation;
import at.gastroflow.backend.demo.model.ReservationStatus;
import at.gastroflow.backend.demo.model.RestaurantSettings;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Temporäre Speicherung aller Bestellungen (gemeinsam für Service, Küche und Leitung). */
public class OrderService {
    private static final List<Order> SENT_ORDERS = new ArrayList<>();
    private static int nextNumber = 1000;

    /**
     * Sendet eine zusammengestellte Bestellung an die Küche.
     * Bei einem Fehler wird eine Exception mit verständlicher Meldung geworfen
     * und nichts gespeichert – der Entwurf bleibt dann unverändert.
     */
    public static synchronized Order submit(OrderDraft draft, String waiter) {
        if (draft.isSent()) {
            throw new IllegalStateException("Diese Bestellung wurde bereits an die Küche gesendet.");
        }
        if (draft.isEmpty()) {
            throw new IllegalArgumentException("Die Bestellung ist leer. Bitte zuerst ein Produkt auswählen.");
        }
        if (draft.getTable() < 1 || draft.getTable() > RestaurantSettings.get().getTableCount()) {
            throw new IllegalArgumentException("Tisch " + draft.getTable() + " gibt es nicht mehr. Bitte einen anderen Tisch wählen.");
        }
        String unavailable = draft.getLines().stream()
                .map(OrderDraft.Line::getProduct)
                .filter(product -> !MenuService.getAvailableProducts().contains(product))
                .map(Product::getName)
                .collect(Collectors.joining(", "));
        if (!unavailable.isEmpty()) {
            throw new IllegalArgumentException("Nicht mehr verfügbar: " + unavailable + ". Bitte aus der Bestellung entfernen.");
        }

        Order order = new Order(draft.getTable(), waiter, LocalDateTime.now(), 1,
                draft.toItems(), OrderStatus.NEU);
        order.setNote(draft.getNoteText());
        draft.markSent(); // ab hier kann derselbe Entwurf nicht noch einmal gesendet werden
        send(order);
        return order;
    }

    /** Neue Bestellung: wird gespeichert und automatisch vom Lager abgebucht (laut Rezeptur). */
    public static synchronized void send(Order order) {
        if (order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Eine leere Bestellung kann nicht gesendet werden.");
        }
        if (SENT_ORDERS.contains(order)) {
            throw new IllegalStateException("Diese Bestellung wurde bereits an die Küche gesendet.");
        }
        addExisting(order);
        InventoryService.bookOrder(order);
    }

    /** Übernimmt eine bereits vorhandene Bestellung ohne Lagerbuchung (z. B. Verlauf früherer Tage). */
    public static void addExisting(Order order) {
        order.setNumber(nextNumber++);
        SENT_ORDERS.add(order);
    }

    public static List<Order> getSentOrders() {
        return Collections.unmodifiableList(SENT_ORDERS);
    }

    /** Bestellungen von from (inklusive) bis to (exklusive). */
    public static List<Order> getOrdersBetween(LocalDateTime from, LocalDateTime to) {
        List<Order> result = new ArrayList<>();
        for (Order order : SENT_ORDERS) {
            LocalDateTime time = order.getCreatedAt();
            if (!time.isBefore(from) && time.isBefore(to)) {
                result.add(order);
            }
        }
        return result;
    }

    public static List<Order> getOrdersOn(LocalDate date) {
        return getOrdersBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    public static List<Order> getOpenOrders() {
        return SENT_ORDERS.stream().filter(Order::isOpen).toList();
    }

    // ---------- Abrechnung ----------

    /** Gesendete, noch nicht bezahlte Bestellungen eines Tisches (älteste zuerst). */
    public static List<Order> getUnpaidOrders(int table) {
        return SENT_ORDERS.stream()
                .filter(order -> order.getTable() == table && order.isPaymentDue())
                .sorted(Comparator.comparing(Order::getCreatedAt))
                .toList();
    }

    /** Alles, was an einem Tisch noch läuft: unbezahlt oder bezahlt, aber noch nicht serviert. */
    public static List<Order> getActiveOrders(int table) {
        return SENT_ORDERS.stream()
                .filter(order -> order.getTable() == table && (order.isOpen() || order.isPaymentDue()))
                .sorted(Comparator.comparing(Order::getCreatedAt))
                .toList();
    }

    /** Offener Betrag eines Tisches in Euro. */
    public static double getAmountDue(int table) {
        double sum = getUnpaidOrders(table).stream().mapToDouble(Order::getTotal).sum();
        return Math.round(sum * 100) / 100.0;
    }

    /** Ein Tisch ist besetzt, solange etwas in der Küche oder noch nicht bezahlt ist. */
    public static boolean isTableOccupied(int table) {
        return SENT_ORDERS.stream()
                .anyMatch(order -> order.getTable() == table && (order.isOpen() || order.isPaymentDue()));
    }

    /**
     * Kassiert alle offenen Bestellungen eines Tisches ab und gibt den bezahlten Betrag zurück.
     * Fertige Bestellungen gelten damit als abgeschlossen, anwesende Reservierungen werden beendet.
     * Was noch in der Küche ist, bleibt dort und hält den Tisch besetzt.
     */
    public static synchronized double payTable(int table) {
        List<Order> unpaid = getUnpaidOrders(table);
        if (unpaid.isEmpty()) {
            throw new IllegalStateException("Für Tisch " + table + " ist nichts zu bezahlen.");
        }
        double amount = getAmountDue(table);
        LocalDateTime now = LocalDateTime.now();

        for (Order order : unpaid) {
            order.markPaid(now);
            if (order.getStatus() == OrderStatus.FERTIG) {
                order.setStatus(OrderStatus.ABGESCHLOSSEN);
            }
        }
        for (Reservation reservation : ReservationService.getReservationsOn(now.toLocalDate())) {
            if (reservation.getTable() == table && reservation.getStatus() == ReservationStatus.ANWESEND) {
                ReservationService.setStatus(reservation, ReservationStatus.ABGESCHLOSSEN);
            }
        }
        return amount;
    }

    // ---------- Weg der Bestellung: Neu → In Zubereitung → Fertig → serviert ----------

    /** Küche beginnt mit der Zubereitung. */
    public static void startPreparation(Order order) {
        advance(order, OrderStatus.NEU, OrderStatus.IN_ZUBEREITUNG);
    }

    /** Küche ist fertig, die Bestellung kann abgeholt werden. */
    public static void markReady(Order order) {
        advance(order, OrderStatus.IN_ZUBEREITUNG, OrderStatus.FERTIG);
    }

    /** Service hat die Bestellung zum Tisch gebracht. Bezahlt wird getrennt davon. */
    public static void markServed(Order order) {
        advance(order, OrderStatus.FERTIG, OrderStatus.ABGESCHLOSSEN);
    }

    private static synchronized void advance(Order order, OrderStatus expected, OrderStatus next) {
        if (order.getStatus() != expected) {
            throw new IllegalStateException("Bestellung #" + order.getNumber() + " ist „" + order.getStatus()
                    + "“ und kann nicht auf „" + next + "“ gesetzt werden.");
        }
        order.setStatus(next);
    }

    public static void changeStatus(Order order, OrderStatus status) {
        order.setStatus(status);
    }

    /**
     * Storniert eine offene Bestellung. Ist sie noch neu (nicht zubereitet),
     * werden die Zutaten zurückgebucht. Gibt true zurück, wenn zurückgebucht wurde.
     */
    public static boolean cancel(Order order) {
        if (!order.isOpen()) {
            throw new IllegalStateException("Nur offene Bestellungen können storniert werden.");
        }
        if (order.isPaid()) {
            throw new IllegalStateException("Bereits bezahlte Bestellungen können nicht storniert werden.");
        }
        boolean alreadyPrepared = order.getStatus() != OrderStatus.NEU;
        order.setStatus(OrderStatus.STORNIERT);
        return InventoryService.reverseOrder(order, alreadyPrepared);
    }
}
