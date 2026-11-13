package at.gastroflow.backend.model;

import java.time.LocalDateTime;

/** Eine Tischreservierung. */
public class Reservation {

    private final int id;
    private String name;
    private String phone;
    private int persons;
    private int table;
    private LocalDateTime start;
    private int durationMinutes;
    private ReservationStatus status;
    private String note;

    public Reservation(int id, String name, String phone, int persons, int table,
                       LocalDateTime start, int durationMinutes, ReservationStatus status, String note) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.persons = persons;
        this.table = table;
        this.start = start;
        this.durationMinutes = durationMinutes;
        this.status = status;
        this.note = note;
    }

    public int getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /** Telefonnummer, leer wenn keine hinterlegt ist. */
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public boolean hasPhone() {
        return phone != null && !phone.isBlank();
    }

    public int getPersons() { return persons; }
    public void setPersons(int persons) { this.persons = persons; }

    public int getTable() { return table; }
    public void setTable(int table) { this.table = table; }

    public LocalDateTime getStart() { return start; }
    public void setStart(LocalDateTime start) { this.start = start; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public LocalDateTime getEnd() {
        return start.plusMinutes(durationMinutes);
    }

    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
