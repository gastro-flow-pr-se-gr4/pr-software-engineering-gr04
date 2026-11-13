package at.gastroflow.backend.model;

import java.time.LocalTime;

/** Eine Mitarbeiterin / ein Mitarbeiter mit Schicht für heute. */
public class Employee {

    private final int id;
    private String name;
    private String role;
    private String shift;
    private LocalTime shiftStart;
    private LocalTime shiftEnd;
    private EmployeeStatus status;

    public Employee(int id, String name, String role, String shift,
                    LocalTime shiftStart, LocalTime shiftEnd, EmployeeStatus status) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.shift = shift;
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
        this.status = status;
    }

    public int getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }

    public LocalTime getShiftStart() { return shiftStart; }
    public void setShiftStart(LocalTime shiftStart) { this.shiftStart = shiftStart; }

    public LocalTime getShiftEnd() { return shiftEnd; }
    public void setShiftEnd(LocalTime shiftEnd) { this.shiftEnd = shiftEnd; }

    public EmployeeStatus getStatus() { return status; }
    public void setStatus(EmployeeStatus status) { this.status = status; }
}
