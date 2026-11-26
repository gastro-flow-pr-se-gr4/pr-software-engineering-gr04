package at.gastroflow.backend.dao;

import at.gastroflow.backend.database.DatabaseConnection;
import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderItem;
import at.gastroflow.backend.model.OrderStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Speichert Bestellungen mit ihren Positionen in PostgreSQL (Tabellen orders und order_items). */
public class OrderDAO {

    private static final String SELECT_ORDERS =
            "SELECT id, number, table_number, waiter, created_at, guests, status, paid_at FROM orders";

    /** Legt die Tabellen an, falls sie noch fehlen (Skript db/orders.sql). */
    public void createTables() throws SQLException {
        String script;
        try (InputStream in = OrderDAO.class.getResourceAsStream("/db/orders.sql")) {
            if (in == null) {
                throw new SQLException("db/orders.sql nicht gefunden.");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("db/orders.sql konnte nicht gelesen werden.", e);
        }
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : script.replaceAll("(?m)^--.*$", "").split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    public List<Order> findAll() throws SQLException {
        return query(SELECT_ORDERS + " ORDER BY created_at, number");
    }

    public List<Order> findByTable(int table) throws SQLException {
        return query(SELECT_ORDERS + " WHERE table_number = ? ORDER BY created_at, number", table);
    }

    /** Alles, was noch läuft: nicht serviert oder nicht bezahlt (Stornierte nicht). */
    public List<Order> findActive() throws SQLException {
        return query(SELECT_ORDERS + " WHERE status <> 'STORNIERT' AND (status <> 'ABGESCHLOSSEN' OR paid_at IS NULL)"
                + " ORDER BY created_at, number");
    }

    /** Bestellungen ab einem Zeitpunkt (z. B. alle von heute). */
    public List<Order> findSince(LocalDateTime from) throws SQLException {
        return query(SELECT_ORDERS + " WHERE created_at >= ? ORDER BY created_at, number", Timestamp.valueOf(from));
    }

    public Order findById(UUID id) throws SQLException {
        List<Order> orders = query(SELECT_ORDERS + " WHERE id = ?", id);
        return orders.isEmpty() ? null : orders.get(0);
    }

    /** Speichert Bestellung und Positionen in einem Schritt (alles oder nichts) und setzt die Nummer. */
    public void save(Order order) throws SQLException {
        String orderSql = "INSERT INTO orders (id, table_number, waiter, created_at, guests, status, paid_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING number";
        String itemSql = "INSERT INTO order_items (id, order_id, position, product_name, category, unit_price, quantity, note)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insertOrder = connection.prepareStatement(orderSql);
                 PreparedStatement insertItem = connection.prepareStatement(itemSql)) {

                insertOrder.setObject(1, order.getId());
                insertOrder.setInt(2, order.getTableNumber());
                insertOrder.setString(3, order.getWaiter());
                insertOrder.setTimestamp(4, Timestamp.valueOf(order.getCreatedAt()));
                insertOrder.setInt(5, order.getGuests());
                insertOrder.setString(6, order.getStatus().name());
                insertOrder.setTimestamp(7, order.getPaidAt() == null ? null : Timestamp.valueOf(order.getPaidAt()));
                try (ResultSet result = insertOrder.executeQuery()) {
                    result.next();
                    order.setNumber(result.getInt("number"));
                }

                int position = 0;
                for (OrderItem item : order.getItems()) {
                    insertItem.setObject(1, item.getId());
                    insertItem.setObject(2, order.getId());
                    insertItem.setInt(3, position++);
                    insertItem.setString(4, item.getProductName());
                    insertItem.setString(5, item.getCategory());
                    insertItem.setBigDecimal(6, item.getUnitPrice());
                    insertItem.setInt(7, item.getQuantity());
                    insertItem.setString(8, item.getNote());
                    insertItem.addBatch();
                }
                insertItem.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                order.setNumber(0);
                throw e;
            }
        }
    }

    public void updateStatus(UUID id, OrderStatus status) throws SQLException {
        update("UPDATE orders SET status = ? WHERE id = ?", status.name(), id);
    }

    public void markPaid(List<UUID> ids, LocalDateTime paidAt) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE orders SET paid_at = ? WHERE id = ANY (?) AND paid_at IS NULL")) {
            statement.setTimestamp(1, Timestamp.valueOf(paidAt));
            statement.setArray(2, connection.createArrayOf("uuid", ids.toArray()));
            statement.executeUpdate();
        }
    }

    private void update(String sql, Object... params) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        }
    }

    // Lädt Bestellungen und danach mit einer zweiten Abfrage alle ihre Positionen
    private List<Order> query(String sql, Object... params) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            Map<UUID, Object[]> rows = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        Timestamp paid = result.getTimestamp("paid_at");
                        rows.put(result.getObject("id", UUID.class), new Object[]{
                                result.getInt("number"),
                                result.getInt("table_number"),
                                result.getString("waiter"),
                                result.getTimestamp("created_at").toLocalDateTime(),
                                result.getInt("guests"),
                                OrderStatus.valueOf(result.getString("status")),
                                paid == null ? null : paid.toLocalDateTime()});
                    }
                }
            }

            Map<UUID, List<OrderItem>> items = new HashMap<>();
            if (!rows.isEmpty()) {
                Array ids = connection.createArrayOf("uuid", rows.keySet().toArray());
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, order_id, product_name, category, unit_price, quantity, note"
                                + " FROM order_items WHERE order_id = ANY (?) ORDER BY order_id, position")) {
                    statement.setArray(1, ids);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            items.computeIfAbsent(result.getObject("order_id", UUID.class), k -> new ArrayList<>())
                                    .add(new OrderItem(
                                            result.getObject("id", UUID.class),
                                            result.getString("product_name"),
                                            result.getString("category"),
                                            result.getBigDecimal("unit_price"),
                                            result.getInt("quantity"),
                                            result.getString("note")));
                        }
                    }
                }
            }

            List<Order> orders = new ArrayList<>();
            rows.forEach((id, row) -> orders.add(new Order(id, (int) row[0], (int) row[1], (String) row[2],
                    (LocalDateTime) row[3], (int) row[4], items.getOrDefault(id, List.of()),
                    (OrderStatus) row[5], (LocalDateTime) row[6])));
            return orders;
        }
    }
}
