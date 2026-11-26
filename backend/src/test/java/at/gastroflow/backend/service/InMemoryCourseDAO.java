package at.gastroflow.backend.service;

import at.gastroflow.backend.dao.CourseDAO;
import at.gastroflow.backend.model.Course;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ersatz für die Tabelle category_courses in Tests. */
public class InMemoryCourseDAO extends CourseDAO {

    private final Map<String, Course> courses = new LinkedHashMap<>();

    @Override
    public Map<String, Course> findAll() {
        return new LinkedHashMap<>(courses);
    }

    @Override
    public void save(String category, Course course) {
        courses.put(category, course);
    }

    @Override
    public void delete(String category) {
        courses.remove(category);
    }
}
