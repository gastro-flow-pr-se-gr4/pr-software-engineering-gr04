package at.gastroflow.backend.service;

import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderItem;
import at.gastroflow.backend.model.OrderStatus;
import at.gastroflow.backend.model.Product;
import at.gastroflow.backend.model.ReservationStatus;
import at.gastroflow.backend.model.RestaurantSettings;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * DEMO-DATEN: Solange es keine Datenbank gibt, werden hier Bestellungen
 * der letzten 90 Tage und Reservierungen für die nächsten 7 Tage erzeugt.
 * Feste Zufallszahlen (Seed), damit bei jedem Start dieselben Werte entstehen.
 */
public class DemoData {

    private static boolean loaded = false;

    // Bestellungen pro Wochentag (Mo bis So)
    private static final int[] ORDERS_PER_WEEKDAY = {24, 22, 26, 30, 40, 46, 36};

    // Gewichtung der Uhrzeiten 8 bis 22 Uhr (Mittag und Abend sind Stoßzeiten)
    private static final int[] HOUR_WEIGHTS = {1, 1, 2, 3, 7, 8, 4, 2, 2, 3, 6, 8, 7, 4, 2};

    private static final String[] GUEST_NAMES = {
            "Familie Huber", "Sarah Klein", "Peter Gruber", "Clara Schmid", "Martin Leitner",
            "Eva Brunner", "Florian Hofmann", "Johanna Mayr", "Tobias Wolf", "Lena Aigner",
            "Michael Reiter", "Sabine Koller", "David Egger", "Teresa Winkler", "Paul Haas"
    };

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        createOrders(new Random(42));
        createReservations(new Random(7));
    }

    public static boolean isLoaded() {
        return loaded;
    }

    // ---------- Bestellungen ----------

    private static void createOrders(Random random) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // Abgeschlossene Bestellungen der letzten 90 Tage und von heute (bis vor 45 Minuten)
        for (int daysBack = 90; daysBack >= 0; daysBack--) {
            LocalDate day = today.minusDays(daysBack);
            int count = ORDERS_PER_WEEKDAY[day.getDayOfWeek().getValue() - 1] + random.nextInt(7) - 3;

            List<LocalDateTime> times = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                LocalDateTime time = day.atTime(randomTime(random));
                if (time.isBefore(now.minusMinutes(45))) {
                    times.add(time);
                }
            }
            times.sort(null);

            for (LocalDateTime time : times) {
                OrderService.addExisting(randomOrder(random, time, 1 + random.nextInt(12), OrderStatus.ABGESCHLOSSEN));
            }
        }

        // Offene Bestellungen gerade eben (eine davon ist verzögert).
        // Demo-Bestellungen werden NICHT vom Lager abgebucht, sonst würde der
        // gespeicherte Bestand bei jedem Programmstart weiter sinken.
        OrderService.addExisting(randomOrder(random, now.minusMinutes(34), 3, OrderStatus.IN_ZUBEREITUNG));
        OrderService.addExisting(randomOrder(random, now.minusMinutes(16), 8, OrderStatus.IN_ZUBEREITUNG));
        OrderService.addExisting(randomOrder(random, now.minusMinutes(11), 6, OrderStatus.FERTIG));
        OrderService.addExisting(randomOrder(random, now.minusMinutes(6), 11, OrderStatus.NEU));
        OrderService.addExisting(randomOrder(random, now.minusMinutes(2), 2, OrderStatus.NEU));
    }

    private static LocalTime randomTime(Random random) {
        int total = 0;
        for (int weight : HOUR_WEIGHTS) {
            total += weight;
        }
        int pick = random.nextInt(total);
        int hour = 8;
        for (int weight : HOUR_WEIGHTS) {
            if (pick < weight) {
                break;
            }
            pick -= weight;
            hour++;
        }
        return LocalTime.of(hour, random.nextInt(60));
    }

    private static Order randomOrder(Random random, LocalDateTime time, int table, OrderStatus status) {
        int[] guestOptions = {1, 2, 2, 2, 3, 4, 4, 5};
        int guests = guestOptions[random.nextInt(guestOptions.length)];

        List<Product> food = productsIn("Hauptgerichte", "Pizza & Pasta", "Burger");
        List<Product> starters = productsIn("Vorspeisen");
        List<Product> drinks = productsIn("Getränke", "Kaffee & Tee");
        List<Product> desserts = productsIn("Desserts");

        Map<Product, Integer> quantities = new LinkedHashMap<>();
        if (random.nextDouble() < 0.25) {
            quantities.merge(pick(random, starters), 1, Integer::sum);
        }
        for (int guest = 0; guest < guests; guest++) {
            quantities.merge(pick(random, food), 1, Integer::sum);
            quantities.merge(pick(random, drinks), 1, Integer::sum);
            if (random.nextDouble() < 0.3) {
                quantities.merge(pick(random, desserts), 1, Integer::sum);
            }
        }

        List<OrderItem> items = new ArrayList<>();
        quantities.forEach((product, quantity) -> items.add(new OrderItem(product, quantity)));

        List<String> waiters = StaffService.getWaiterNames();
        String waiter = waiters.isEmpty() ? "–" : waiters.get(random.nextInt(waiters.size()));
        int safeTable = Math.min(table, RestaurantSettings.get().getTableCount());

        return new Order(safeTable, waiter, time, guests, items, status);
    }

    private static List<Product> productsIn(String... categories) {
        List<Product> result = new ArrayList<>();
        for (String category : categories) {
            result.addAll(MenuService.getProductsInCategory(category));
        }
        return result;
    }

    // Bevorzugt Produkte weiter vorne in der Liste, damit es klare Favoriten gibt
    private static Product pick(Random random, List<Product> products) {
        int size = products.size();
        return products.get(Math.min(random.nextInt(size), random.nextInt(size)));
    }

    // ---------- Reservierungen ----------

    private static void createReservations(Random random) {
        LocalDate today = LocalDate.now();

        // Beispiele für heute (Lisa Wagner bewusst ohne Telefonnummer)
        add("Markus Weber", "+43 650 2223344", 2, 1, today.atTime(11, 0), 60, ReservationStatus.ABGESCHLOSSEN, "");
        add("Julia Hofer", "+43 699 1112233", 2, 6, today.atTime(11, 30), 90, ReservationStatus.NO_SHOW, "");
        add("Max Mustermann", "+43 660 1234567", 2, 4, today.atTime(12, 0), 90, ReservationStatus.ANWESEND, "");
        add("Anna Müller", "+43 664 9876543", 4, 7, today.atTime(12, 30), 90, ReservationStatus.BESTAETIGT, "Geburtstag");
        add("Thomas Berger", "+43 676 5554433", 3, 2, today.atTime(13, 0), 90, ReservationStatus.BESTAETIGT, "");
        add("Lisa Wagner", "", 5, 9, today.atTime(13, 30), 120, ReservationStatus.BESTAETIGT, "");
        add("Familie Huber", "+43 680 7778899", 6, 10, today.atTime(18, 0), 120, ReservationStatus.BESTAETIGT, "Kinderstuhl benötigt");
        add("Sarah Klein", "+43 660 3332211", 2, 3, today.atTime(19, 0), 90, ReservationStatus.BESTAETIGT, "");
        add("Peter Gruber", "+43 664 4445566", 4, 5, today.atTime(19, 30), 90, ReservationStatus.BESTAETIGT, "");
        add("Clara Schmid", "+43 676 9990011", 3, 8, today.atTime(20, 0), 90, ReservationStatus.BESTAETIGT, "");

        // Nächste sieben Tage
        String[] times = {"12:00", "12:30", "13:00", "18:00", "18:30", "19:00", "19:30", "20:00"};
        RestaurantSettings settings = RestaurantSettings.get();

        for (int day = 1; day <= 7; day++) {
            int count = 3 + random.nextInt(4);
            for (int i = 0; i < count; i++) {
                LocalDateTime start = today.plusDays(day)
                        .atTime(LocalTime.parse(times[random.nextInt(times.length)]));
                String name = GUEST_NAMES[random.nextInt(GUEST_NAMES.length)];
                int persons = 2 + random.nextInt(5);
                String phone = String.format("+43 6%02d %07d", 50 + random.nextInt(50), random.nextInt(10_000_000));

                for (int attempt = 0; attempt < 30; attempt++) {
                    int table = 1 + random.nextInt(settings.getTableCount());
                    if (settings.getCapacity(table) >= persons
                            && ReservationService.isTableAvailable(table, start, 90, null)) {
                        add(name, phone, persons, table, start, 90, ReservationStatus.BESTAETIGT, "");
                        break;
                    }
                }
            }
        }
    }

    private static void add(String name, String phone, int persons, int table, LocalDateTime start,
                            int minutes, ReservationStatus status, String note) {
        ReservationService.add(name, phone, persons, table, start, minutes, status, note);
    }
}
