package at.gastroflow.backend.dao;

import at.gastroflow.backend.database.DatabaseConnection;
import at.gastroflow.backend.model.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProductDAO {

    public List<Product> findAll() throws SQLException {
        List<Product> products = new ArrayList<>();

        String sql = "SELECT id, name, price, description, available FROM products";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet result = preparedStatement.executeQuery()) {
            while (result.next()) {
                UUID id = result.getObject("id", UUID.class);

                Product product = new Product(id, result.getString(
                        "name"),
                        result.getBigDecimal("price"),
                        result.getString("description"),
                        result.getBoolean("available"));
                products.add(product);
            }
        }
        return products;
    }

    public Product findById(UUID id) throws SQLException {
        String sql = "SELECT id, name, price, description, available FROM products WHERE id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setObject(1, id);

            try (ResultSet result = preparedStatement.executeQuery()) {
                if (result.next()) {
                    return new Product(
                            result.getObject("id", UUID.class),
                            result.getString("name"),
                            result.getBigDecimal("price"),
                            result.getString("description"),
                            result.getBoolean("available")
                    );
                }
            }
        }
        return null;
    }

    public void save(Product product) throws SQLException {
        String sql = "INSERT INTO products (id, name, price, description, available) VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setObject(1, product.getId());
            preparedStatement.setString(2, product.getName());
            preparedStatement.setBigDecimal(3, product.getPrice());
            preparedStatement.setString(4, product.getDescription());
            preparedStatement.setBoolean(5, product.isAvailable());
            preparedStatement.executeUpdate();
        }
    }

    public void update(Product product) throws SQLException {
        String sql = "UPDATE products SET name = ?, price = ?, description = ?, available = ? WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setString(1, product.getName());
            preparedStatement.setBigDecimal(2, product.getPrice());
            preparedStatement.setString(3, product.getDescription());
            preparedStatement.setBoolean(4, product.isAvailable());
            preparedStatement.setObject(5, product.getId());
            preparedStatement.executeUpdate();
        }
    }

    public void delete(UUID id) throws SQLException {
        String sql = "DELETE FROM products WHERE id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setObject(1, id);
            preparedStatement.executeUpdate();
        }
    }
}