package at.gastroflow.backend.service;

import at.gastroflow.backend.model.Reservation;
import at.gastroflow.backend.model.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests für Tischverfügbarkeit, Kapazität und Öffnungszeiten. Datum weit in der Zukunft, damit nichts kollidiert. */
class ReservationServiceTest {

    private LocalDateTime at(int dayOffset, int hour, int minute) {
        return LocalDate.now().plusYears(10).plusDays(dayOffset).atTime(hour, minute);
    }

    @Test
    void overlappingReservationOnSameTableIsRejected() {
        ReservationService.add("A", "", 2, 1, at(1, 18, 0), 90, ReservationStatus.BESTAETIGT, "");
        assertThrows(IllegalArgumentException.class, () ->
                ReservationService.add("B", "", 2, 1, at(1, 19, 0), 90, ReservationStatus.BESTAETIGT, ""));
    }

    @Test
    void backToBackReservationIsAllowed() {
        ReservationService.add("A", "", 2, 2, at(2, 18, 0), 90, ReservationStatus.BESTAETIGT, "");
        assertDoesNotThrow(() ->
                ReservationService.add("B", "", 2, 2, at(2, 19, 30), 90, ReservationStatus.BESTAETIGT, ""));
    }

    @Test
    void tooManyPersonsForTableIsRejected() {
        // Tisch 1 hat 4 Plätze
        assertThrows(IllegalArgumentException.class, () ->
                ReservationService.add("Große Gruppe", "", 6, 1, at(3, 12, 0), 90, ReservationStatus.BESTAETIGT, ""));
    }

    @Test
    void reservationOutsideOpeningHoursIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ReservationService.add("Spät", "", 2, 3, at(4, 22, 30), 90, ReservationStatus.BESTAETIGT, ""));
    }

    @Test
    void noShowFreesTheTable() {
        Reservation r = ReservationService.add("A", "", 2, 4, at(5, 12, 0), 90, ReservationStatus.BESTAETIGT, "");
        assertFalse(ReservationService.isTableAvailable(4, at(5, 12, 30), 60, null));

        ReservationService.setStatus(r, ReservationStatus.NO_SHOW);
        assertTrue(ReservationService.isTableAvailable(4, at(5, 12, 30), 60, null));
    }

    @Test
    void phoneAndNoteAreStored() {
        Reservation r = ReservationService.add("Max", " +43 660 1234567 ", 2, 5, at(6, 12, 0), 90,
                ReservationStatus.BESTAETIGT, "Fensterplatz");
        assertEquals("+43 660 1234567", r.getPhone());
        assertEquals("Fensterplatz", r.getNote());
    }

    @Test
    void invalidPhoneIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ReservationService.add("X", "abc", 2, 6, at(7, 12, 0), 90, ReservationStatus.BESTAETIGT, ""));
    }

    @Test
    void freeTablesRespectCapacity() {
        // Für 7 Personen kommen nur die 8er-Tische 11 und 12 in Frage
        assertEquals(java.util.List.of(11, 12),
                ReservationService.getFreeTables(at(8, 12, 0), 90, 7, null));
    }
}
