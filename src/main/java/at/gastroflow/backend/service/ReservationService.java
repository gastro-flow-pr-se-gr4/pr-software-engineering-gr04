package at.gastroflow.backend.service;

import at.gastroflow.backend.model.Reservation;
import at.gastroflow.backend.model.ReservationStatus;
import at.gastroflow.backend.model.RestaurantSettings;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Reservierungen inklusive Prüfung, ob ein Tisch frei ist und genug Plätze hat (nur im Speicher). */
public class ReservationService {

    private static final List<Reservation> RESERVATIONS = new ArrayList<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static int nextId = 1;

    public static List<Reservation> getReservations() {
        return Collections.unmodifiableList(RESERVATIONS);
    }

    /** Reservierungen von from bis to (beide Tage inklusive), nach Uhrzeit sortiert. */
    public static List<Reservation> getReservationsBetween(LocalDate from, LocalDate to) {
        return RESERVATIONS.stream()
                .filter(r -> !r.getStart().toLocalDate().isBefore(from)
                        && !r.getStart().toLocalDate().isAfter(to))
                .sorted(Comparator.comparing(Reservation::getStart))
                .toList();
    }

    public static List<Reservation> getReservationsOn(LocalDate date) {
        return getReservationsBetween(date, date);
    }

    public static Reservation add(String name, String phone, int persons, int table, LocalDateTime start,
                                  int durationMinutes, ReservationStatus status, String note) {
        check(name, phone, persons, table, start, durationMinutes, status, null);
        Reservation reservation = new Reservation(nextId++, name.trim(), clean(phone), persons, table,
                start, durationMinutes, status, clean(note));
        RESERVATIONS.add(reservation);
        return reservation;
    }

    public static void update(Reservation reservation, String name, String phone, int persons, int table,
                              LocalDateTime start, int durationMinutes, ReservationStatus status, String note) {
        check(name, phone, persons, table, start, durationMinutes, status, reservation);
        reservation.setName(name.trim());
        reservation.setPhone(clean(phone));
        reservation.setPersons(persons);
        reservation.setTable(table);
        reservation.setStart(start);
        reservation.setDurationMinutes(durationMinutes);
        reservation.setStatus(status);
        reservation.setNote(clean(note));
    }

    public static void delete(Reservation reservation) {
        RESERVATIONS.remove(reservation);
    }

    public static void setStatus(Reservation reservation, ReservationStatus status) {
        if (status.blocksTable()) {
            checkConflict(reservation.getTable(), reservation.getStart(),
                    reservation.getDurationMinutes(), reservation);
        }
        reservation.setStatus(status);
    }

    public static void assignTable(Reservation reservation, int table) {
        checkTable(table);
        checkCapacity(table, reservation.getPersons());
        checkConflict(table, reservation.getStart(), reservation.getDurationMinutes(), reservation);
        reservation.setTable(table);
    }

    /** Erste andere Reservierung, die den Tisch zur gewünschten Zeit belegt. */
    public static Optional<Reservation> findConflict(int table, LocalDateTime start,
                                                     int durationMinutes, Reservation ignore) {
        LocalDateTime end = start.plusMinutes(durationMinutes);
        return RESERVATIONS.stream()
                .filter(r -> r != ignore)
                .filter(r -> r.getTable() == table)
                .filter(r -> r.getStatus().blocksTable())
                .filter(r -> start.isBefore(r.getEnd()) && end.isAfter(r.getStart()))
                .findFirst();
    }

    public static boolean isTableAvailable(int table, LocalDateTime start,
                                           int durationMinutes, Reservation ignore) {
        return findConflict(table, start, durationMinutes, ignore).isEmpty();
    }

    /** Tische, die zur Zeit frei sind und genug Plätze für die Personen haben. */
    public static List<Integer> getFreeTables(LocalDateTime start, int durationMinutes, int persons,
                                              Reservation ignore) {
        RestaurantSettings settings = RestaurantSettings.get();
        List<Integer> free = new ArrayList<>();
        for (int table = 1; table <= settings.getTableCount(); table++) {
            if (settings.getCapacity(table) >= persons && isTableAvailable(table, start, durationMinutes, ignore)) {
                free.add(table);
            }
        }
        return free;
    }

    private static void check(String name, String phone, int persons, int table, LocalDateTime start,
                              int durationMinutes, ReservationStatus status, Reservation ignore) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte den Namen des Gastes eingeben.");
        }
        if (phone != null && !phone.isBlank() && !phone.trim().matches("\\+?[0-9 /()\\-]{6,20}")) {
            throw new IllegalArgumentException("Bitte eine gültige Telefonnummer eingeben, z. B. +43 660 1234567.");
        }
        if (persons < 1) {
            throw new IllegalArgumentException("Es muss mindestens eine Person sein.");
        }
        if (start == null) {
            throw new IllegalArgumentException("Bitte Datum und Uhrzeit auswählen.");
        }
        checkTable(table);
        checkCapacity(table, persons);

        RestaurantSettings settings = RestaurantSettings.get();
        LocalTime open = settings.getOpeningTime();
        LocalTime close = settings.getClosingTime();
        LocalDateTime end = start.plusMinutes(durationMinutes);
        boolean inOpeningHours = !start.toLocalTime().isBefore(open)
                && end.toLocalDate().equals(start.toLocalDate())
                && !end.toLocalTime().isAfter(close);
        if (!inOpeningHours) {
            throw new IllegalArgumentException("Die Reservierung muss zwischen "
                    + open.format(TIME) + " und " + close.format(TIME) + " Uhr liegen.");
        }

        if (status.blocksTable()) {
            checkConflict(table, start, durationMinutes, ignore);
        }
    }

    private static void checkTable(int table) {
        int count = RestaurantSettings.get().getTableCount();
        if (table < 1 || table > count) {
            throw new IllegalArgumentException("Tisch " + table + " gibt es nicht (1 bis " + count + ").");
        }
    }

    private static void checkCapacity(int table, int persons) {
        int seats = RestaurantSettings.get().getCapacity(table);
        if (persons > seats) {
            throw new IllegalArgumentException("Tisch " + table + " hat nur " + seats
                    + " Plätze – bitte einen größeren Tisch wählen.");
        }
    }

    private static void checkConflict(int table, LocalDateTime start, int durationMinutes, Reservation ignore) {
        Optional<Reservation> conflict = findConflict(table, start, durationMinutes, ignore);
        if (conflict.isPresent()) {
            Reservation other = conflict.get();
            throw new IllegalArgumentException("Tisch " + table + " ist von "
                    + other.getStart().format(TIME) + " bis " + other.getEnd().format(TIME)
                    + " Uhr durch " + other.getName() + " belegt.");
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }
}
