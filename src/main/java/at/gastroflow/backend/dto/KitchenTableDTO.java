package at.gastroflow.backend.dto;

import at.gastroflow.backend.model.Course;
import at.gastroflow.backend.model.OrderStatus;

import java.util.List;

public record KitchenTableDTO(int table, OrderStatus stage, Course nextCourse, boolean canClose,
                              List<CourseProgressDTO> courses, List<OrderDTO> orders) {

    public record CourseProgressDTO(Course course, boolean done, boolean cooking) {
    }
}
