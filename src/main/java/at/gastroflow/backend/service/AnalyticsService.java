package at.gastroflow.backend.service;

import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderItem;
import at.gastroflow.backend.model.OrderStatus;
import at.gastroflow.backend.model.Reservation;
import at.gastroflow.backend.model.ReservationStatus;
import at.gastroflow.backend.model.RestaurantSettings;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Berechnet Kennzahlen für die Leitung aus den gespeicherten Bestellungen. */
public class AnalyticsService {

    /** Wie oft ein Tisch pro Tag neu besetzt wird (für die Tageskapazität). */
    private static final int SEATINGS_PER_DAY = 3;

    // Stornierte Bestellungen zählen nicht zum Umsatz
    private static List<Order> ordersBetween(LocalDate from, LocalDate to) {
        return OrderService.getOrdersBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
                .filter(order -> order.getStatus() != OrderStatus.STORNIERT)
                .toList();
    }

    public static double revenueBetween(LocalDate from, LocalDate to) {
        return ordersBetween(from, to).stream().mapToDouble(Order::getTotal).sum();
    }

    public static int orderCountBetween(LocalDate from, LocalDate to) {
        return ordersBetween(from, to).size();
    }

    public static double averageOrderValue(LocalDate from, LocalDate to) {
        List<Order> orders = ordersBetween(from, to);
        return orders.isEmpty() ? 0 : orders.stream().mapToDouble(Order::getTotal).sum() / orders.size();
    }

    public static int guestsBetween(LocalDate from, LocalDate to) {
        return ordersBetween(from, to).stream().mapToInt(Order::getGuests).sum();
    }

    /** Auslastung eines Tages: Gäste / (alle Plätze × Durchgänge), höchstens 100 %. */
    public static double occupancyOn(LocalDate date) {
        double capacity = RestaurantSettings.get().getTotalSeats() * SEATINGS_PER_DAY;
        return Math.min(1.0, guestsBetween(date, date) / capacity);
    }

    public static int openOrderCount() {
        return OrderService.getOpenOrders().size();
    }

    /** Tische mit offener Bestellung oder anwesenden Gästen. */
    public static Set<Integer> occupiedTables(LocalDateTime now) {
        Set<Integer> tables = new TreeSet<>();
        for (Order order : OrderService.getOpenOrders()) {
            tables.add(order.getTable());
        }
        for (Reservation reservation : ReservationService.getReservationsOn(now.toLocalDate())) {
            if (reservation.getStatus() == ReservationStatus.ANWESEND) {
                tables.add(reservation.getTable());
            }
        }
        tables.removeIf(table -> table > RestaurantSettings.get().getTableCount());
        return tables;
    }

    /** Meistverkaufte Produkte (Name → Stück), absteigend sortiert. */
    public static List<Map.Entry<String, Integer>> topProducts(LocalDate from, LocalDate to, int limit) {
        Map<String, Integer> counts = new HashMap<>();
        for (Order order : ordersBetween(from, to)) {
            for (OrderItem item : order.getItems()) {
                counts.merge(item.getProductName(), item.getQuantity(), Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        return list.subList(0, Math.min(limit, list.size()));
    }

    /** Umsatz je Kategorie, absteigend sortiert. */
    public static List<Map.Entry<String, Double>> revenueByCategory(LocalDate from, LocalDate to) {
        Map<String, Double> sums = new HashMap<>();
        for (Order order : ordersBetween(from, to)) {
            for (OrderItem item : order.getItems()) {
                sums.merge(item.getCategory(), item.getTotal(), Double::sum);
            }
        }
        List<Map.Entry<String, Double>> list = new ArrayList<>(sums.entrySet());
        list.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        return list;
    }

    /** Durchschnittliche Bestellungen pro Stunde (Stunde → Bestellungen pro Tag). */
    public static Map<Integer, Double> averageOrdersPerHour(LocalDate from, LocalDate to) {
        RestaurantSettings settings = RestaurantSettings.get();
        Map<Integer, Double> result = new LinkedHashMap<>();
        for (int hour = settings.getOpeningTime().getHour(); hour < settings.getClosingTime().getHour(); hour++) {
            result.put(hour, 0.0);
        }
        long days = Math.max(1, to.toEpochDay() - from.toEpochDay() + 1);
        for (Order order : ordersBetween(from, to)) {
            int hour = order.getCreatedAt().getHour();
            if (result.containsKey(hour)) {
                result.put(hour, result.get(hour) + 1.0 / days);
            }
        }
        return result;
    }

    /** Durchschnittlicher Tagesumsatz je Wochentag. */
    public static Map<DayOfWeek, Double> averageRevenueByWeekday(LocalDate from, LocalDate to) {
        Map<DayOfWeek, Double> sums = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, Integer> days = new EnumMap<>(DayOfWeek.class);
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            sums.merge(day.getDayOfWeek(), revenueBetween(day, day), Double::sum);
            days.merge(day.getDayOfWeek(), 1, Integer::sum);
        }
        Map<DayOfWeek, Double> result = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            int count = days.getOrDefault(dayOfWeek, 0);
            result.put(dayOfWeek, count == 0 ? 0 : sums.get(dayOfWeek) / count);
        }
        return result;
    }
}
