package at.gastroflow.backend.service;

import at.gastroflow.backend.dao.CourseDAO;
import at.gastroflow.backend.model.Course;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

public class CourseService {

    private final CourseDAO courseDAO;

    public CourseService() {
        this(new CourseDAO());
    }

    public CourseService(CourseDAO courseDAO) {
        this.courseDAO = courseDAO;
    }

    public Map<String, Course> getSavedCourses() throws SQLException {
        return courseDAO.findAll();
    }

    public Course getCourse(String category) throws SQLException {
        if (category == null || category.isBlank()) {
            return Course.HAUPTSPEISE;
        }
        Course saved = courseDAO.findAll().get(category.trim());
        return saved != null ? saved : guessCourse(category);
    }

    public void setCourse(String category, Course course) throws SQLException {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Bitte eine Kategorie angeben.");
        }
        if (course == null) {
            throw new IllegalArgumentException("Bitte einen Gang auswählen.");
        }
        courseDAO.save(category.trim(), course);
    }

    public void renameCategory(String oldName, String newName) throws SQLException {
        Course course = getCourse(oldName);
        courseDAO.delete(oldName.trim());
        setCourse(newName, course);
    }

    public void deleteCategory(String category) throws SQLException {
        courseDAO.delete(category.trim());
    }

    public static Course guessCourse(String category) {
        String name = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (containsAny(name, "hauptspeise", "hauptgericht")) {
            return Course.HAUPTSPEISE;
        }
        if (containsAny(name, "getränk", "getraenk", "drink", "kaffee", "tee", "bier", "wein",
                "saft", "cocktail", "limo", "spirituose")) {
            return Course.GETRAENK;
        }
        if (containsAny(name, "vorspeise", "starter", "suppe", "salat", "antipast")) {
            return Course.VORSPEISE;
        }
        if (containsAny(name, "dessert", "nachspeise", "nachtisch", "süß", "eisbecher", "kuchen", "torte")) {
            return Course.NACHSPEISE;
        }
        return Course.HAUPTSPEISE;
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
