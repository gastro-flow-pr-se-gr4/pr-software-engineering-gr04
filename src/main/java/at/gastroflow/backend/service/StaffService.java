package at.gastroflow.backend.service;

import at.gastroflow.backend.model.Employee;
import at.gastroflow.backend.model.EmployeeStatus;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Personal und heutige Schichten (nur im Speicher). */
public class StaffService {

    private static final List<Employee> EMPLOYEES = new ArrayList<>();
    private static int nextId = 1;

    // Beispieldaten
    static {
        seed("Katharina Lang", "Service", "Frühschicht", 8, 16, EmployeeStatus.IM_DIENST);
        seed("Mia Steiner", "Service", "Mittelschicht", 11, 19, EmployeeStatus.PAUSE);
        seed("Daniel Novak", "Service", "Spätschicht", 15, 23, EmployeeStatus.NICHT_ERSCHIENEN);
        seed("Lukas Bauer", "Küche", "Frühschicht", 8, 16, EmployeeStatus.IM_DIENST);
        seed("Nina Fischer", "Küche", "Mittelschicht", 11, 19, EmployeeStatus.IM_DIENST);
        seed("Stefan Moser", "Küche", "Spätschicht", 15, 23, EmployeeStatus.NICHT_ERSCHIENEN);
        seed("Jonas Pichler", "Bar", "Spätschicht", 17, 23, EmployeeStatus.FEIERABEND);
        seed("Laura Wimmer", "Leitung", "Mittelschicht", 10, 18, EmployeeStatus.IM_DIENST);
    }

    private static void seed(String name, String role, String shift, int from, int to, EmployeeStatus status) {
        EMPLOYEES.add(new Employee(nextId++, name, role, shift,
                LocalTime.of(from, 0), LocalTime.of(to, 0), status));
    }

    public static List<Employee> getEmployees() {
        return Collections.unmodifiableList(EMPLOYEES);
    }

    /** Namen aller Service-Mitarbeiter (Kellner). */
    public static List<String> getWaiterNames() {
        return EMPLOYEES.stream()
                .filter(e -> e.getRole().equals("Service"))
                .map(Employee::getName)
                .toList();
    }

    public static Employee add(String name, String role, String shift,
                               LocalTime start, LocalTime end, EmployeeStatus status) {
        check(name, role, start, end);
        Employee employee = new Employee(nextId++, name.trim(), role, shift, start, end, status);
        EMPLOYEES.add(employee);
        return employee;
    }

    public static void update(Employee employee, String name, String role, String shift,
                              LocalTime start, LocalTime end, EmployeeStatus status) {
        check(name, role, start, end);
        employee.setName(name.trim());
        employee.setRole(role);
        employee.setShift(shift);
        employee.setShiftStart(start);
        employee.setShiftEnd(end);
        employee.setStatus(status);
    }

    public static void setStatus(Employee employee, EmployeeStatus status) {
        employee.setStatus(status);
    }

    public static void delete(Employee employee) {
        EMPLOYEES.remove(employee);
    }

    private static void check(String name, String role, LocalTime start, LocalTime end) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Namen eingeben.");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Bitte eine Rolle auswählen.");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Der Dienstbeginn muss vor dem Dienstende liegen.");
        }
    }
}
