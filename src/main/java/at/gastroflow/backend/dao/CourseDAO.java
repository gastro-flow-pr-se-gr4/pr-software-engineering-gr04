package at.gastroflow.backend.dao;

import at.gastroflow.backend.database.DatabaseConnection;
import at.gastroflow.backend.model.Course;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class CourseDAO {

    public Map<String, Course> findAll() throws SQLException {
        Map<String, Course> courses = new LinkedHashMap<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT category, course FROM category_courses ORDER BY category");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                courses.put(result.getString("category"), Course.valueOf(result.getString("course")));
            }
        }
        return courses;
    }

    public void save(String category, Course course) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO category_courses (category, course) VALUES (?, ?)"
                             + " ON CONFLICT (category) DO UPDATE SET course = EXCLUDED.course")) {
            statement.setString(1, category);
            statement.setString(2, course.name());
            statement.executeUpdate();
        }
    }

    public void delete(String category) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM category_courses WHERE category = ?")) {
            statement.setString(1, category);
            statement.executeUpdate();
        }
    }
}
