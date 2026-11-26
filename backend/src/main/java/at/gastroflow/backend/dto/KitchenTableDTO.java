package at.gastroflow.backend.dto;

import at.gastroflow.backend.model.Course;
import at.gastroflow.backend.model.OrderStatus;

import java.util.List;

/**
 * Eine Tischkarte in der Küche.
 * stage = Spalte (NEU, IN_ZUBEREITUNG, FERTIG), nextCourse = nächster noch nicht begonnener Gang (oder null),
 * canClose = alle Gänge fertig, die Küche kann abschließen. courses = alle bestellten Gänge mit Fortschritt.
 */
public record KitchenTableDTO(int table, OrderStatus stage, Course nextCourse, boolean canClose,
                              List<CourseProgressDTO> courses, List<OrderDTO> orders) {

    /** Ein bestellter Gang: fertig (grüner Haken), wird gerade gekocht, oder wartet noch. */
    public record CourseProgressDTO(Course course, boolean done, boolean cooking) {
    }
}
